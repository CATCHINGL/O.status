package com.catch7ng.ostatus

import android.content.*
import android.provider.Settings
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val supported = i.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            i.action == Intent.ACTION_BOOT_COMPLETED ||
            i.action == Intent.ACTION_USER_UNLOCKED ||
            i.action == Intent.ACTION_USER_PRESENT ||
            i.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!supported || !BootPrefs.isEnabled(c) || !Settings.canDrawOverlays(c)) return

        // Try at the earliest Direct-Boot point, then BOOT_COMPLETED / USER_UNLOCKED /
        // USER_PRESENT provide independent recovery points if Samsung blocks an earlier start.
        try {
            ContextCompat.startForegroundService(c, Intent(c, StatusBarService::class.java))
        } catch (_: Exception) { }

        // Once credential storage is available, refresh the Direct-Boot mirror for next cold boot.
        if (i.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            try { BootPrefs.syncFromNormal(c) } catch (_: Exception) { }
        }
    }
}
