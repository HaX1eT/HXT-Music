package com.hxt.music

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Библиотека: каждый трек скачивается ОДИН раз в files/library/audio,
 * обложка — в covers, метаданные и лайки — library.json, ручной порядок Liked — order.json.
 * Логика один в один с server.py.
 */
class Library(ctx: Context, private val client: OkHttpClient) {
    private val appCtx: Context = ctx.applicationContext
    private val base = File(ctx.filesDir, "library").apply { mkdirs() }
    private val audioDir = File(base, "audio").apply { mkdirs() }
    private val coversDir = File(base, "covers").apply { mkdirs() }
    private val dbFile = File(base, "library.json")
    private val orderFile = File(base, "order.json")

    private val lock = Any()
    private val db = LinkedHashMap<String, JSONObject>()
    private var order: List<String> = emptyList()
    private val dlLocks = ConcurrentHashMap<String, Mutex>()

    init {
        runCatching {
            if (dbFile.exists()) {
                val o = JSONObject(dbFile.readText())
                o.keys().forEach { k -> db[k] = o.getJSONObject(k) }
            }
        }
        runCatching {
            if (orderFile.exists()) {
                val a = JSONArray(orderFile.readText())
                order = List(a.length()) { a.getString(it) }
            }
        }
    }

    private fun saveDb() {
        val o = JSONObject()
        db.forEach { (k, v) -> o.put(k, v) }
        dbFile.writeText(o.toString())
    }

    /** Копия Liked на внешнее хранилище (только если выдан доступ). Вызывать под lock. */
    private fun writeBackup() {
        if (!hasBackupAccess()) return
        Backup.write(likedTracks())
    }

    private fun hasBackupAccess() = Backup.hasAccess(appCtx)

    /**
     * Подтягивает из backup треки, которых нет в локальной базе (после переустановки),
     * затем перезаписывает backup актуальным состоянием. Аудио не качается —
     * оно скачается при первом воспроизведении. Возвращает число восстановленных.
     */
    fun syncBackup(): Int = synchronized(lock) {
        if (!hasBackupAccess()) return 0
        val saved = Backup.read()
        var restored = 0
        for (t in saved) {
            if (db.containsKey(t.videoId)) continue
            db[t.videoId] = JSONObject().put("title", t.title).put("artist", t.artist)
                .put("duration", t.duration).put("thumb", t.thumb).put("liked", true)
            restored++
        }
        if (restored > 0) {
            saveDb()
            // порядок из backup; свои (локальные) позиции сохраняем после
            val ids = saved.map { it.videoId }
            order = ids + order.filter { it !in ids }
            orderFile.writeText(JSONArray(order).toString())
        }
        writeBackup()
        restored
    }

    fun audioFile(vid: String): File? =
        audioDir.listFiles()?.firstOrNull { it.name.substringBeforeLast('.') == vid && !it.name.endsWith(".part") }

    fun coverFile(vid: String): File = File(coversDir, "$vid.jpg")

    /** Проставляет liked/downloaded из библиотеки (аналог parse() в server.py). */
    fun decorate(t: Track): Track = synchronized(lock) {
        t.copy(
            liked = db[t.videoId]?.optBoolean("liked", false) ?: false,
            downloaded = audioFile(t.videoId) != null,
        )
    }

    /** Новые лайки — сверху, дальше — пользовательский порядок. */
    fun likedTracks(): List<Track> = synchronized(lock) {
        val items = db.entries.filter { it.value.optBoolean("liked", false) }.map { (k, v) ->
            Track(k, v.optString("title"), v.optString("artist"), v.optString("duration"),
                v.optString("thumb"), liked = true, downloaded = audioFile(k) != null)
        }
        val pos = order.withIndex().associate { it.value to it.index }
        val fresh = items.filter { it.videoId !in pos }.reversed()
        val kept = items.filter { it.videoId in pos }.sortedBy { pos[it.videoId] }
        fresh + kept
    }

    fun saveOrder(ids: List<String>) = synchronized(lock) {
        order = ids
        orderFile.writeText(JSONArray(ids).toString())
        writeBackup()
    }

    /** Скачивает аудио и обложку, только если их ещё нет. */
    suspend fun ensureDownloaded(t: Track) = withContext(Dispatchers.IO) {
        val mu = dlLocks.getOrPut(t.videoId) { Mutex() }
        mu.withLock {
            if (audioFile(t.videoId) == null) {
                try {
                    Dbg.log("[${t.videoId}] скачивание: ${t.title}")
                    val (url, ext) = Music.audioStream(t.videoId)
                    Dbg.log("[${t.videoId}] ссылка: хост=${runCatching { android.net.Uri.parse(url).host }.getOrNull()}, ext=$ext")
                    download(url, File(audioDir, "${t.videoId}.$ext"))
                    Dbg.log("[${t.videoId}] файл сохранён")
                } catch (e: Exception) {
                    Dbg.log("[${t.videoId}] ОШИБКА скачивания: $e")
                    throw e
                }
            }
            val cover = coverFile(t.videoId)
            if (!cover.exists() && t.thumb.isNotBlank()) {
                runCatching { cover.writeBytes(getBytes(t.thumb)) }
            }
        }
        synchronized(lock) {
            val e = db.getOrPut(t.videoId) { JSONObject() }
            e.put("title", t.title).put("artist", t.artist).put("duration", t.duration).put("thumb", t.thumb)
            saveDb()
        }
    }

    /** Лайк = трек сохранён в библиотеке. Возвращает новое состояние лайка. */
    suspend fun toggleLike(t: Track): Boolean {
        val was = synchronized(lock) { db[t.videoId]?.optBoolean("liked", false) ?: false }
        if (!was) ensureDownloaded(t)
        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                val e = db.getOrPut(t.videoId) { JSONObject() }
                val now = !e.optBoolean("liked", false)
                e.put("liked", now)
                saveDb()
                writeBackup()
                now
            }
        }
    }

    suspend fun setLiked(vid: String, value: Boolean) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            db.getOrPut(vid) { JSONObject() }.put("liked", value)
            saveDb()
            writeBackup()
        }
    }

    // ---------- сеть ----------

    private fun req(url: String) = Request.Builder().url(url).header("User-Agent", UA).build()

    private fun getBytes(url: String): ByteArray =
        client.newCall(req(url)).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body!!.bytes()
        }

    private fun download(url: String, dest: File) {
        val part = File(dest.path + ".part")
        try {
            ranged(url, part)
            if (part.length() == 0L) throw IOException("Пустой файл")
            dest.delete()
            if (!part.renameTo(dest)) throw IOException("Не удалось сохранить файл")
        } finally {
            part.delete()
        }
    }

    private class Chunk(val bytes: ByteArray, val total: Long, val whole: Boolean)

    private fun fetchChunk(url: String, start: Long, step: Long): Chunk {
        val rq = Request.Builder().url(url).header("User-Agent", UA)
            .header("Range", "bytes=$start-${start + step - 1}").build()
        return client.newCall(rq).execute().use { r ->
            if (r.code != 200 && r.code != 206) throw IOException("HTTP ${r.code}")
            val bytes = r.body!!.bytes()
            val whole = r.code == 200
            val total = if (whole) bytes.size.toLong()
            else r.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
            Chunk(bytes, total, whole)
        }
    }

    private fun fetchChunkRetry(url: String, start: Long, step: Long): Chunk {
        var attempt = 0
        while (true) {
            try {
                return fetchChunk(url, start, step)
            } catch (e: IOException) {
                attempt++
                Dbg.log("кусок с $start: попытка $attempt: $e")
                if (attempt >= 4) throw e
                Thread.sleep(500L * attempt)
            }
        }
    }

    /**
     * Загрузка кусками по 1 МБ через заголовок Range (так делает и yt-dlp).
     * YouTube рвёт соединение («Software caused connection abort»), если качать файл целиком одним запросом.
     * Каждый кусок читается в память и при обрыве перезапрашивается заново.
     */
    private fun ranged(url: String, out: File) {
        out.delete()
        val step = 1_048_576L
        var start = 0L
        var total = -1L
        out.outputStream().use { o ->
            while (total < 0 || start < total) {
                val c = fetchChunkRetry(url, start, step)
                if (c.whole && start != 0L) throw IOException("сервер игнорирует Range")
                if (c.bytes.isEmpty()) throw IOException("пустой кусок")
                o.write(c.bytes)
                start += c.bytes.size
                total = when {
                    c.whole -> start
                    c.total >= 0 -> c.total
                    c.bytes.size < step -> start
                    else -> -1L
                }
            }
        }
        Dbg.log("ranged: готово, $start байт")
    }

    /** Обычная загрузка одним запросом. */
    private fun plain(url: String, out: File): Boolean {
        client.newCall(req(url)).execute().use { r ->
            Dbg.log("plain: HTTP ${r.code}, размер=${r.body?.contentLength()}")
            if (!r.isSuccessful) return false
            val n = r.body!!.byteStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            Dbg.log("plain: скачано $n байт")
        }
        return true
    }

    /** Запасной вариант: кусками по 1 МБ через параметр range (если сервер режет обычную загрузку). */
    private fun chunked(url: String, out: File) {
        Dbg.log("chunked: старт")
        out.delete()
        val step = 1_048_576L
        var start = 0L
        out.outputStream().use { o ->
            while (true) {
                client.newCall(req("$url&range=$start-${start + step - 1}")).execute().use { r ->
                    if (!r.isSuccessful) { Dbg.log("chunked: HTTP ${r.code} на $start"); throw IOException("HTTP ${r.code}") }
                    val n = r.body!!.byteStream().copyTo(o)
                    start += n
                    if (n < step) return
                }
            }
        }
    }
}
