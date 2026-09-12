package com.catch7ng.ostatus

import android.content.Context
import android.os.Build
import android.os.UserManager

object BootPrefs {
    private const val FILE = "boot_settings"

    private fun deviceContext(c: Context): Context =
        if (Build.VERSION.SDK_INT >= 24) c.createDeviceProtectedStorageContext() else c

    fun syncFromNormal(c: Context) {
        val normal = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        deviceContext(c).getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("duo_enabled", normal.getBoolean("duo_enabled", true))
            .putInt("size5", normal.getInt("size5", 24))
            .putInt("margin5", normal.getInt("margin5", 7))
            .putInt("y5", normal.getInt("y5", 0))
            .putString("color_mode", normal.getString("color_mode", "legacy"))
            .putBoolean("black5", normal.getBoolean("black5", false))
            .apply()
    }

    fun setEnabled(c: Context, enabled: Boolean) {
        deviceContext(c).getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean("duo_enabled", enabled).apply()
    }

    fun isEnabled(c: Context): Boolean {
        val boot = deviceContext(c).getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (boot.contains("duo_enabled")) return boot.getBoolean("duo_enabled", true)
        val um = c.getSystemService(UserManager::class.java)
        return if (Build.VERSION.SDK_INT < 24 || um?.isUserUnlocked != false) {
            c.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("duo_enabled", true)
        } else true
    }
}
