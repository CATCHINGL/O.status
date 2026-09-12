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
    private var dndShiftPx = 0
    private var overlayAttached = false

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

        // Temporary DND avoidance only. Never changes the user's saved horizontal offset.
        val avoidancePx = if (dndOn) dndShiftPx else 0
        val targetX = baseMarginPx + avoidancePx
        if (lastDndOn != dndOn || lp.x != targetX) {
            lastDndOn = dndOn
            lp.x = targetX
            try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
        }
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
        if (!BootPrefs.isEnabled(this)) { stopSelf(); return }

        val nm=getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("duo5","Duo overlay",NotificationManager.IMPORTANCE_LOW))
        startForeground(5,Notification.Builder(this,"duo5").setContentTitle("O.status 1.0.0").setContentText("Active").setSmallIcon(android.R.drawable.ic_menu_info_details).build())

        val d=resources.displayMetrics.density
        val baseScale=.62f+pref.getInt("size5",24)/200f
        val scale=baseScale * 1.30f * (pref.getInt("duo_scale_v2_pct",100)/100f)
        val mode=pref.getString("color_mode","legacy") ?: "legacy"
        val resolvedMode = if (mode=="legacy") { if(pref.getBoolean("black5",false)) "black" else "white" } else mode
        v=DuoIndicatorView(this,resolvedMode)
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        baseMarginPx=((pref.getInt("margin5",7) + pref.getInt("duo_h_offset",0))*d).toInt()
        dndShiftPx=(30*d).toInt()
        lp=WindowManager.LayoutParams(
            (36*d*scale).toInt(), (36*d*scale).toInt(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity=Gravity.TOP or Gravity.END
            x=baseMarginPx; y=((pref.getInt("y5",0) + 7 + pref.getInt("duo_v_offset_v2",0))*d).toInt()
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        wm.addView(v,lp)
        overlayAttached = true
        registerReceiver(batteryRx,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val dndFilter = IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dndRx, dndFilter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(dndRx, dndFilter)
        updateAvoidance()
        dndHandler.removeCallbacks(dndFallback)
        dndHandler.post(dndFallback)
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
        if(::v.isInitialized)v.stop()
        if(::wm.isInitialized&&::v.isInitialized)try{wm.removeView(v); overlayAttached=false}catch(_:Exception){}
        super.onDestroy()
    }
    override fun onBind(i:Intent?):IBinder?=null
}
