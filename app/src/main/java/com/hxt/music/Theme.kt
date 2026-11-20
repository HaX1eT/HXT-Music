package com.hxt.music

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

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
 * Unbounded (заголовки) и Manrope (текст). Файлы скачиваются в CI в res/font/.
 * Если их нет — используется системный шрифт, приложение всё равно работает.
 */
object Fonts {
    var display: FontFamily = FontFamily.Default
    var body: FontFamily = FontFamily.Default
    var lyrics: FontFamily = FontFamily.Default

    fun init(ctx: Context) {
        val d = ctx.resources.getIdentifier("unbounded", "font", ctx.packageName)
        val b = ctx.resources.getIdentifier("manrope", "font", ctx.packageName)
        runCatching {
            if (d != 0) display = FontFamily(listOf(600, 800).map { w ->
                Font(d, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
            })
        }
        runCatching {
            if (b != 0) body = FontFamily(listOf(400, 500, 700).map { w ->
                Font(b, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
            })
        }
        // шрифт текстов песен: Manrope, насыщенные начертания
        runCatching {
            if (b != 0) lyrics = FontFamily(listOf(700, 800).map { w ->
                Font(b, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
            })
        }
    }
}
