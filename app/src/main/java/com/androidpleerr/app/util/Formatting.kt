package com.androidpleerr.app.util

import java.util.Locale

/** Human-friendly formatting for byte counts / speeds used across the UI. */
object Formatting {

    fun formatBytes(bytes: Long?): String {
        val b = bytes ?: 0L
        if (b < 1024) return "$b B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = b.toDouble()
        var i = -1
        do {
            value /= 1024.0
            i++
        } while (value >= 1024 && i < units.size - 1)
        return String.format(Locale.US, "%.1f %s", value, units[i])
    }

    fun formatSpeed(bytesPerSec: Long?): String = "${formatBytes(bytesPerSec)}/s"

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }
}
