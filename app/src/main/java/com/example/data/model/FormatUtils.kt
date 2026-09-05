package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "Unavailable in Lightweight Mode"
        if (bytes == 0L) return "0 B"

        val k = 1024.0
        val sizes = arrayOf("B", "KB", "MB", "GB", "TB")
        val i = (Math.log(bytes.toDouble()) / Math.log(k)).toInt().coerceIn(0, sizes.size - 1)
        val value = bytes / Math.pow(k, i.toDouble())
        return String.format(Locale.US, "%.1f %s", value, sizes[i])
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec < 0) return "Unavailable in Lightweight Mode"
        if (bytesPerSec == 0L) return "0 B/s"

        val k = 1024.0
        val sizes = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        val i = (Math.log(bytesPerSec.toDouble()) / Math.log(k)).toInt().coerceIn(0, sizes.size - 1)
        val value = bytesPerSec / Math.pow(k, i.toDouble())
        return String.format(Locale.US, "%.1f %s", value, sizes[i])
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
