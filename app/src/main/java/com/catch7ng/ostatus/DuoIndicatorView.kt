package com.catch7ng.ostatus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

class DuoIndicatorView(
    ctx: Context,
    private var colorMode: String,
    private var batteryPercentageEnabled: Boolean = false
) : View(ctx) {

    var batteryPercent = 0
    var charging = false
    private var powerSaveMode = false
    private var wifiConnected = false
    private var wifiLevel = 0
    private var cellularLevel = 0
    private var cellularNetworkLabel = ""
    private var mobileDataConnected = false
    private var airplaneMode = false
    private var trackedSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    private var networkDisplayTracker: NetworkDisplayTracker? = null
    private var shizukuAutoBlack: Boolean? = null

    fun setShizukuAutoBlack(value: Boolean?) {
        if (shizukuAutoBlack == value) return
        shizukuAutoBlack = value
        invalidate()
    }

    fun setBatteryPercentageEnabled(value: Boolean) {
        if (batteryPercentageEnabled == value) return
        batteryPercentageEnabled = value
        invalidate()
    }

    fun setColorMode(value: String) {
        if (colorMode == value) return
        colorMode = value
        shizukuAutoBlack = null
        invalidate()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            refreshWifi()
            refreshCellular()
            refreshSystemStates()
            invalidate()
            handler.postDelayed(this, 1200)
        }
    }

    fun start() { handler.post(poll) }
    fun stop() {
        handler.removeCallbacks(poll)
        networkDisplayTracker?.stop()
        networkDisplayTracker = null
        trackedSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    private fun isBlackIndicator(): Boolean = when (colorMode) {
        "black" -> true
        "white" -> false
        else -> shizukuAutoBlack ?: ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES)
    }

    private fun refreshSystemStates() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerSaveMode = pm.isPowerSaveMode
        airplaneMode = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (_: Exception) { false }
    }

    private fun refreshWifi() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        mobileDataConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        if (wifiConnected) {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION") val rssi = wm.connectionInfo.rssi
            wifiLevel = WifiManager.calculateSignalLevel(rssi, 4).coerceIn(0, 3)
        } else wifiLevel = 0
    }

    private fun refreshCellular() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            cellularLevel = 0
            cellularNetworkLabel = ""
            return
        }
        try {
            val base = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
            val tm = if (SubscriptionManager.isValidSubscriptionId(defaultDataSubId)) base.createForSubscriptionId(defaultDataSubId) else base
            if (trackedSubId != defaultDataSubId) {
                networkDisplayTracker?.stop()
                trackedSubId = defaultDataSubId
                networkDisplayTracker = NetworkDisplayTracker(tm, context.mainExecutor) {
                    handler.post { refreshCellular(); invalidate() }
                }.also { it.start() }
            }

            // Keep aggregate Default Data SIM signal level.
            @Suppress("DEPRECATION") val signal = tm.signalStrength
            cellularLevel = signal?.level?.coerceIn(0, 4) ?: 0

            @Suppress("DEPRECATION") val fallbackType = tm.dataNetworkType
            cellularNetworkLabel = NetworkDisplayTracker.label(networkDisplayTracker?.displayInfo, fallbackType).let {
                if (it == "4G" || it == "5G") it else ""
            }
        } catch (_: Exception) {
            cellularLevel = 0
            cellularNetworkLabel = ""
        }
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val black = isBlackIndicator()
        val active = if (black) Color.BLACK else Color.WHITE
        val inactive = if (black) 0x38000000 else 0x55FFFFFF

        val s = min(width, height) / 436f
        fun X(v: Float) = width / 2f + (v - 237f) * s
        fun Y(v: Float) = height / 2f + (v - 211f) * s

        val cx = X(237f); val cy = Y(211f); val r = 163f * s
        val rect = RectF(cx-r, cy-r, cx+r, cy+r)

        val batteryProgressColor = when {
            batteryPercent >= 100 -> Color.rgb(52,199,89)
            charging -> Color.rgb(52,199,89)
            powerSaveMode -> Color.rgb(255,204,0)
            batteryPercent <= 20 -> Color.rgb(255,59,48)
            else -> active
        }
        val showBatteryPercentage = batteryPercentageEnabled || batteryPercent <= 20

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 26f * s
        paint.strokeCap = Paint.Cap.ROUND

        if (!showBatteryPercentage) {
            val start = 151.5f
            val sweep = 237f
            paint.color = inactive
            c.drawArc(rect, start, sweep, false, paint)
            paint.color = batteryProgressColor
            c.drawArc(rect, start, sweep * (batteryPercent.coerceIn(0,100)/100f), false, paint)
        } else {
            // Percentage mode is derived from the ORIGINAL locked C-ring.
            // Same centre, radius, stroke and LOWER ENDPOINTS. Only the top
            // portion is opened to make room for the battery number.
            val originalStart = 151.5f
            val originalEnd = 151.5f + 237f

            // Top opening boundaries. These alter only the upper portion of the
            // original track; the lower endpoints remain exactly at 151.5°/388.5°.
            val isFullBattery = batteryPercent >= 100
            val leftTopEnd = if (isFullBattery) 231f else 236f
            val rightTopStart = if (isFullBattery) 309f else 304f
            val leftSweep = leftTopEnd - originalStart
            val rightSweep = originalEnd - rightTopStart

            paint.color = inactive
            c.drawArc(rect, originalStart, leftSweep, false, paint)
            c.drawArc(rect, rightTopStart, rightSweep, false, paint)

            // Preserve the original battery-progress direction and total 237°
            // scale. The hidden top gap simply clips the part that would pass
            // behind the number.
            val progressEnd = originalStart +
                237f * (batteryPercent.coerceIn(0,100) / 100f)
            paint.color = batteryProgressColor

            val leftProgressEnd = minOf(progressEnd, leftTopEnd)
            if (leftProgressEnd > originalStart) {
                c.drawArc(rect, originalStart, leftProgressEnd-originalStart, false, paint)
            }
            if (progressEnd > rightTopStart) {
                val rightProgressEnd = minOf(progressEnd, originalEnd)
                c.drawArc(rect, rightTopStart, rightProgressEnd-rightTopStart, false, paint)
            }

            // Keep the user-approved number scale from the previous preview.
            paint.style = Paint.Style.FILL
            paint.color = active
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 92f * s
            val fmBattery = paint.fontMetrics
            val batteryTextY = Y(60f) - (fmBattery.ascent + fmBattery.descent)/2f
            c.drawText(batteryPercent.coerceIn(0,100).toString(), X(237f), batteryTextY, paint)
        }

        // Locked cellular-dot geometry.
        val dots = arrayOf(Pair(143f,342f), Pair(202f,372f), Pair(272f,372f), Pair(331f,342f))
        val shownCellularLevel = if (airplaneMode) 0 else cellularLevel
        paint.style = Paint.Style.FILL
        dots.forEachIndexed { index, (dx,dy) ->
            paint.color = if (index < shownCellularLevel) active else inactive
            c.drawCircle(X(dx), Y(dy), 18f*s, paint)
        }

        if (wifiConnected) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 21f*s; paint.strokeCap = Paint.Cap.ROUND
            Path().also { path ->
                path.moveTo(X(171f),Y(194f)); path.cubicTo(X(205f),Y(160f),X(269f),Y(160f),X(304f),Y(194f))
                paint.color = if (wifiLevel >= 2) active else inactive; c.drawPath(path,paint)
            }
            Path().also { path ->
                path.moveTo(X(199f),Y(224f)); path.cubicTo(X(220f),Y(203f),X(256f),Y(203f),X(277f),Y(224f))
                paint.color = if (wifiLevel >= 1) active else inactive; c.drawPath(path,paint)
            }
            val terminal = Path().apply {
                moveTo(X(237f),Y(238f)); cubicTo(X(223f),Y(238f),X(215f),Y(247f),X(215f),Y(255f))
                cubicTo(X(215f),Y(262f),X(226f),Y(273f),X(233f),Y(279f)); cubicTo(X(236f),Y(282f),X(239f),Y(282f),X(242f),Y(279f))
                cubicTo(X(249f),Y(273f),X(260f),Y(262f),X(260f),Y(255f)); cubicTo(X(260f),Y(247f),X(251f),Y(238f),X(237f),Y(238f)); close()
            }
            paint.style = Paint.Style.FILL; paint.color = active; c.drawPath(terminal,paint)
        } else if (airplaneMode) {
            drawAirplane(c, s, active, ::X) { value -> Y(value - 18f) }
        } else if (mobileDataConnected && cellularNetworkLabel.isNotEmpty()) {
            paint.style = Paint.Style.FILL; paint.color = active
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); paint.textAlign = Paint.Align.CENTER; paint.textSize = 128f*s
            val fm = paint.fontMetrics; val textY = Y(214f) - (fm.ascent + fm.descent)/2f
            c.drawText(cellularNetworkLabel, X(237f), textY, paint)
        }
    }

    private fun drawAirplane(c: Canvas, s: Float, color: Int, X: (Float)->Float, Y: (Float)->Float) {
        // Solid, rounded nose-up silhouette based on the supplied reference.
        val p = Path().apply {
            moveTo(X(237f), Y(143f))
            cubicTo(X(230f),Y(143f),X(226f),Y(151f),X(225f),Y(163f))
            lineTo(X(221f),Y(205f))
            lineTo(X(165f),Y(239f)); cubicTo(X(157f),Y(244f),X(154f),Y(251f),X(156f),Y(257f))
            cubicTo(X(158f),Y(263f),X(166f),Y(264f),X(174f),Y(261f)); lineTo(X(221f),Y(244f))
            lineTo(X(220f),Y(278f)); lineTo(X(196f),Y(297f)); cubicTo(X(191f),Y(301f),X(190f),Y(307f),X(193f),Y(311f))
            cubicTo(X(196f),Y(315f),X(202f),Y(314f),X(207f),Y(312f)); lineTo(X(237f),Y(300f))
            lineTo(X(267f),Y(312f)); cubicTo(X(272f),Y(314f),X(278f),Y(315f),X(281f),Y(311f))
            cubicTo(X(284f),Y(307f),X(283f),Y(301f),X(278f),Y(297f)); lineTo(X(254f),Y(278f))
            lineTo(X(253f),Y(244f)); lineTo(X(300f),Y(261f)); cubicTo(X(308f),Y(264f),X(316f),Y(263f),X(318f),Y(257f))
            cubicTo(X(320f),Y(251f),X(317f),Y(244f),X(309f),Y(239f)); lineTo(X(253f),Y(205f)); lineTo(X(249f),Y(163f))
            cubicTo(X(248f),Y(151f),X(244f),Y(143f),X(237f),Y(143f)); close()
        }
        paint.style = Paint.Style.FILL; paint.color = color
        c.drawPath(p, paint)
    }
}
