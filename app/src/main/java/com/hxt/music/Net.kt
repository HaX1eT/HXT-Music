package com.hxt.music

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException

const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

/** HTTP-слой для NewPipeExtractor поверх OkHttp. */
class NpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody(null)
            method == "POST" || method == "PUT" -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        val b = okhttp3.Request.Builder().url(request.url()).method(method, body)
        b.header("User-Agent", UA)
        request.headers().forEach { (name, values) ->
            b.removeHeader(name)
            values.forEach { v -> b.addHeader(name, v) }
        }
        client.newCall(b.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
        }
    }
}

object Music {
    private val ID = Regex("[?&]v=([\\w-]{11})")

    fun bigThumb(url: String): String = Regex("=w\\d+-h\\d+[^/]*$").replace(url, "=w600-h600-l90-rj")

    /** Поиск песен в YouTube Music (аналог yt.search(filter="songs", limit=12)). */
    fun search(q: String, limit: Int = 12): List<Track> {
        val yt = ServiceList.YouTube
        val handler = yt.searchQHFactory.fromQuery(q, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "")
        val ex = yt.getSearchExtractor(handler)
        ex.fetchPage()
        return ex.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toTrack() }
            .take(limit)
    }

    private fun StreamInfoItem.toTrack(): Track? {
        val id = ID.find(url)?.groupValues?.get(1) ?: return null
        val thumb = thumbnails.lastOrNull()?.url ?: ""
        val d = duration
        val dur = if (d > 0) "${d / 60}:${(d % 60).toString().padStart(2, '0')}" else ""
        val artist = uploaderName?.takeIf { it.isNotBlank() } ?: "Unknown"
        return Track(id, name, artist, dur, bigThumb(thumb))
    }

    /** Прямая ссылка на лучший аудиопоток (m4a в приоритете) + расширение файла. */
    fun audioStream(vid: String): Pair<String, String> {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$vid")
        val http = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val best = http.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: http.maxByOrNull { it.averageBitrate }
            ?: throw IOException("Нет доступного аудиопотока")
        val ext = best.format?.suffix ?: "m4a"
        return best.content to ext
    }
}
