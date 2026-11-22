package com.hxt.music

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
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

    /**
     * Ручной бэкап: ничего локального не удаляется. Всё из liked.json добавляется в Liked
     * (в конец, после текущих), затем в liked.json записывается объединённый список —
     * так в копию попадают и треки, лайкнутые до включения бэкапа.
     * Возвращает (сколько добавлено из копии, сколько теперь всего) или null, если нет доступа.
     */
    fun mergeBackup(): Pair<Int, Int>? = synchronized(lock) {
        if (!hasBackupAccess()) return null
        val current = likedTracks().map { it.videoId }
        val saved = Backup.read()
        var added = 0
        for (t in saved) {
            val e = db[t.videoId]
            if (e == null) {
                db[t.videoId] = JSONObject().put("title", t.title).put("artist", t.artist)
                    .put("duration", t.duration).put("thumb", t.thumb).put("liked", true)
                added++
            } else if (!e.optBoolean("liked", false)) {
                e.put("liked", true)
                added++
            }
        }
        if (added > 0) {
            saveDb()
            order = current + saved.map { it.videoId }.filter { it !in current }
            orderFile.writeText(JSONArray(order).toString())
        }
        writeBackup()
        added to likedTracks().size
    }

    fun audioFile(vid: String): File? =
        audioDir.listFiles()?.firstOrNull { it.name.substringBeforeLast('.') == vid && !it.name.endsWith(".part") }

    fun coverFile(vid: String): File = File(coversDir, "$vid.jpg")

    /** Проставляет liked/downloaded из библиотеки (аналог parse() в server.py). */
    fun decorate(t: Track): Track = synchronized(lock) {
        t.copy(
            liked = db[t.videoId]?.optBoolean("liked", false) ?: false,
            downloaded = audioFile(t.videoId) != null,
            custom = db[t.videoId]?.optBoolean("custom", false) ?: false,
        )
    }

    /** Новые лайки — сверху, дальше — пользовательский порядок. */
    fun likedTracks(): List<Track> = synchronized(lock) {
        val items = db.entries.filter { it.value.optBoolean("liked", false) }.map { (k, v) ->
            Track(k, v.optString("title"), v.optString("artist"), v.optString("duration"),
                v.optString("thumb"), liked = true, downloaded = audioFile(k) != null,
                custom = v.optBoolean("custom", false))
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

    // ---------- свои звуковые дорожки ----------

    private val idOk = Regex("[A-Za-z0-9_-]{5,32}")

    /** Удаляет все аудиофайлы трека, кроме `keep` (временный файл, из которого сейчас делается новая дорожка). */
    private fun deleteAudio(vid: String, keep: File? = null) {
        audioDir.listFiles()?.filter { it.name.startsWith("$vid.") && it != keep }?.forEach { it.delete() }
    }

    private fun copyAtomic(src: File, dst: File) {
        val tmp = File(dst.path + ".tmp")
        try {
            src.inputStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            dst.delete()
            if (!tmp.renameTo(dst)) throw IOException("rename failed")
        } finally {
            tmp.delete()
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        appCtx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Длительность аудиофайла в мс; null — файл не читается как аудио. */
    private fun probeDuration(f: File): Long? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /**
     * Заменяет звуковую дорожку трека файлом из памяти телефона. Название, автор и обложка не меняются.
     * Возвращает новую длительность («м:сс»). Копия сразу уходит в бэкап (если выдан доступ).
     */
    suspend fun setCustomAudio(t: Track, uri: Uri): String = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val ext = name?.substringAfterLast('.', "")?.lowercase()
            ?.takeIf { Regex("[a-z0-9]{2,5}").matches(it) && it != "part" && it != "tmp" }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(appCtx.contentResolver.getType(uri))
            ?: "mp3"
        val mu = dlLocks.getOrPut(t.videoId) { Mutex() }
        val dur = mu.withLock {
            val part = File(audioDir, "${t.videoId}.$ext.part")
            val dest = File(audioDir, "${t.videoId}.$ext")
            val d = try {
                val input = appCtx.contentResolver.openInputStream(uri) ?: throw IOException("Не удалось открыть файл")
                input.use { i -> part.outputStream().use { o -> i.copyTo(o) } }
                if (part.length() == 0L) throw IOException("Пустой файл")
                val ms = probeDuration(part)
                if (ms == null || ms <= 0L) throw IOException("Файл не похож на аудио")
                deleteAudio(t.videoId, keep = part)
                if (!part.renameTo(dest)) throw IOException("Не удалось сохранить файл")
                fmtTime(ms)
            } finally {
                part.delete()
            }
            val cover = coverFile(t.videoId)
            if (!cover.exists() && t.thumb.isNotBlank()) runCatching { cover.writeBytes(getBytes(t.thumb)) }
            d
        }
        synchronized(lock) {
            val e = db.getOrPut(t.videoId) {
                JSONObject().put("title", t.title).put("artist", t.artist).put("thumb", t.thumb)
            }
            e.put("duration", dur).put("custom", true).put("customName", name ?: "")
            saveDb()
            writeBackup()
            writeCustomBackup()
        }
        Dbg.log("[${t.videoId}] своя дорожка: ${name ?: ext}, $dur")
        dur
    }

    /** Копия своих дорожек: аудио + обложки + custom.json. Копируется только то, чего в папке ещё нет. Вызывать под lock. */
    private fun writeCustomBackup() {
        if (!hasBackupAccess()) return
        runCatching {
            val dir = Backup.customDir.apply { mkdirs() }
            val entries = ArrayList<CustomEntry>()
            for ((k, v) in db) {
                if (!v.optBoolean("custom", false) || !idOk.matches(k)) continue
                val a = audioFile(k) ?: continue
                val audioName = "$k.${a.extension}"
                val dst = File(dir, audioName)
                if (!dst.isFile || dst.length() != a.length()) copyAtomic(a, dst)
                var coverName = ""
                val c = coverFile(k)
                if (c.exists()) {
                    val cd = File(dir, "$k.jpg")
                    if (!cd.isFile || cd.length() != c.length()) copyAtomic(c, cd)
                    coverName = cd.name
                }
                entries += CustomEntry(k, v.optString("title"), v.optString("artist"), v.optString("duration"),
                    v.optString("thumb"), audioName, coverName, v.optString("customName"))
            }
            // убираем только свои устаревшие файлы (например, прежнее расширение того же трека)
            val keep = entries.flatMap { listOf(it.audio, it.cover) }.toSet()
            val ids = entries.map { it.id }.toSet()
            dir.listFiles()?.forEach { f -> if (f.name !in keep && f.nameWithoutExtension in ids) f.delete() }
            Backup.writeCustom(entries)
        }.onFailure { Dbg.log("custom backup: $it") }
    }

    /**
     * Двусторонняя синхронизация своих дорожек с копией в памяти телефона:
     * дорожки из копии, которых нет локально (после переустановки), возвращаются и снова привязываются
     * к своим трекам; локальные — дописываются в копию. Возвращает (восстановлено, всего своих)
     * или null, если нет доступа к файлам.
     */
    fun syncCustom(): Pair<Int, Int>? = synchronized(lock) {
        if (!hasBackupAccess()) return null
        val dir = Backup.customDir
        var restored = 0
        for (e in Backup.readCustom()) {
            if (!idOk.matches(e.id)) continue
            val local = db[e.id]
            if (local?.optBoolean("custom", false) == true && audioFile(e.id) != null) continue
            val src = File(dir, File(e.audio).name)
            if (!src.isFile || src.length() == 0L) continue
            runCatching {
                deleteAudio(e.id)
                copyAtomic(src, File(audioDir, "${e.id}.${src.extension.ifBlank { "mp3" }}"))
                if (e.cover.isNotBlank()) {
                    val cs = File(dir, File(e.cover).name)
                    val cd = coverFile(e.id)
                    if (cs.isFile && !cd.exists()) copyAtomic(cs, cd)
                }
                val o = local ?: JSONObject().put("title", e.title).put("artist", e.artist)
                    .put("thumb", e.thumb).also { db[e.id] = it }
                if (e.duration.isNotBlank()) o.put("duration", e.duration)
                o.put("custom", true).put("customName", e.name)
                restored++
            }.onFailure { Dbg.log("[${e.id}] восстановление своей дорожки: $it") }
        }
        if (restored > 0) saveDb()
        writeCustomBackup()
        restored to db.values.count { it.optBoolean("custom", false) }
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
