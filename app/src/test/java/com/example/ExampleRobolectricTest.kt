package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NetWatch", appName)
  }

  @Test
  fun `formatBytes correctly handles units and negatives`() {
    assertEquals("0 B", com.example.data.model.FormatUtils.formatBytes(0L))
    assertEquals("1.0 KB", com.example.data.model.FormatUtils.formatBytes(1024L))
    assertEquals("1.0 MB", com.example.data.model.FormatUtils.formatBytes(1024L * 1024L))
    assertEquals("Unavailable in Lightweight Mode", com.example.data.model.FormatUtils.formatBytes(-1L))
  }

  @Test
  fun `formatSpeed correctly handles units and negatives`() {
    assertEquals("0 B/s", com.example.data.model.FormatUtils.formatSpeed(0L))
    assertEquals("1.0 KB/s", com.example.data.model.FormatUtils.formatSpeed(1024L))
    assertEquals("Unavailable in Lightweight Mode", com.example.data.model.FormatUtils.formatSpeed(-1L))
  }

  @Test
  fun `network stats helper initializes and checks usage access without crash`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val helper = com.example.monitoring.NetworkStatsHelper(context)
    val hasUsage = helper.hasUsageAccess()
    // Verify method safely evaluates permission without throwing an exception
    org.junit.Assert.assertTrue(hasUsage == true || hasUsage == false)
  }

  @Test
  fun `csv export handles records and file provider`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val repository = com.example.repository.TrafficRepository(context)
    val db = com.example.data.local.AppDatabase.getInstance(context)
    db.trafficRecordDao().insertRecord(
      com.example.data.local.TrafficRecord(
        timestamp = System.currentTimeMillis(),
        rxBytes = 1000L,
        txBytes = 500L,
        rxSpeedBps = 100L,
        txSpeedBps = 50L,
        networkType = "Wi-Fi"
      )
    )
    val intent = repository.exportHistoryCsv(context)
    org.junit.Assert.assertNotNull("Intent should not be null when records exist", intent)
  }

  @Test
  fun `viewmodel initializes without error`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.NetWatchViewModel(app)
    org.junit.Assert.assertNotNull(vm.dashboardState.value)
    org.junit.Assert.assertNotNull(vm.appsState.value)
    org.junit.Assert.assertNotNull(vm.historyState.value)
    org.junit.Assert.assertNotNull(vm.settingsState.value)
  }
}
