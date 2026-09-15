package com.catch7ng.ostatus

import android.Manifest
import android.app.NotificationManager
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val d by lazy { resources.displayMetrics.density }
    private var overlayValue: TextView? = null
    private var phoneValue: TextView? = null
    private var dndValue: TextView? = null
    private var shizukuValue: TextView? = null
    private var colorValue: TextView? = null

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
        refreshPermissionStates()
        ensureOverlayStartedIfEnabled()
    }

    private fun ensureOverlayStartedIfEnabled() {
        // The main switch defaults to ON. If the user returns after granting
        // overlay permission, start the service immediately instead of
        // requiring an OFF -> ON toggle.
        if (!prefs.getBoolean("duo_enabled", true)) return
        if (!Settings.canDrawOverlays(this)) return
        BootPrefs.setEnabled(this, true)
        ContextCompat.startForegroundService(this, Intent(this, StatusBarService::class.java))
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(pageColor)
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(46), dp(20), dp(28))
        }

        content.addView(TextView(this).apply {
            text = "O.status"
            textSize = 34f
            setTextColor(primary)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(dp(2), 0, 0, dp(4))
        })
        sectionTitle(content, "O STATUS BAR")
        val mainCard = card()
        val onRow = row("O Status Bar")
        val enabledSwitch = OStatusSwitch(this).apply {
            isOn = prefs.getBoolean("duo_enabled", true)
            onToggleRequested = { requested ->
                if (requested && !Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    false
                } else {
                    prefs.edit().putBoolean("duo_enabled", requested).apply()
                    BootPrefs.setEnabled(this@MainActivity, requested)
                    if (requested) ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, StatusBarService::class.java))
                    else stopService(Intent(this@MainActivity, StatusBarService::class.java))
                    true
                }
            }
        }
        onRow.addView(enabledSwitch, LinearLayout.LayoutParams(dp(51), dp(31)))
        onRow.setOnClickListener { enabledSwitch.requestToggle() }
        mainCard.addView(onRow)
        content.addView(mainCard)

        sectionTitle(content, "APPEARANCE")
        val appearance = card()
        val colorRow = row("Indicator Colour")
        colorValue = colourChoiceLabel(colorName())
        colorRow.addView(colorValue)
        colorRow.setOnClickListener { cycleIndicatorColour() }
        appearance.addView(colorRow)
        appearance.addView(separatorView())
        val batteryPercentageRow = row("Battery Percentage")
        val batteryPercentageSwitch = OStatusSwitch(this).apply {
            isOn = prefs.getBoolean("battery_percentage", false)
            onToggleRequested = { requested ->
                prefs.edit().putBoolean("battery_percentage", requested).apply()
                BootPrefs.setBatteryPercentage(this@MainActivity, requested)
                if (prefs.getBoolean("duo_enabled", true) && Settings.canDrawOverlays(this@MainActivity)) {
                    ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, StatusBarService::class.java))
                }
                true
            }
        }
        batteryPercentageRow.addView(batteryPercentageSwitch, LinearLayout.LayoutParams(dp(51), dp(31)))
        batteryPercentageRow.setOnClickListener { batteryPercentageSwitch.requestToggle() }
        appearance.addView(batteryPercentageRow)
        appearance.addView(separatorView())
        val positionRow = row("Position & Size")
        positionRow.addView(ForwardChevronView(this), LinearLayout.LayoutParams(dp(18), dp(32)))
        positionRow.setOnClickListener { startActivity(Intent(this, PositionSizeActivity::class.java)) }
        appearance.addView(positionRow)
        content.addView(appearance)

        sectionTitle(content, "PERMISSIONS")
        val permissions = card()
        val overlayRow = permissionRow("Display Over Apps") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        overlayValue = overlayRow.second; permissions.addView(overlayRow.first)
        permissions.addView(separatorView())
        val phoneRow = permissionRow("Phone Status") { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), 10) }
        phoneValue = phoneRow.second; permissions.addView(phoneRow.first)
        permissions.addView(separatorView())
        val dndRow = permissionRow("Do Not Disturb Access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        dndValue = dndRow.second; permissions.addView(dndRow.first)
        permissions.addView(separatorView())
        val shizukuRow = permissionRow("Shizuku") { handleShizukuClick() }
        shizukuValue = shizukuRow.second; permissions.addView(shizukuRow.first)
        content.addView(permissions)

        sectionTitle(content, "OPTIONS")
        val options = card()
        val detailsRow = row("Details")
        detailsRow.addView(ForwardChevronView(this), LinearLayout.LayoutParams(dp(18), dp(32)))
        detailsRow.setOnClickListener { startActivity(Intent(this, DetailsActivity::class.java)) }
        options.addView(detailsRow)
        content.addView(options)

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(8))
        }
        fun footerText(value: String, clickable: Boolean = false) = TextView(this).apply {
            text = value
            textSize = 11.5f
            setTextColor(secondary)
            gravity = Gravity.CENTER
            if (clickable) {
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CATCHINGL")))
                }
            }
        }
        val footerMain = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        footerMain.addView(footerText("V${displayVersion()}"))
        footerMain.addView(footerText("  ·  "))
        footerMain.addView(footerText("GitHub", true))
        footerMain.addView(footerText("  ·  "))
        footerMain.addView(footerText("© 2026 CATCH7NG.L"))
        footer.addView(footerMain)
        footer.addView(footerText("Dynamic Auto requires Shizuku, a third-party app, to enable status bar colour detection.").apply {
            textSize = 10.5f
            setPadding(0, dp(4), 0, 0)
        })
        content.addView(footer)

        scroll.addView(content)
        scroll.viewTreeObserver.addOnGlobalLayoutListener {
            val child = scroll.getChildAt(0)
            val fits = child != null && child.height <= scroll.height
            scroll.setOnTouchListener(if (fits) View.OnTouchListener { _, _ -> true } else null)
        }
        GeistTypography.apply(scroll)
        setContentView(scroll)
        refreshPermissionStates()
    }

    private fun cycleIndicatorColour() {
        val current = prefs.getString("color_mode", "auto") ?: "auto"
        val next = when (current) {
            "auto" -> "black"
            "black" -> "white"
            else -> "auto"
        }
        prefs.edit().putString("color_mode", next).apply()
        colorValue?.text = when (next) {
            "black" -> "Black"
            "white" -> "White"
            else -> "Auto"
        }
        if (prefs.getBoolean("duo_enabled", true) && Settings.canDrawOverlays(this)) {
            // Ask the existing service to refresh its colour lifecycle in-place.
            // Avoid stop/start races when switching Black/White back to Auto.
            ContextCompat.startForegroundService(this, Intent(this, StatusBarService::class.java))
        }
    }

    private fun refreshPermissionStates() {
        overlayValue?.let { setPermission(it, Settings.canDrawOverlays(this)) }
        phoneValue?.let { setPermission(it, ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) }
        dndValue?.let { setPermission(it, getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) }
        shizukuValue?.let {
            it.text = ShizukuAppearanceManager.status(this)
            it.setTextColor(if (ShizukuAppearanceManager.isAuthorized()) secondary else primary)
        }
    }

    private fun handleShizukuClick() {
        when {
            !ShizukuAppearanceManager.isInstalled(this) -> {
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
                try { startActivity(market) } catch (_: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
                }
            }
            !ShizukuAppearanceManager.isRunning() -> packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let(::startActivity)
            !ShizukuAppearanceManager.isAuthorized() -> Shizuku.requestPermission(1001)
            else -> packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let(::startActivity)
        }
    }

    private fun setPermission(v: TextView, granted: Boolean) {
        v.text = if (granted) "Allowed" else "Required  ›"
        v.setTextColor(if (granted) secondary else primary)
    }

    private inner class OStatusSwitch(context: android.content.Context) : View(context) {
        var isOn: Boolean = false
            set(value) {
                field = value
                progress = if (value) 1f else 0f
                invalidate()
            }

        var onToggleRequested: ((Boolean) -> Boolean)? = null
        private var progress = 0f
        private var animator: ValueAnimator? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            isClickable = true
            isFocusable = true
            contentDescription = "O Status Bar"
            setOnClickListener { requestToggle() }
        }

        fun requestToggle() {
            val requested = !isOn
            val accepted = onToggleRequested?.invoke(requested) ?: true
            if (accepted) setOnAnimated(requested)
        }

        private fun setOnAnimated(value: Boolean) {
            if (isOn == value) return
            val start = progress
            isOn = value
            progress = start
            val target = if (value) 1f else 0f
            animator?.cancel()
            animator = ValueAnimator.ofFloat(start, target).apply {
                duration = 180L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            // Monochrome O.status switch: neutral OFF, black/white ON depending theme.
            val offTrack = if (dark) Color.rgb(58,58,60) else Color.rgb(229,229,234)
            val onTrack = if (dark) Color.WHITE else Color.BLACK
            paint.color = blend(offTrack, onTrack, progress)
            canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, paint)

            val pad = dp(2).toFloat()
            val radius = (h - pad * 2f) / 2f
            val leftCx = pad + radius
            val rightCx = w - pad - radius
            val cx = leftCx + (rightCx - leftCx) * progress

            paint.color = if (dark && progress > .5f) Color.BLACK else Color.WHITE
            paint.setShadowLayer(dp(1).toFloat(), 0f, dp(1).toFloat(), 0x33000000)
            setLayerType(LAYER_TYPE_SOFTWARE, paint)
            canvas.drawCircle(cx, h / 2f, radius, paint)
            paint.clearShadowLayer()
        }

        private fun blend(a: Int, b: Int, t: Float): Int {
            val clamped = t.coerceIn(0f, 1f)
            return Color.rgb(
                (Color.red(a) + (Color.red(b) - Color.red(a)) * clamped).toInt(),
                (Color.green(a) + (Color.green(b) - Color.green(a)) * clamped).toInt(),
                (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * clamped).toInt()
            )
        }
    }

    private fun displayVersion(): String {
        val raw = packageManager.getPackageInfo(packageName, 0).versionName ?: "2.0.0"
        return raw
    }

    private fun colorName() = when (prefs.getString("color_mode", "auto")) { "black" -> "Black"; "white" -> "White"; else -> "Auto" }

    private fun sectionTitle(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = title; textSize = 12f; setTextColor(secondary)
            setPadding(dp(16), dp(18), 0, dp(6))
        })
    }

    private fun card(radius: Float = 14f) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { setColor(cardColor); cornerRadius = dp(radius.toInt()).toFloat() }
        clipToOutline = true
    }

    private fun row(title: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(9), dp(14), dp(9)); minimumHeight = dp(54)
            addView(TextView(this@MainActivity).apply {
                text = title; textSize = 16f; setTextColor(primary); gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun permissionRow(title: String, click: () -> Unit): Pair<LinearLayout, TextView> {
        val r = row(title)
        val v = valueLabel("")
        r.addView(v); r.setOnClickListener { click() }
        return Pair(r, v)
    }

    private fun valueLabel(value: String) = TextView(this).apply {
        text = value; textSize = 15f; setTextColor(secondary); gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, 0, 0)
    }

    private fun colourChoiceLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(12), 0, dp(12), 0)
        minWidth = dp(64)
        minimumHeight = dp(32)
        background = GradientDrawable().apply {
            setColor(if (dark) Color.rgb(58,58,60) else Color.rgb(44,44,46))
            cornerRadius = dp(9).toFloat()
        }
    }

    private fun separatorView() = View(this).apply {
        setBackgroundColor(separator)
        layoutParams = LinearLayout.LayoutParams(-1, 1).apply { marginStart = dp(16) }
    }

    private fun dp(v: Int) = (v * d + 0.5f).toInt()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); refreshPermissionStates()
    }
}
