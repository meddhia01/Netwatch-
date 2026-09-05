package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.TrafficRecord
import com.example.data.model.AppTrafficItem
import com.example.data.model.SortOption
import com.example.data.model.TrafficSnapshot
import com.example.repository.TrafficRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DashboardUiState(
    val snapshot: TrafficSnapshot,
    val isMonitoring: Boolean = true,
    val refreshIntervalSec: Long = 2L,
    val hasUsageAccess: Boolean = false,
    val monitoringMode: String = "Lightweight Monitoring"
)

data class AppsUiState(
    val apps: List<AppTrafficItem> = emptyList(),
    val filteredApps: List<AppTrafficItem> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.TOTAL_TRAFFIC,
    val hasUsageAccess: Boolean = false
)

data class HistoryUiState(
    val records: List<TrafficRecord> = emptyList(),
    val recordCount: Int = 0,
    val isExporting: Boolean = false,
    val exportSuccessMessage: String? = null,
    val errorMessage: String? = null
)

data class SettingsUiState(
    val refreshIntervalSec: Long = 2L,
    val hasUsageAccess: Boolean = false,
    val monitoringMode: String = "Lightweight Monitoring",
    val deepMonitoringInfo: String = "Deep Monitoring is currently unavailable. Lightweight Monitoring does not use VPN or packet interception."
)

class NetWatchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TrafficRepository(application.applicationContext)

    // Dashboard State
    val dashboardState: StateFlow<DashboardUiState> = combine(
        repository.currentSnapshot,
        repository.isMonitoringActive,
        repository.refreshIntervalSec
    ) { snapshot, isMonitoring, interval ->
        DashboardUiState(
            snapshot = snapshot,
            isMonitoring = isMonitoring,
            refreshIntervalSec = interval,
            hasUsageAccess = repository.hasUsageAccess(),
            monitoringMode = "Lightweight Monitoring"
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(snapshot = repository.currentSnapshot.value)
    )

    // Apps State
    private val _appsState = MutableStateFlow(AppsUiState(isLoading = true))
    val appsState: StateFlow<AppsUiState> = _appsState.asStateFlow()

    // History State
    private val _historyExportState = MutableStateFlow<String?>(null)
    val historyState: StateFlow<HistoryUiState> = combine(
        repository.historyRecords,
        repository.recordCount,
        _historyExportState
    ) { records, count, exportMsg ->
        HistoryUiState(
            records = records,
            recordCount = count,
            exportSuccessMessage = exportMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    // Settings State
    val settingsState: StateFlow<SettingsUiState> = combine(
        repository.refreshIntervalSec,
        repository.isMonitoringActive
    ) { interval, _ ->
        SettingsUiState(
            refreshIntervalSec = interval,
            hasUsageAccess = repository.hasUsageAccess()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    private var perAppPollJob: Job? = null

    init {
        repository.startMonitoring(viewModelScope)
        startPerAppPolling()
    }

    private fun startPerAppPolling() {
        perAppPollJob?.cancel()
        perAppPollJob = viewModelScope.launch {
            // Initial load with fresh NSM data
            refreshAppsList(forceNsm = true)
            while (isActive) {
                // Poll at relaxed interval (defaulting to 10s) using lightweight TrafficStats deltas
                delay(10_000L)
                refreshAppsList(forceNsm = false)
            }
        }
    }

    fun refreshAppsList(forceNsm: Boolean = false) {
        viewModelScope.launch {
            val hasUsage = repository.hasUsageAccess()
            val apps = repository.getPerAppTraffic(forceNsmRefresh = forceNsm)
            val query = _appsState.value.searchQuery
            val sort = _appsState.value.sortOption
            val filtered = filterAndSortApps(apps, query, sort)

            _appsState.value = _appsState.value.copy(
                apps = apps,
                filteredApps = filtered,
                isLoading = false,
                hasUsageAccess = hasUsage
            )
        }
    }

    fun setSearchQuery(query: String) {
        val current = _appsState.value
        val filtered = filterAndSortApps(current.apps, query, current.sortOption)
        _appsState.value = current.copy(
            searchQuery = query,
            filteredApps = filtered
        )
    }

    fun setSortOption(sort: SortOption) {
        val current = _appsState.value
        val filtered = filterAndSortApps(current.apps, current.searchQuery, sort)
        _appsState.value = current.copy(
            sortOption = sort,
            filteredApps = filtered
        )
    }

    private fun filterAndSortApps(
        apps: List<AppTrafficItem>,
        query: String,
        sort: SortOption
    ): List<AppTrafficItem> {
        val filtered = if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }

        return when (sort) {
            SortOption.TOTAL_TRAFFIC -> filtered.sortedByDescending { it.totalBytes }
            SortOption.DOWNLOAD -> filtered.sortedByDescending { it.rxBytes }
            SortOption.UPLOAD -> filtered.sortedByDescending { it.txBytes }
            SortOption.APP_NAME -> filtered.sortedBy { it.appName.lowercase() }
        }
    }

    fun setRefreshInterval(seconds: Long) {
        repository.setRefreshInterval(seconds)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun exportHistoryCsv(context: Context, onChooserReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val intent = repository.exportHistoryCsv(context)
            if (intent != null) {
                onChooserReady(intent)
            }
        }
    }

    fun checkUsageAccess() {
        val hasUsage = repository.hasUsageAccess()
        _appsState.value = _appsState.value.copy(hasUsageAccess = hasUsage)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopMonitoring()
    }
}
