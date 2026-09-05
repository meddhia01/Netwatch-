package com.example.data.model

data class TrafficSnapshot(
    val totalRxBytes: Long,
    val totalTxBytes: Long,
    val rxSpeedBps: Long,
    val txSpeedBps: Long,
    val networkType: NetworkType,
    val timestamp: Long,
    val isLightweightMode: Boolean = true,
    val isStatsSupported: Boolean = true
)
