package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "traffic_records")
data class TrafficRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val rxSpeedBps: Long,
    val txSpeedBps: Long,
    val networkType: String,
    val packageOrUid: String? = null
)
