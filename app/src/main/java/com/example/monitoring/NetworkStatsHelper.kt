package com.example.monitoring

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import com.example.data.model.AppTrafficItem
import com.example.data.model.NetworkType
import com.example.data.model.TrafficSnapshot

class NetworkStatsHelper(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val networkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager

    private val packageManager = context.packageManager

    // Byte counter state for system-wide rate deltas
    private var lastRxBytes: Long = 0L
    private var lastTxBytes: Long = 0L
    private var lastTimestamp: Long = 0L

    // Byte counter state for per-UID rate deltas
    private val lastUidRxMap = mutableMapOf<Int, Long>()
    private val lastUidTxMap = mutableMapOf<Int, Long>()
    private var lastUidTimestamp: Long = 0L

    // Cache for NetworkStatsManager aggregate results to prevent expensive queries every 1s/2s
    private var lastNsmQueryTimestamp: Long = 0L
    private val nsmStatsCache = mutableMapOf<Int, Pair<Long, Long>>()
    private val NSM_CACHE_TTL_MS = 30_000L // Query NetworkStatsManager at most every 30 seconds

    /**
     * Checks if the app has PACKAGE_USAGE_STATS (Usage Access) permission.
     */
    fun hasUsageAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Detects the currently active network transport type.
     */
    fun getCurrentNetworkType(): NetworkType {
        try {
            val activeNetwork = connectivityManager?.activeNetwork ?: return NetworkType.DISCONNECTED
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                ?: return NetworkType.DISCONNECTED

            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkType.BLUETOOTH
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
                else -> NetworkType.OTHER
            }
        } catch (_: Exception) {
            return NetworkType.OTHER
        }
    }

    /**
     * Samples the system-wide traffic stats using TrafficStats and calculates real deltas.
     */
    @Synchronized
    fun getSystemTrafficSnapshot(): TrafficSnapshot {
        val currentRx = TrafficStats.getTotalRxBytes()
        val currentTx = TrafficStats.getTotalTxBytes()
        val now = System.currentTimeMillis()
        val currentNetType = getCurrentNetworkType()

        // Handle TrafficStats unsupported (-1)
        if (currentRx == TrafficStats.UNSUPPORTED.toLong() || currentTx == TrafficStats.UNSUPPORTED.toLong()) {
            return TrafficSnapshot(
                totalRxBytes = -1L,
                totalTxBytes = -1L,
                rxSpeedBps = 0L,
                txSpeedBps = 0L,
                networkType = currentNetType,
                timestamp = now,
                isLightweightMode = true,
                isStatsSupported = false
            )
        }

        var rxSpeed = 0L
        var txSpeed = 0L

        if (lastTimestamp > 0L && now > lastTimestamp) {
            val elapsedSec = (now - lastTimestamp).toDouble() / 1000.0
            if (elapsedSec > 0.1) {
                // Check if counter reset or reboot happened
                if (currentRx >= lastRxBytes) {
                    rxSpeed = ((currentRx - lastRxBytes) / elapsedSec).toLong()
                }
                if (currentTx >= lastTxBytes) {
                    txSpeed = ((currentTx - lastTxBytes) / elapsedSec).toLong()
                }
            }
        }

        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastTimestamp = now

        return TrafficSnapshot(
            totalRxBytes = currentRx,
            totalTxBytes = currentTx,
            rxSpeedBps = rxSpeed,
            txSpeedBps = txSpeed,
            networkType = currentNetType,
            timestamp = now,
            isLightweightMode = true,
            isStatsSupported = true
        )
    }

    /**
     * Queries per-app traffic statistics.
     * Uses TrafficStats per UID as baseline and integrates NetworkStatsManager when Usage Access is available.
     * Throttles NetworkStatsManager queries to avoid expensive binder IPC every second.
     * Calculates real delta rates between consecutive calls without fabricating unobservable values.
     */
    @Synchronized
    fun getPerAppTraffic(forceNsmRefresh: Boolean = false): List<AppTrafficItem> {
        val now = System.currentTimeMillis()
        val elapsedSec = if (lastUidTimestamp > 0L && now > lastUidTimestamp) {
            (now - lastUidTimestamp).toDouble() / 1000.0
        } else {
            0.0
        }

        val hasUsage = hasUsageAccess()
        val installedApps: List<ApplicationInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                packageManager.getInstalledApplications(0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        // Group by UID because multiple packages can share a UID (e.g. system services)
        val uidGroups = installedApps.groupBy { it.uid }
        val result = mutableListOf<AppTrafficItem>()

        // Check whether NSM cache should be refreshed (throttled to at most every 30 seconds unless forced)
        val shouldRefreshNsm = hasUsage && networkStatsManager != null &&
                (forceNsmRefresh || (now - lastNsmQueryTimestamp >= NSM_CACHE_TTL_MS))

        if (shouldRefreshNsm) {
            lastNsmQueryTimestamp = now
        }

        for ((uid, apps) in uidGroups) {
            // Pick primary app representative
            val primaryApp = apps.firstOrNull() ?: continue
            val packageName = primaryApp.packageName
            val isSystem = (primaryApp.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

            var rxBytes = 0L
            var txBytes = 0L
            var isAvailable = false

            // Check cached or refreshed NetworkStatsManager if Usage Access is granted
            var querySucceededWithNsm = false
            if (hasUsage && networkStatsManager != null) {
                if (shouldRefreshNsm) {
                    try {
                        val nsmStats = queryNsmForUid(uid)
                        if (nsmStats != null) {
                            nsmStatsCache[uid] = nsmStats
                        }
                    } catch (_: Exception) {
                        // Keep any existing cache
                    }
                }
                val cached = nsmStatsCache[uid]
                if (cached != null) {
                    rxBytes = cached.first
                    txBytes = cached.second
                    isAvailable = true
                    querySucceededWithNsm = true
                }
            }

            // Fallback to TrafficStats if NetworkStatsManager wasn't used
            if (!querySucceededWithNsm) {
                try {
                    val uidRx = TrafficStats.getUidRxBytes(uid)
                    val uidTx = TrafficStats.getUidTxBytes(uid)
                    if (uidRx != TrafficStats.UNSUPPORTED.toLong() && uidTx != TrafficStats.UNSUPPORTED.toLong()) {
                        rxBytes = uidRx
                        txBytes = uidTx
                        isAvailable = true
                    } else {
                        isAvailable = false
                    }
                } catch (_: Exception) {
                    isAvailable = false
                }
            }

            // Calculate delta speed for UID only if real previous data exists and delta is non-negative
            var currentRxSpeed: Long? = null
            var currentTxSpeed: Long? = null

            if (isAvailable && elapsedSec > 0.1) {
                val prevRx = lastUidRxMap[uid]
                val prevTx = lastUidTxMap[uid]

                if (prevRx != null && rxBytes >= prevRx) {
                    currentRxSpeed = ((rxBytes - prevRx) / elapsedSec).toLong()
                }
                if (prevTx != null && txBytes >= prevTx) {
                    currentTxSpeed = ((txBytes - prevTx) / elapsedSec).toLong()
                }
            }

            if (isAvailable) {
                lastUidRxMap[uid] = rxBytes
                lastUidTxMap[uid] = txBytes
            }

            val appLabel = try {
                packageManager.getApplicationLabel(primaryApp).toString()
            } catch (_: Exception) {
                packageName
            }

            val iconDrawable = try {
                packageManager.getApplicationIcon(primaryApp)
            } catch (_: Exception) {
                null
            }

            result.add(
                AppTrafficItem(
                    uid = uid,
                    packageName = packageName,
                    appName = if (appLabel.isNotBlank()) appLabel else packageName,
                    icon = iconDrawable,
                    rxBytes = rxBytes,
                    txBytes = txBytes,
                    totalBytes = if (rxBytes >= 0 && txBytes >= 0) rxBytes + txBytes else 0L,
                    currentRxSpeedBps = currentRxSpeed,
                    currentTxSpeedBps = currentTxSpeed,
                    isSystemApp = isSystem,
                    isAvailable = isAvailable
                )
            )
        }

        lastUidTimestamp = now
        return result
    }

    /**
     * Queries NetworkStatsManager for a specific UID across Mobile and Wi-Fi networks since 24 hours ago.
     */
    private fun queryNsmForUid(uid: Int): Pair<Long, Long>? {
        val nsm = networkStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (24 * 60 * 60 * 1000L) // Last 24 hours

        var totalRx = 0L
        var totalTx = 0L
        var foundAny = false

        // Query Wi-Fi
        try {
            val wifiStats = nsm.queryDetailsForUid(
                ConnectivityManager.TYPE_WIFI,
                null,
                startTime,
                endTime,
                uid
            )
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                totalRx += bucket.rxBytes
                totalTx += bucket.txBytes
                foundAny = true
            }
            wifiStats.close()
        } catch (_: Exception) {}

        // Query Mobile
        try {
            val mobileStats = nsm.queryDetailsForUid(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTime,
                endTime,
                uid
            )
            val bucket = NetworkStats.Bucket()
            while (mobileStats.hasNextBucket()) {
                mobileStats.getNextBucket(bucket)
                totalRx += bucket.rxBytes
                totalTx += bucket.txBytes
                foundAny = true
            }
            mobileStats.close()
        } catch (_: Exception) {}

        return if (foundAny) Pair(totalRx, totalTx) else null
    }
}
