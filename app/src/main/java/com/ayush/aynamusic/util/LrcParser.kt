package com.ayush.aynamusic.util

import com.ayush.aynamusic.data.model.LyricLine

private val timeTag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

fun parseLrc(raw: String): List<LyricLine> {
    if (raw.isBlank()) return emptyList()
    val lines = mutableListOf<LyricLine>()
    raw.lineSequence().forEach { line ->
        val matches = timeTag.findAll(line).toList()
        if (matches.isEmpty()) return@forEach
        val text = timeTag.replace(line, "").trim()
        if (text.isEmpty()) return@forEach
        matches.forEach { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
            val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
            val fraction = match.groupValues[3]
            val millis = when (fraction.length) {
                0 -> 0L
                1 -> fraction.toLong() * 100
                2 -> fraction.toLong() * 10
                else -> fraction.take(3).toLong()
            }
            lines += LyricLine((minutes * 60 + seconds) * 1000 + millis, text)
        }
    }
    return lines.sortedBy { it.timeMs }
}
