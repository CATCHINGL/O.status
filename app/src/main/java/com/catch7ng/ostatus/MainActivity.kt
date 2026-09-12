package com.catch7ng.ostatus

import android.Manifest
import android.app.NotificationManager
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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val d by lazy { resources.displayMetrics.density }
    private var overlayValue: TextView? = null
    private var phoneValue: TextView? = null
    private var dndValue: TextView? = null
    private var colorValue: TextView? = null
    private var suppressSwitch = false

    private val dark get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val pageColor get() = if (dark) Color.BLACK else Color.rgb(242,242,247)
    private val cardColor get() = if (dark) Color.rgb(28,28,30) else Color.WHITE
    private val primary get() = if (dark) Color.WHITE else Color.BLACK
    private val secondary get() = if (dark) Color.rgb(142,142,147) else Color.rgb(99,99,102)
    private val separator get() = if (dark) Color.rgb(56,56,58) else Color.rgb(229,229,234)
    private val iosBlue = Color.rgb(0,122,255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageColor
        window.navigationBarColor = pageColor
        if (!dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        buildUi()
    }

    override fun onResume() { super.onResume(); refreshPermissionStates() }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(pageColor); isFillViewport = true }
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
        val enabledSwitch = SwitchCompat(this).apply {
            isChecked = prefs.getBoolean("duo_enabled", true)
            showText = false
            setOnCheckedChangeListener { _, checked ->
                if (suppressSwitch) return@setOnCheckedChangeListener
                if (checked && !Settings.canDrawOverlays(this@MainActivity)) {
                    suppressSwitch = true; isChecked = false; suppressSwitch = false
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    return@setOnCheckedChangeListener
                }
                prefs.edit().putBoolean("duo_enabled", checked).apply()
                BootPrefs.setEnabled(this@MainActivity, checked)
                if (checked) ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, StatusBarService::class.java))
                else stopService(Intent(this@MainActivity, StatusBarService::class.java))
            }
        }
        onRow.addView(enabledSwitch)
        mainCard.addView(onRow)
        content.addView(mainCard)

        sectionTitle(content, "APPEARANCE")
        val appearance = card()
        val colorRow = row("Indicator Colour")
        colorValue = valueLabel(colorName())
        colorRow.addView(colorValue)
        colorRow.setOnClickListener { showColorPicker() }
        appearance.addView(colorRow)
        appearance.addView(separatorView())
        val positionRow = row("Position & Size")
        positionRow.addView(valueLabel("›").apply { textSize = 25f })
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
        val bootRow = row("Start on Boot")
        bootRow.addView(valueLabel("On")); options.addView(bootRow)
        options.addView(separatorView())
        val detailsRow = row("Details")
        detailsRow.addView(valueLabel("›").apply { textSize = 25f })
        detailsRow.setOnClickListener { startActivity(Intent(this, DetailsActivity::class.java)) }
        options.addView(detailsRow)
        options.addView(separatorView())
        val diagRow = row("Diagnostics")
        diagRow.addView(valueLabel("›").apply { textSize = 25f })
        diagRow.setOnClickListener { showDiagnostics() }
        options.addView(diagRow)
        content.addView(options)

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(34), 0, dp(10))

            addView(TextView(this@MainActivity).apply {
                text = "O.status ${displayVersion()}\n© 2026 CATCH7NG.L · All Rights Reserved"
                textSize = 11.5f
                setTextColor(secondary)
                gravity = Gravity.CENTER
            })

            addView(TextView(this@MainActivity).apply {
                text = "GitHub · CATCHINGL"
                textSize = 11.5f
                setTextColor(iosBlue)
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, 0)
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CATCHINGL")))
                }
            })
        })

        scroll.addView(content)
        setContentView(scroll)
        refreshPermissionStates()
    }

    private fun showColorPicker() {
        val labels = arrayOf("Auto", "Black", "White")
        val keys = arrayOf("auto", "black", "white")
        val current = keys.indexOf(prefs.getString("color_mode", "auto")).coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Indicator Colour")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.edit().putString("color_mode", keys[which]).apply()
                colorValue?.text = labels[which]
                if (prefs.getBoolean("duo_enabled", true) && Settings.canDrawOverlays(this)) {
                    stopService(Intent(this, StatusBarService::class.java))
                    ContextCompat.startForegroundService(this, Intent(this, StatusBarService::class.java))
                }
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showDiagnostics() {
        val phone = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val msg = buildString {
            append("O.status ${displayVersion()}\n\n")
            append("Overlay permission: ${Settings.canDrawOverlays(this@MainActivity)}\n")
            append("Phone permission: $phone\n")
            append("DND access: ${getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted}\n\n")
            append("Colour mode: ${colorName()}\n")
            append("O Status Bar enabled: ${prefs.getBoolean("duo_enabled", true)}")
        }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Diagnostics").setMessage(msg).setPositiveButton("Done", null).show()
    }

    private fun refreshPermissionStates() {
        overlayValue?.let { setPermission(it, Settings.canDrawOverlays(this)) }
        phoneValue?.let { setPermission(it, ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) }
        dndValue?.let { setPermission(it, getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) }
    }

    private fun setPermission(v: TextView, granted: Boolean) {
        v.text = if (granted) "Allowed" else "Required  ›"
        v.setTextColor(if (granted) secondary else iosBlue)
    }

    private fun displayVersion(): String {
        val raw = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
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
