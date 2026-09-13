package com.catch7ng.ostatus

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class DetailsActivity : AppCompatActivity() {
    private val networkTrackers = mutableMapOf<Int, NetworkDisplayTracker>()
    private val speedHandler = Handler(Looper.getMainLooper())
    private var downloadSpeedValue: TextView? = null
    private var uploadSpeedValue: TextView? = null
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastSpeedSampleMs = 0L
    private val speedSampler = object : Runnable {
        override fun run() {
            updateNetworkSpeed()
            speedHandler.postDelayed(this, 1000L)
        }
    }
    private val d by lazy { resources.displayMetrics.density }
    private val dark get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val pageColor get() = if (dark) Color.BLACK else Color.rgb(242,242,247)
    private val cardColor get() = if (dark) Color.rgb(28,28,30) else Color.WHITE
    private val primary get() = if (dark) Color.WHITE else Color.BLACK
    private val secondary get() = if (dark) Color.rgb(142,142,147) else Color.rgb(99,99,102)
    private val separator get() = if (dark) Color.rgb(56,56,58) else Color.rgb(229,229,234)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageColor
        window.navigationBarColor = pageColor
        if (!dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refreshNetworkTrackers()
        buildUi()
        startNetworkSpeedSampling()
    }

    override fun onPause() {
        stopNetworkSpeedSampling()
        networkTrackers.values.forEach { it.stop() }
        networkTrackers.clear()
        super.onPause()
    }

    private fun refreshNetworkTrackers() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val sm = getSystemService(SubscriptionManager::class.java)
        val base = getSystemService(TelephonyManager::class.java)
        val subs = try { sm.activeSubscriptionInfoList ?: emptyList() } catch (_: Exception) { emptyList() }
        val activeIds = subs.map { it.subscriptionId }.toSet()
        networkTrackers.keys.filter { it !in activeIds }.forEach { id -> networkTrackers.remove(id)?.stop() }
        subs.forEach { sub ->
            if (!networkTrackers.containsKey(sub.subscriptionId)) {
                val tm = base.createForSubscriptionId(sub.subscriptionId)
                networkTrackers[sub.subscriptionId] = NetworkDisplayTracker(tm, mainExecutor) {
                    runOnUiThread { buildUi() }
                }.also { it.start() }
            }
        }
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(pageColor); isFillViewport = true }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(38), dp(20), dp(28)) }

        val back = BackChevronView(this).apply {
            setOnClickListener { finish() }
        }
        content.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            marginStart = -dp(12)
            bottomMargin = dp(2)
        })
        content.addView(TextView(this).apply {
            text = "Details"; textSize = 34f; setTextColor(primary); typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(dp(2), 0, 0, dp(4))
        })

        section(content, "BATTERY", listOf("Battery" to batterySummary()))
        section(content, "WI-FI", listOf("Wi-Fi" to wifiSummary()))
        section(content, "MOBILE", mobileRows())
        networkSpeedSection(content)

        scroll.addView(content)
        GeistTypography.apply(scroll)
        setContentView(scroll)
    }

    private fun batterySummary(): String {
        val i = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "Unavailable"
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0,100) else -1
        val parts = mutableListOf<String>()
        if (pct >= 0) parts += "$pct%"
        when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> parts += "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> parts += "Full"
        }
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (pm.isPowerSaveMode) parts += "Power Saving"
        return if (parts.isEmpty()) "Unavailable" else parts.joinToString("   ")
    }

    private fun wifiSummary(): String {
        val cm = getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return "Not Connected"
        val caps = cm.getNetworkCapabilities(network) ?: return "Not Connected"
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Not Connected"
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION") val info = wm.connectionInfo ?: return "Connected"
        val standard = if (Build.VERSION.SDK_INT >= 30) when (info.wifiStandard) {
            4 -> "Wi-Fi 4"
            5 -> "Wi-Fi 5"
            6 -> "Wi-Fi 6"
            7 -> "WiGig"
            8 -> "Wi-Fi 7"
            else -> "Wi-Fi"
        } else "Wi-Fi"
        val level = WifiManager.calculateSignalLevel(info.rssi, 5)
        val quality = when (level) { 4 -> "Excellent"; 3 -> "Good"; 2 -> "Fair"; else -> "Weak" }
        return "$standard   $quality"
    }

    private fun mobileRows(): List<Pair<String,String>> {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return listOf("Mobile" to "Phone permission required")
        }
        val sm = getSystemService(SubscriptionManager::class.java)
        val base = getSystemService(TelephonyManager::class.java)
        val defaultData = SubscriptionManager.getDefaultDataSubscriptionId()
        val subs = try { sm.activeSubscriptionInfoList ?: emptyList() } catch (_: Exception) { emptyList() }
        if (subs.isEmpty()) return listOf("Mobile" to "No active SIM")
        return subs.sortedBy { it.simSlotIndex }.map { sub ->
            val tm = base.createForSubscriptionId(sub.subscriptionId)
            val carrier = sub.carrierName?.toString()?.trim().orEmpty().ifEmpty { "SIM ${sub.simSlotIndex + 1}" }
            val isData = sub.subscriptionId == defaultData
            val title = if (isData) "SIM ${sub.simSlotIndex + 1}  DATA" else "SIM ${sub.simSlotIndex + 1}"
            val network = try { NetworkDisplayTracker.label(networkTrackers[sub.subscriptionId]?.displayInfo, tm.dataNetworkType) } catch (_: Exception) { "" }
            val level = try { tm.signalStrength?.level?.coerceIn(0,4) ?: 0 } catch (_: Exception) { 0 }
            val dots = (0 until 4).joinToString("\u2009") { if (it < level) "●" else "○" }
            val valueParts = mutableListOf(carrier)
            if (network.isNotEmpty()) valueParts += network
            valueParts += dots
            title to valueParts.joinToString("   ")
        }
    }



    private fun networkSpeedSection(parent: LinearLayout) {
        parent.addView(TextView(this).apply {
            text = "NETWORK SPEED"
            textSize = 12f
            setTextColor(secondary)
            setPadding(dp(16), dp(18), 0, dp(6))
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(cardColor); cornerRadius = dp(14).toFloat() }
            clipToOutline = true
        }
        card.addView(speedRow("Download", true))
        card.addView(separatorView())
        card.addView(speedRow("Upload", false))
        parent.addView(card)
    }

    private fun speedRow(title: String, download: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(9), dp(14), dp(9))
        minimumHeight = dp(54)
        addView(TextView(this@DetailsActivity).apply {
            text = title
            textSize = 16f
            setTextColor(primary)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, -1, 1f))
        val valueView = TextView(this@DetailsActivity).apply {
            text = "0 KB/s"
            textSize = 15f
            setTextColor(secondary)
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            maxLines = 1
        }
        addView(valueView)
        if (download) downloadSpeedValue = valueView else uploadSpeedValue = valueView
    }

    private fun startNetworkSpeedSampling() {
        speedHandler.removeCallbacks(speedSampler)
        lastRxBytes = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        lastTxBytes = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        lastSpeedSampleMs = SystemClock.elapsedRealtime()
        downloadSpeedValue?.text = "0 KB/s"
        uploadSpeedValue?.text = "0 KB/s"
        speedHandler.postDelayed(speedSampler, 1000L)
    }

    private fun stopNetworkSpeedSampling() {
        speedHandler.removeCallbacks(speedSampler)
        lastSpeedSampleMs = 0L
    }

    private fun updateNetworkSpeed() {
        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (lastSpeedSampleMs <= 0L || rx < 0L || tx < 0L) return
        val elapsedMs = (now - lastSpeedSampleMs).coerceAtLeast(1L)
        val rxPerSecond = ((rx - lastRxBytes).coerceAtLeast(0L) * 1000.0) / elapsedMs
        val txPerSecond = ((tx - lastTxBytes).coerceAtLeast(0L) * 1000.0) / elapsedMs
        lastRxBytes = rx
        lastTxBytes = tx
        lastSpeedSampleMs = now
        downloadSpeedValue?.text = formatSpeed(rxPerSecond)
        uploadSpeedValue?.text = formatSpeed(txPerSecond)
    }

    private fun formatSpeed(bytesPerSecond: Double): String = when {
        bytesPerSecond >= 1024.0 * 1024.0 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        bytesPerSecond >= 1024.0 -> String.format(java.util.Locale.US, "%.1f KB/s", bytesPerSecond / 1024.0)
        else -> "${bytesPerSecond.toLong()} B/s"
    }

    private fun isDndOn(): Boolean = try {
        when (getSystemService(NotificationManager::class.java).currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            NotificationManager.INTERRUPTION_FILTER_NONE,
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> true
            else -> false
        }
    } catch (_: Exception) { false }

    private fun section(parent: LinearLayout, title: String, rows: List<Pair<String,String>>) {
        parent.addView(TextView(this).apply { text = title; textSize = 12f; setTextColor(secondary); setPadding(dp(16), dp(18), 0, dp(6)) })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(cardColor); cornerRadius = dp(14).toFloat() }
            clipToOutline = true
        }
        rows.forEachIndexed { index, pair ->
            if (index > 0) card.addView(separatorView())
            card.addView(row(pair.first, pair.second))
        }
        parent.addView(card)
    }

    private fun row(title: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(9), dp(14), dp(9)); minimumHeight = dp(54)
        addView(TextView(this@DetailsActivity).apply { text = title; textSize = 16f; setTextColor(primary); gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(0,-1,1f))
        addView(TextView(this@DetailsActivity).apply { text = value; textSize = 15f; setTextColor(secondary); gravity = Gravity.CENTER_VERTICAL or Gravity.END; maxLines = 1 })
    }

    private fun separatorView() = View(this).apply { setBackgroundColor(separator); layoutParams = LinearLayout.LayoutParams(-1,1).apply { marginStart = dp(16) } }
    private fun dp(v: Int) = (v*d + .5f).toInt()
}
