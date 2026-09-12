package com.catch7ng.ostatus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat

class TaskRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!BootPrefs.isEnabled(context) || !Settings.canDrawOverlays(context)) return
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, StatusBarService::class.java)
            )
        } catch (_: Exception) { }
    }

    companion object {
        private const val REQUEST_CODE = 112
        private const val RECOVERY_DELAY_MS = 1500L

        fun schedule(context: Context) {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, TaskRecoveryReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                alarm.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + RECOVERY_DELAY_MS,
                    pending
                )
            } catch (_: Exception) { }
        }
    }
}
