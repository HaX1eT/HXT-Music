package com.hxt.music

import okhttp3.MediaType.Companion.toMediaType
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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
import java.util.concurrent.TimeUnit

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

    @Volatile var ytdlpReady = false

    /**
     * Прямая ссылка на лучший аудиопоток (m4a в приоритете) + расширение файла.
     * Порядок: yt-dlp (как в веб-версии) → NewPipeExtractor → ANDROID_VR.
     */
    fun audioStream(vid: String): Pair<String, String> {
        val errors = mutableListOf<String>()
        if (ytdlpReady) {
            try {
                return audioViaYtDlp(vid)
            } catch (e: Exception) {
                errors += "YT: ${e.message ?: "ошибка"}"
            }
        } else {
            errors += "YT: не запущен"
        }
        try {
            return audioViaNewPipe(vid)
        } catch (e: Exception) {
            errors += "NP: ${e.message ?: "ошибка"}"
        }
        try {
            return audioViaAndroidVr(vid)
        } catch (e: Exception) {
            errors += "VR: ${e.message ?: "ошибка"}"
        }
        throw IOException(errors.joinToString(" | "))
    }

    /** Общая блокировка: обновление yt-dlp и получение ссылки не должны идти одновременно. */
    val ytLock = Any()

    private val ytPool = java.util.concurrent.Executors.newCachedThreadPool { r ->
        Thread(r).apply { isDaemon = true }
    }

    /** yt-dlp с таймаутом 45 с: раньше зависание давало «бесконечную загрузку», теперь — переход на запасные способы. */
    private fun audioViaYtDlp(vid: String): Pair<String, String> {
        val task = ytPool.submit(java.util.concurrent.Callable {
            synchronized(ytLock) {
                val req = YoutubeDLRequest("https://www.youtube.com/watch?v=$vid")
                req.addOption("-f", "bestaudio[ext=m4a]/bestaudio")
                req.addOption("--no-playlist")
                req.addOption("--socket-timeout", "15")
                req.addOption("--retries", "1")
                val info = YoutubeDL.getInstance().getInfo(req)
                val url = info.url?.takeIf { it.isNotBlank() } ?: throw IOException("нет ссылки")
                val ext = info.ext?.takeIf { it.isNotBlank() } ?: "m4a"
                url to ext
            }
        })
        try {
            return task.get(45, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            task.cancel(true)
            throw IOException("таймаут 45 с")
        } catch (e: java.util.concurrent.ExecutionException) {
            throw IOException(e.cause?.message ?: "ошибка")
        }
    }

    private fun audioViaNewPipe(vid: String): Pair<String, String> {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$vid")
        val http = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val best = http.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: http.maxByOrNull { it.averageBitrate }
            ?: throw IOException("Нет доступного аудиопотока")
        val ext = best.format?.suffix ?: "m4a"
        return best.content to ext
    }

    private const val VR_VERSION = "1.60.19"
    private const val VR_UA =
        "com.google.android.apps.youtube.vr.oculus/$VR_VERSION (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

    private val fallbackHttp by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Запасной путь: запрос плеера InnerTube клиентом ANDROID_VR — отдаёт прямые ссылки без подписи. */
    private fun audioViaAndroidVr(vid: String): Pair<String, String> {
        val client = JSONObject()
            .put("clientName", "ANDROID_VR")
            .put("clientVersion", VR_VERSION)
            .put("deviceMake", "Oculus")
            .put("deviceModel", "Quest 3")
            .put("androidSdkVersion", 32)
            .put("osName", "Android")
            .put("osVersion", "12L")
            .put("hl", "en")
            .put("gl", "US")
        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", vid)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val req = okhttp3.Request.Builder()
            .url("https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent", VR_UA)
            .header("X-YouTube-Client-Name", "28")
            .header("X-YouTube-Client-Version", VR_VERSION)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        fallbackHttp.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val json = JSONObject(text)
            val formats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
            if (formats == null) {
                val reason = json.optJSONObject("playabilityStatus")?.optString("reason").orEmpty()
                throw IOException(reason.ifBlank { "нет потоков" })
            }
            var bestUrl: String? = null
            var bestExt = "m4a"
            var bestScore = -1L
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val mime = f.optString("mimeType")
                val url = f.optString("url")
                if (!mime.startsWith("audio/") || url.isBlank()) continue
                val isMp4 = mime.startsWith("audio/mp4")
                val score = (if (isMp4) 1_000_000_000L else 0L) + f.optLong("bitrate")   // m4a в приоритете
                if (score > bestScore) {
                    bestScore = score
                    bestUrl = url
                    bestExt = if (isMp4) "m4a" else "webm"
                }
            }
            return (bestUrl ?: throw IOException("нет аудио со ссылкой")) to bestExt
        }
    }
}
