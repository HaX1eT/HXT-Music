package com.hxt.music

data class Track(
    val videoId: String,
    val title: String,
    val artist: String,
    val duration: String,
    val thumb: String,
    val liked: Boolean = false,
    val downloaded: Boolean = false,
)

enum class SearchState { Hint, Loading, Results, Empty, Error }

data class ImportItem(val line: String, val ok: Boolean, val found: String)

fun fmtTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0L)) / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
