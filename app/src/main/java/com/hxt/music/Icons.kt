package com.hxt.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Те же SVG-пути (viewBox 24×24), что и в index.html */
object Ic {
    val SEARCH = arrayOf("M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "m20 20-3.5-3.5")
    val GEAR = arrayOf(
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    )
    val BACK = arrayOf("M19 12H5M11 5l-7 7 7 7")
    val CLEAR = arrayOf("M6 6l12 12M18 6 6 18")
    val PEN = arrayOf("M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")
    val CHECK = arrayOf("M5 12.5l4.5 4.5L19 7.5")
    val HEART = arrayOf("M12 21s-7.5-4.6-9.6-9.2C.9 8.3 3 4.5 6.7 4.5c2 0 3.5 1.1 5.3 3 1.8-1.9 3.3-3 5.3-3 3.7 0 5.8 3.8 4.3 7.3C19.5 16.4 12 21 12 21z")
    val PREV = arrayOf("M6 5h2v14H6zM20 5v14L9 12z")
    val NEXT = arrayOf("M16 5h2v14h-2zM4 5v14l11-7z")
    val PLAY = arrayOf("M7 4.5v15l13-7.5z")
    val PAUSE = arrayOf("M6 4h4v16H6zM14 4h4v16h-4z")
    val MUSIC = arrayOf("M9 18V5l12-2v13", "M3 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", "M15 16a3 3 0 1 0 6 0a3 3 0 1 0 -6 0")
    val CHEVRON = arrayOf("m6 9 6 6 6-6")
    val REPEAT = arrayOf("M17 2l4 4-4 4", "M3 11V9a3 3 0 0 1 3-3h15", "M7 22l-4-4 4-4", "M21 13v2a3 3 0 0 1-3 3H3")
    val LYRICS = arrayOf("M4 6h16M4 12h10M4 18h16")
    val SHUFFLE = arrayOf("M16 3h5v5M4 20 21 3M21 16v5h-5M15 15l6 6M4 4l5 5")
    val DOTS = arrayOf(
        "M7.3 6a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0 -3.4 0",
        "M13.3 6a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0 -3.4 0",
        "M7.3 12a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0 -3.4 0",
        "M13.3 12a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0 -3.4 0",
        "M7.3 18a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0 -3.4 0",
        "M13.3 18a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0 -3.4 0",
    )
}

@Composable
fun Svg(
    paths: Array<String>,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Float = 0f,
    fill: Boolean = false,
) {
    val parsed = remember(paths) { paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, Offset.Zero) {
            parsed.forEach { p ->
                if (fill) drawPath(p, color)
                if (stroke > 0f) drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
