package com.hxt.music

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Одна строка текста; timeMs = -1 для текста без синхронизации. */
data class LyricLine(val timeMs: Long, val text: String)

class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val instrumental: Boolean = false,
)

/**
 * Тексты с lrclib.net. Порядок поиска: точное совпадение (/api/get) → поиск по названию и исполнителю →
 * свободный поиск. Результат (и «не найдено», на 3 дня) кешируется в filesDir/lyrics, чтобы работало офлайн.
 */
object LrcLib {
    private const val API = "https://lrclib.net/api"
    private const val NONE_TTL = 3L * 24 * 3600 * 1000

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** null = текста нет (не найден). Сетевая ошибка — исключение (повторить можно позже). */
    fun load(ctx: Context, t: Track, fallbackDurSec: Int): Lyrics? {
        val dir = File(ctx.filesDir, "lyrics").apply { mkdirs() }
        val cache = File(dir, "${t.videoId}.json")
        if (cache.exists()) {
            runCatching {
                val o = JSONObject(cache.readText())
                if (!o.optBoolean("none")) return build(o)
                if (System.currentTimeMillis() - o.optLong("ts") < NONE_TTL) return null
            }
        }
        val dur = parseDur(t.duration).takeIf { it > 0 } ?: fallbackDurSec
        val found = find(t, dur)
        if (found == null) {
            runCatching { cache.writeText(JSONObject().put("none", true).put("ts", System.currentTimeMillis()).toString()) }
            return null
        }
        val store = JSONObject()
            .put("synced", found.optString("syncedLyrics").takeIf { it != "null" } ?: "")
            .put("plain", found.optString("plainLyrics").takeIf { it != "null" } ?: "")
            .put("instr", found.optBoolean("instrumental"))
        runCatching { cache.writeText(store.toString()) }
        return build(store)
    }

    private fun build(o: JSONObject): Lyrics? {
        if (o.optBoolean("instr")) return Lyrics(emptyList(), false, instrumental = true)
        val synced = parseLrc(o.optString("synced"))
        if (synced.isNotEmpty()) return Lyrics(synced, true)
        val plain = o.optString("plain").lines().map { it.trim() }
        // пустые строки между куплетами оставляем (но не по краям и не по несколько подряд)
        val out = ArrayList<LyricLine>()
        for (l in plain) {
            if (l.isEmpty() && (out.isEmpty() || out.last().text.isEmpty())) continue
            out += LyricLine(-1, l)
        }
        while (out.isNotEmpty() && out.last().text.isEmpty()) out.removeAt(out.lastIndex)
        return if (out.isEmpty()) null else Lyrics(out, false)
    }

    private val TS = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parseLrc(src: String): List<LyricLine> {
        val res = ArrayList<LyricLine>()
        for (raw in src.lines()) {
            val ms = TS.findAll(raw).toList()
            if (ms.isEmpty()) continue
            val text = raw.substring(ms.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (m in ms) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val fMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                res += LyricLine(min * 60_000 + sec * 1000 + fMs, text)
            }
        }
        return res.sortedBy { it.timeMs }
    }

    // ---------- поиск ----------

    private fun find(t: Track, dur: Int): JSONObject? {
        val title = cleanTitle(t.title)
        val artist = cleanArtist(t.artist)

        // 1. точное совпадение
        getObj(
            "get",
            "track_name" to title, "artist_name" to artist,
            *(if (dur > 0) arrayOf("duration" to dur.toString()) else emptyArray()),
        )?.let { if (usable(it)) return it }

        // 2. поиск по полям, затем свободный
        val tries = listOf(
            arrayOf("track_name" to title, "artist_name" to artist),
            arrayOf("q" to "$artist $title"),
            arrayOf("track_name" to title),
        )
        for (q in tries) {
            val items = getArr("search", *q).filter { sameTitle(it.optString("trackName"), title) }
            pick(items, dur)?.let { return it }
        }
        return null
    }

    private fun usable(o: JSONObject) =
        o.optBoolean("instrumental") || o.optString("syncedLyrics").let { it.isNotBlank() && it != "null" } ||
            o.optString("plainLyrics").let { it.isNotBlank() && it != "null" }

    private fun has(o: JSONObject, k: String) = o.optString(k).let { it.isNotBlank() && it != "null" }

    private fun pick(items: List<JSONObject>, dur: Int): JSONObject? {
        val close = items
            .filter { dur <= 0 || o(it) <= 0.0 || abs(o(it) - dur) <= 6.0 }
            .sortedBy { if (dur > 0) abs(o(it) - dur) else 0.0 }
        return close.firstOrNull { has(it, "syncedLyrics") }
            ?: close.firstOrNull { has(it, "plainLyrics") }
            ?: close.firstOrNull { it.optBoolean("instrumental") }
            ?: items.firstOrNull { has(it, "plainLyrics") }   // другая версия: тайминги не подойдут, обычный текст — да
    }

    private fun o(it: JSONObject) = it.optDouble("duration", 0.0)

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun sameTitle(a: String, b: String): Boolean {
        val x = norm(a); val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || x.contains(y) || y.contains(x)
    }

    fun cleanTitle(s: String): String = s
        .replace(Regex("\\s*[(\\[][^)\\]]*(feat|ft\\.|remaster|version|edit|official|prod|from)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+(feat\\.?|ft\\.?)\\s.*$", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifBlank { s }

    fun cleanArtist(s: String): String = s
        .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("VEVO$", RegexOption.IGNORE_CASE), "")
        .split(Regex("\\s*(,|&|\\bfeat\\.?|\\bft\\.?|\\bx\\b|;)\\s*", RegexOption.IGNORE_CASE))
        .first()
        .trim()
        .ifBlank { s }

    private fun parseDur(s: String): Int {
        val p = s.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (p.size) { 2 -> p[0] * 60 + p[1]; 3 -> p[0] * 3600 + p[1] * 60 + p[2]; else -> 0 }
    }

    // ---------- сеть ----------

    private fun call(path: String, params: Array<out Pair<String, String>>): String? {
        val url = "$API/$path".toHttpUrl().newBuilder().apply { params.forEach { addQueryParameter(it.first, it.second) } }.build()
        val req = Request.Builder().url(url).header("User-Agent", "HXT Music (Android)").build()
        http.newCall(req).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) throw IOException("lrclib HTTP ${r.code}")
            return r.body?.string()
        }
    }

    private fun getObj(path: String, vararg p: Pair<String, String>): JSONObject? =
        call(path, p)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun getArr(path: String, vararg p: Pair<String, String>): List<JSONObject> {
        val a = call(path, p)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
}
