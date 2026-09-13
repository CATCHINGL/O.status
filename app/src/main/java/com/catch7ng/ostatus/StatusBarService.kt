package com.catch7ng.ostatus

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*

class StatusBarService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var v: DuoIndicatorView
    private lateinit var lp: WindowManager.LayoutParams
    private var baseMarginPx = 0
    private var baseYPx = 0
    private var dndShiftPx = 0
    private var overlayAttached = false
    private var isLeftSide = false
    private var autoHideInFullScreen = true
    private var hiddenForFullScreen = false

    private val fullScreenHandler = Handler(Looper.getMainLooper())
    private val fullScreenRunnable = object : Runnable {
        override fun run() {
            updateFullScreenVisibility()
            fullScreenHandler.postDelayed(this, 350L)
        }
    }

    // Burn-in protection: move the overlay by a tiny, temporary offset over time.
    // User-saved Position & Size values are never modified.
    private val pixelShiftHandler = Handler(Looper.getMainLooper())
    private val pixelShiftPattern = arrayOf(
        0 to 0, 1 to 0, 1 to 1, 0 to 1,
        -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1
    )
    private var pixelShiftIndex = 0
    private var pixelShiftXPx = 0
    private var pixelShiftYPx = 0
    private val pixelShiftRunnable = object : Runnable {
        override fun run() {
            if (!::lp.isInitialized) return
            pixelShiftIndex = (pixelShiftIndex + 1) % pixelShiftPattern.size
            val (dx, dy) = pixelShiftPattern[pixelShiftIndex]
            val density = resources.displayMetrics.density
            pixelShiftXPx = (dx * density).toInt()
            pixelShiftYPx = (dy * density).toInt()
            applyTemporaryPosition()
            pixelShiftHandler.postDelayed(this, 60_000L)
        }
    }

    private fun displaySizePx(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            Pair(b.width(), b.height())
        } else {
            @Suppress("DEPRECATION")
            val dm = resources.displayMetrics
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun clampUserPosition(density: Float) {
        val (screenW, screenH) = displaySizePx()
        val overlayW = lp.width.coerceAtLeast(1)
        val overlayH = lp.height.coerceAtLeast(1)

        // x in START/END gravity is an inward margin. Keep the complete overlay visible.
        val maxX = (screenW - overlayW).coerceAtLeast(0)
        val requestedX = ((getSharedPreferences("settings", MODE_PRIVATE)
            .getInt("margin5", 7) + getSharedPreferences("settings", MODE_PRIVATE)
            .getInt("duo_h_offset", 0)) * density).toInt()
        baseMarginPx = requestedX.coerceIn(0, maxX)

        // y uses TOP gravity. Preserve the legacy +7 baseline, but never allow clipping.
        val pref = getSharedPreferences("settings", MODE_PRIVATE)
        val requestedY = ((pref.getInt("y5", 0) + 7 + pref.getInt("duo_v_offset_v2", 0)) * density).toInt()
        baseYPx = requestedY.coerceIn(0, (screenH - overlayH).coerceAtLeast(0))
        lp.y = baseYPx
        lp.x = baseMarginPx
    }

    private fun ensureOverlayAttached() {
        if (!BootPrefs.isEnabled(this) || !Settings.canDrawOverlays(this)) return
        if (!::wm.isInitialized || !::v.isInitialized || !::lp.isInitialized) return
        if (!overlayAttached || !v.isAttachedToWindow) {
            try {
                if (v.parent != null) try { wm.removeViewImmediate(v) } catch (_: Exception) {}
                wm.addView(v, lp)
                overlayAttached = true
            } catch (_: Exception) { }
        } else {
            try { wm.updateViewLayout(v, lp) } catch (_: Exception) { }
        }
        updateAvoidance()
        v.invalidate()
    }

    private val dndRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            updateAvoidance()
        }
    }

    private val dndHandler = Handler(Looper.getMainLooper())
    private var lastDndOn: Boolean? = null
    private val dndFallback = object : Runnable {
        override fun run() {
            updateAvoidance()
            dndHandler.postDelayed(this, 500L)
        }
    }

    private fun updateAvoidance() {
        if (!::wm.isInitialized || !::v.isInitialized || !::lp.isInitialized) return
        val nm = getSystemService(NotificationManager::class.java)
        val dndOn = try {
            when (nm.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                NotificationManager.INTERRUPTION_FILTER_NONE,
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> true
                else -> false
            }
        } catch (_: Exception) { false }

        if (lastDndOn != dndOn) lastDndOn = dndOn
        applyTemporaryPosition()
    }

    private fun applyTemporaryPosition() {
        if (!::wm.isInitialized || !::v.isInitialized || !::lp.isInitialized) return
        val (screenW, screenH) = displaySizePx()
        val maxX = (screenW - lp.width).coerceAtLeast(0)
        val maxY = (screenH - lp.height).coerceAtLeast(0)

        // Right-side DND avoidance remains exactly as before. Pixel shifting is layered
        // on top and clamped so the overlay can never move outside the current display.
        val avoidancePx = if (lastDndOn == true && !isLeftSide) dndShiftPx else 0
        val targetX = (baseMarginPx + avoidancePx + pixelShiftXPx).coerceIn(0, maxX)
        val targetY = (baseYPx + pixelShiftYPx).coerceIn(0, maxY)
        if (lp.x != targetX || lp.y != targetY) {
            lp.x = targetX
            lp.y = targetY
            try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
        }
    }

    private fun startPixelShifting() {
        pixelShiftHandler.removeCallbacks(pixelShiftRunnable)
        pixelShiftIndex = 0
        pixelShiftXPx = 0
        pixelShiftYPx = 0
        applyTemporaryPosition()
        pixelShiftHandler.postDelayed(pixelShiftRunnable, 60_000L)
    }


    private fun updateFullScreenVisibility() {
        if (!::v.isInitialized || !v.isAttachedToWindow) return
        if (!autoHideInFullScreen) {
            if (hiddenForFullScreen) {
                hiddenForFullScreen = false
                v.alpha = 1f
                applyTemporaryPosition()
            }
            return
        }

        val insets = v.rootWindowInsets ?: return
        val statusBarVisible = if (Build.VERSION.SDK_INT >= 30) {
            insets.isVisible(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            (v.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0
        }
        val shouldHide = !statusBarVisible
        if (shouldHide != hiddenForFullScreen) {
            hiddenForFullScreen = shouldHide
            if (shouldHide) {
                // Do not make the overlay root invisible: once invisible, its own
                // WindowInsets can stop updating and we may never observe fullscreen exit.
                v.alpha = 0f
            } else {
                v.alpha = 1f
                applyTemporaryPosition()
            }
        }
    }

    private fun startFullScreenWatcher() {
        fullScreenHandler.removeCallbacks(fullScreenRunnable)
        hiddenForFullScreen = false
        if (::v.isInitialized) v.alpha = 1f
        fullScreenHandler.post(fullScreenRunnable)
    }


    private val batteryRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (!::v.isInitialized) return
            val level=i?.getIntExtra(BatteryManager.EXTRA_LEVEL,0)?:0
            val scale=i?.getIntExtra(BatteryManager.EXTRA_SCALE,100)?:100
            val st=i?.getIntExtra(BatteryManager.EXTRA_STATUS,-1)?:-1
            v.batteryPercent=if(scale>0)(level*100/scale).coerceIn(0,100) else 0
            v.charging=st==BatteryManager.BATTERY_STATUS_CHARGING||st==BatteryManager.BATTERY_STATUS_FULL
            v.invalidate()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val pref=getSharedPreferences("settings",MODE_PRIVATE)
        autoHideInFullScreen = true
        if (!BootPrefs.isEnabled(this)) { stopSelf(); return }

        val nm=getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("duo5","Duo overlay",NotificationManager.IMPORTANCE_LOW))
        startForeground(5,Notification.Builder(this,"duo5").setContentTitle("O.status").setContentText("Active").setSmallIcon(android.R.drawable.ic_menu_info_details).build())

        val d=resources.displayMetrics.density
        val baseScale=.62f+pref.getInt("size5",24)/200f
        val scale=baseScale * 1.56f * (pref.getInt("duo_scale_v3_pct",100)/100f)
        val mode=pref.getString("color_mode","legacy") ?: "legacy"
        val resolvedMode = if (mode=="legacy") { if(pref.getBoolean("black5",false)) "black" else "white" } else mode
        v=DuoIndicatorView(this, resolvedMode, pref.getBoolean("battery_percentage", BootPrefs.batteryPercentage(this)))
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        baseMarginPx=((pref.getInt("margin5",7) + pref.getInt("duo_h_offset",0))*d).toInt()
        isLeftSide = pref.getString("duo_side","right") == "left"
        dndShiftPx=(30*d).toInt()
        lp=WindowManager.LayoutParams(
            (36*d*scale).toInt(), (36*d*scale).toInt(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity=Gravity.TOP or if (isLeftSide) Gravity.START else Gravity.END
            x=baseMarginPx; y=((pref.getInt("y5",0) + 7 + pref.getInt("duo_v_offset_v2",0))*d).toInt()
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        clampUserPosition(d)
        wm.addView(v,lp)
        overlayAttached = true
        registerReceiver(batteryRx,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val dndFilter = IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dndRx, dndFilter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(dndRx, dndFilter)
        updateAvoidance()
        dndHandler.removeCallbacks(dndFallback)
        dndHandler.post(dndFallback)
        startPixelShifting()
        startFullScreenWatcher()
        v.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureOverlayAttached()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Removing the settings Activity from Recents is not the same as turning
        // O Status Bar off. Most Android builds keep this foreground service alive.
        // Some OEMs tear the process down with the task, so arm one short, one-shot
        // recovery check. Force Stop / Android's Active apps Stop still wins because
        // the OS cancels pending work for a force-stopped package.
        if (BootPrefs.isEnabled(this) && Settings.canDrawOverlays(this)) {
            TaskRecoveryReceiver.schedule(this)
        }
        super.onTaskRemoved(rootIntent)
    }
    override fun onDestroy() {
        try{unregisterReceiver(batteryRx)}catch(_:Exception){}
        try{unregisterReceiver(dndRx)}catch(_:Exception){}
        dndHandler.removeCallbacks(dndFallback)
        pixelShiftHandler.removeCallbacks(pixelShiftRunnable)
        fullScreenHandler.removeCallbacks(fullScreenRunnable)
        if(::v.isInitialized)v.stop()
        if(::wm.isInitialized&&::v.isInitialized)try{wm.removeView(v); overlayAttached=false}catch(_:Exception){}
        super.onDestroy()
    }
    override fun onBind(i:Intent?):IBinder?=null
}
