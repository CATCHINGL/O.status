package com.catch7ng.ostatus

import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager

/** Tracks Android's display-oriented mobile network info (including 5G NSA branding). */
class NetworkDisplayTracker(
    private val tm: TelephonyManager,
    private val executor: java.util.concurrent.Executor,
    private val onChanged: () -> Unit
) {
    @Volatile var displayInfo: TelephonyDisplayInfo? = null
        private set

    private var callback31: TelephonyCallback? = null
    @Suppress("DEPRECATION") private var listener30: PhoneStateListener? = null

    fun start() {
        stop()
        if (Build.VERSION.SDK_INT >= 31) {
            val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                    displayInfo = info
                    onChanged()
                }
            }
            callback31 = cb
            try { tm.registerTelephonyCallback(executor, cb) } catch (_: Exception) {}
        } else if (Build.VERSION.SDK_INT >= 30) {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                    displayInfo = info
                    onChanged()
                }
            }
            listener30 = listener
            @Suppress("DEPRECATION")
            try { tm.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED) } catch (_: Exception) {}
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= 31) callback31?.let {
            try { tm.unregisterTelephonyCallback(it) } catch (_: Exception) {}
        }
        @Suppress("DEPRECATION")
        listener30?.let { try { tm.listen(it, PhoneStateListener.LISTEN_NONE) } catch (_: Exception) {} }
        callback31 = null
        listener30 = null
    }

    companion object {
        fun label(info: TelephonyDisplayInfo?, fallbackNetworkType: Int): String {
            val networkType = info?.networkType ?: fallbackNetworkType
            if (networkType == TelephonyManager.NETWORK_TYPE_NR) return "5G"
            if (Build.VERSION.SDK_INT >= 30) {
                when (info?.overrideNetworkType) {
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> return "5G"
                }
            }
            return when (networkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> ""
            }
        }
    }
}
