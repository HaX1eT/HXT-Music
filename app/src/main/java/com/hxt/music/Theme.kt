package com.hxt.music

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat

/** Палитра 1-в-1 с :root в index.html */
object Col {
    val Bg = Color(0xFF0C0406)
    val Panel = Color(0xFF150A0D)
    val Card = Color(0xFF1D0E12)
    val Line = Color(0xFF2E1519)
    val Red = Color(0xFFC4122F)
    val RedHi = Color(0xFFEF2A4A)
    val RedDeep = Color(0xFF6B0A1A)
    val Text = Color(0xFFF5E9EB)
    val Muted = Color(0xFFA0818A)
    val Track = Color(0xFF3A1C22)
}

val Ease = CubicBezierEasing(.2f, .8f, .2f, 1f)

/**
 * Unbounded (заголовки) и Manrope (текст). Файлы скачиваются в CI в res/font/ (или лежат в репозитории).
 * Перед использованием каждый шрифт проверяется: файл должен существовать и реально читаться системой.
 * Иначе Compose упал бы при первой отрисовке, а так — тихий откат на системный шрифт.
 */
object Fonts {
    var display: FontFamily = FontFamily.Default
    var body: FontFamily = FontFamily.Default
    var lyrics: FontFamily = FontFamily.Default

    /** true — шрифт текстов песен (Manrope) найден и успешно загружен. */
    var lyricsLoaded = false
        private set

    private var inited = false

    /** id ресурса res/font/<name>, если файл есть и открывается; иначе 0. */
    private fun usable(ctx: Context, name: String): Int {
        val id = ctx.resources.getIdentifier(name, "font", ctx.packageName)
        if (id == 0) { Dbg.log("шрифт $name: файла нет в res/font"); return 0 }
        val ok = runCatching { ResourcesCompat.getFont(ctx, id) != null }.getOrDefault(false)
        if (!ok) Dbg.log("шрифт $name: файл повреждён или не читается")
        return if (ok) id else 0
    }

    private fun family(res: Int, weights: List<Int>) = FontFamily(weights.map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    })

    /**
     * Manrope из обычных (статических) файлов manrope_regular/medium/bold/extrabold.ttf — их CI нарезает
     * из вариативного шрифта. В отличие от оси wght, статическое начертание рисуется одинаково везде.
     */
    private fun manropeStatic(ctx: Context): FontFamily? {
        val parts = listOf(400 to "manrope_regular", 500 to "manrope_medium", 700 to "manrope_bold", 800 to "manrope_extrabold")
        val fonts = parts.map { (w, name) ->
            val id = usable(ctx, name)
            if (id == 0) return null
            Font(id, FontWeight(w))
        }
        return FontFamily(fonts)
    }

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        val d = usable(ctx, "unbounded")
        val b = usable(ctx, "manrope")
        if (d != 0) runCatching { display = family(d, listOf(600, 800)) }

        val st = runCatching { manropeStatic(ctx) }.getOrNull()
        if (st != null) {
            // основной путь: статические начертания
            body = st
            lyrics = st
            lyricsLoaded = true
        } else if (b != 0) {
            // запасной путь: вариативный шрифт (вес может не применяться на части устройств)
            runCatching {
                body = family(b, listOf(400, 500, 700))
                lyrics = family(b, listOf(700, 800))
                lyricsLoaded = true
            }
        }
        Dbg.log("шрифты: unbounded=${d != 0}, manrope=${b != 0}, manrope-статика=${st != null}")
    }
}
