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

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val d by lazy { resources.displayMetrics.density }
    private var overlayValue: TextView? = null
    private var phoneValue: TextView? = null
    private var dndValue: TextView? = null
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

    override fun onResume() { super.onResume(); refreshPermissionStates() }

    private fun buildUi() {
        val scroll = object : ScrollView(this) {
            private fun hasScrollableContent(): Boolean =
                canScrollVertically(-1) || canScrollVertically(1)

            override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
                if (!hasScrollableContent()) return false
                return super.onInterceptTouchEvent(ev)
            }

            override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
                if (!hasScrollableContent()) return false
                return super.onTouchEvent(ev)
            }
        }.apply {
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
                    stopService(Intent(this@MainActivity, StatusBarService::class.java))
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
        content.addView(permissions)

        sectionTitle(content, "OPTIONS")
        val options = card()
        val detailsRow = row("Details")
        detailsRow.addView(ForwardChevronView(this), LinearLayout.LayoutParams(dp(18), dp(32)))
        detailsRow.setOnClickListener { startActivity(Intent(this, DetailsActivity::class.java)) }
        options.addView(detailsRow)
        content.addView(options)

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
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
        footer.addView(footerText("V${displayVersion()}"))
        footer.addView(footerText("  ·  "))
        footer.addView(footerText("GitHub", true))
        footer.addView(footerText("  ·  "))
        footer.addView(footerText("© 2026 CATCH7NG.L"))
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
            stopService(Intent(this, StatusBarService::class.java))
            ContextCompat.startForegroundService(this, Intent(this, StatusBarService::class.java))
        }
    }

    private fun refreshPermissionStates() {
        overlayValue?.let { setPermission(it, Settings.canDrawOverlays(this)) }
        phoneValue?.let { setPermission(it, ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) }
        dndValue?.let { setPermission(it, getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) }
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
        val raw = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.3.0"
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

private class OStatusPreview(context: android.content.Context) : View(context) {
    var mode: String = "auto"
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val black = when(mode) { "black" -> true; "white" -> false; else -> !dark }
        val active = if (black) Color.BLACK else Color.WHITE
        val inactive = if (black) Color.rgb(210,210,215) else Color.rgb(70,70,73)
        val cx = width/2f; val cy = height/2f - 2f; val s = resources.displayMetrics.density
        val r = 46f*s
        p.style = Paint.Style.STROKE; p.strokeWidth = 7f*s; p.color = inactive
        c.drawArc(cx-r,cy-r,cx+r,cy+r,151.5f,237f,false,p)
        p.color = active
        c.drawArc(cx-r,cy-r,cx+r,cy+r,151.5f,190f,false,p)
        p.strokeWidth = 5.5f*s
        val path=Path(); path.moveTo(cx-19*s,cy-5*s); path.cubicTo(cx-10*s,cy-14*s,cx+10*s,cy-14*s,cx+19*s,cy-5*s); c.drawPath(path,p)
        val path2=Path(); path2.moveTo(cx-11*s,cy+3*s); path2.cubicTo(cx-5*s,cy-3*s,cx+5*s,cy-3*s,cx+11*s,cy+3*s); c.drawPath(path2,p)
        p.style=Paint.Style.FILL; c.drawCircle(cx,cy+12*s,3.5f*s,p)
        val dots=floatArrayOf(-27f,-9f,9f,27f)
        dots.forEachIndexed { i,x -> p.color=if(i<3) active else inactive; c.drawCircle(cx+x*s, cy+36*s-(if(i==0||i==3) 5*s else 0f),4.5f*s,p) }
    }
}
