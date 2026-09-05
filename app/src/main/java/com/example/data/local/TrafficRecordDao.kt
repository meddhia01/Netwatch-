package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficRecordDao {
    @Query("SELECT * FROM traffic_records ORDER BY timestamp DESC LIMIT 150")
    fun getRecentRecords(): Flow<List<TrafficRecord>>

    @Query("SELECT * FROM traffic_records ORDER BY timestamp ASC")
    suspend fun getAllRecordsForExport(): List<TrafficRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TrafficRecord)

    @Query("DELETE FROM traffic_records")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM traffic_records")
    fun getRecordCount(): Flow<Int>

    @Query("DELETE FROM traffic_records WHERE id NOT IN (SELECT id FROM traffic_records ORDER BY timestamp DESC LIMIT 500)")
    suspend fun pruneOldRecords()
}
