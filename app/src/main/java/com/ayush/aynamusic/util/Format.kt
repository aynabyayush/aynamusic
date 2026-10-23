package com.ayush.aynamusic.util

import java.text.NumberFormat
import java.util.Locale

fun Long.asClock(): String {
    if (this <= 0L) return "0:00"
    val totalSeconds = this / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun Long.asCompactCount(): String {
    if (this < 1_000) return this.toString()
    if (this < 1_000_000) return String.format(Locale.US, "%.1fK", this / 1_000.0)
    return String.format(Locale.US, "%.1fM", this / 1_000_000.0)
}

fun Long.asStorageSize(): String {
    if (this <= 0) return "0 MB"
    val kb = this / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

fun String.initials(): String = trim().split(Regex("\\s+"))
    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
    .take(2)
    .joinToString("")

fun String.normalizedForSearch(): String = lowercase(Locale.ROOT).trim()

fun String.toDisplayCount(): String = NumberFormat.getIntegerInstance().format(this.toIntOrNull() ?: 0)
