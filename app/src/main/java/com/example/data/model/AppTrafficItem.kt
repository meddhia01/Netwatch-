package com.example.data.model

import android.graphics.drawable.Drawable

data class AppTrafficItem(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val rxBytes: Long,
    val txBytes: Long,
    val totalBytes: Long,
    val currentRxSpeedBps: Long?,
    val currentTxSpeedBps: Long?,
    val isSystemApp: Boolean,
    val isAvailable: Boolean = true
)
