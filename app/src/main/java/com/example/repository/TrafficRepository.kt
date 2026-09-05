package com.example.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TrafficRecord
import com.example.data.model.AppTrafficItem
import com.example.data.model.FormatUtils
import com.example.data.model.NetworkType
import com.example.data.model.TrafficSnapshot
import com.example.monitoring.NetworkStatsHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TrafficRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val networkStatsHelper = NetworkStatsHelper(context)
    private val database = AppDatabase.getInstance(context)
    private val dao = database.trafficRecordDao()

    private val _currentSnapshot = MutableStateFlow(
        TrafficSnapshot(
            totalRxBytes = 0L,
            totalTxBytes = 0L,
            rxSpeedBps = 0L,
            txSpeedBps = 0L,
            networkType = NetworkType.DISCONNECTED,
            timestamp = System.currentTimeMillis()
        )
    )
    val currentSnapshot: StateFlow<TrafficSnapshot> = _currentSnapshot.asStateFlow()

    private val _refreshIntervalSec = MutableStateFlow(2L)
    val refreshIntervalSec: StateFlow<Long> = _refreshIntervalSec.asStateFlow()

    private val _isMonitoringActive = MutableStateFlow(true)
    val isMonitoringActive: StateFlow<Boolean> = _isMonitoringActive.asStateFlow()

    val historyRecords: Flow<List<TrafficRecord>> = dao.getRecentRecords()
    val recordCount: Flow<Int> = dao.getRecordCount()

    private var monitorJob: Job? = null
    private var lastDbWriteTime = 0L
    private val DB_SAMPLE_INTERVAL_MS = 15_000L // Sample history every 15 seconds to minimize writes

    fun startMonitoring(scope: CoroutineScope) {
        monitorJob?.cancel()
        _isMonitoringActive.value = true
        monitorJob = scope.launch(ioDispatcher) {
            while (isActive && _isMonitoringActive.value) {
                try {
                    val snapshot = networkStatsHelper.getSystemTrafficSnapshot()
                    _currentSnapshot.value = snapshot

                    val now = System.currentTimeMillis()
                    // Sensible sampling: Save to Room at most once every 15 seconds
                    if (now - lastDbWriteTime >= DB_SAMPLE_INTERVAL_MS && snapshot.isStatsSupported) {
                        dao.insertRecord(
                            TrafficRecord(
                                timestamp = now,
                                rxBytes = snapshot.totalRxBytes,
                                txBytes = snapshot.totalTxBytes,
                                rxSpeedBps = snapshot.rxSpeedBps,
                                txSpeedBps = snapshot.txSpeedBps,
                                networkType = snapshot.networkType.displayName
                            )
                        )
                        dao.pruneOldRecords()
                        lastDbWriteTime = now
                    }
                } catch (_: Exception) {
                    // Prevent any crash from interrupting the monitor loop
                }

                val intervalMs = (_refreshIntervalSec.value * 1000L).coerceAtLeast(1000L)
                delay(intervalMs)
            }
        }
    }

    fun stopMonitoring() {
        _isMonitoringActive.value = false
        monitorJob?.cancel()
        monitorJob = null
    }

    fun setRefreshInterval(seconds: Long) {
        _refreshIntervalSec.value = seconds.coerceIn(1L, 10L)
    }

    fun hasUsageAccess(): Boolean {
        return networkStatsHelper.hasUsageAccess()
    }

    suspend fun getPerAppTraffic(forceNsmRefresh: Boolean = false): List<AppTrafficItem> = withContext(ioDispatcher) {
        try {
            networkStatsHelper.getPerAppTraffic(forceNsmRefresh)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun clearHistory() = withContext(ioDispatcher) {
        dao.clearAll()
    }

    suspend fun exportHistoryCsv(context: Context): Intent? = withContext(ioDispatcher) {
        try {
            val records = dao.getAllRecordsForExport()
            if (records.isEmpty()) return@withContext null

            val cacheDir = context.cacheDir
            val exportFile = File(cacheDir, "netwatch_traffic_history.csv")
            FileOutputStream(exportFile).bufferedWriter().use { writer ->
                writer.write("ID,Timestamp,Formatted_Time,Network_Type,Total_RX_Bytes,Total_TX_Bytes,RX_Speed_Bps,TX_Speed_Bps,Formatted_RX_Speed,Formatted_TX_Speed\n")
                for (rec in records) {
                    writer.write(
                        "${rec.id}," +
                        "${rec.timestamp}," +
                        "\"${FormatUtils.formatDateTime(rec.timestamp)}\"," +
                        "\"${rec.networkType}\"," +
                        "${rec.rxBytes}," +
                        "${rec.txBytes}," +
                        "${rec.rxSpeedBps}," +
                        "${rec.txSpeedBps}," +
                        "\"${FormatUtils.formatSpeed(rec.rxSpeedBps)}\"," +
                        "\"${FormatUtils.formatSpeed(rec.txSpeedBps)}\"\n"
                    )
                }
            }

            val uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile
                )
            } catch (_: Exception) {
                FileProvider.getUriForFile(
                    context,
                    "com.meddhia.netwatch.fileprovider",
                    exportFile
                )
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "NetWatch Traffic History Export")
                clipData = android.content.ClipData.newRawUri("NetWatch Traffic CSV", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            Intent.createChooser(shareIntent, "Export Traffic History (CSV)").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            null
        }
    }
}
