package com.catch7ng.ostatus

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

class ShizukuAppearanceManager(private val context: Context, private val onColour: (Boolean?) -> Unit) {
    private var remote: IShizukuAppearanceService? = null
    private var bound = false
    private val callback = object : IAppearanceCallback.Stub() { override fun onColourChanged(black: Boolean) = onColour(black) }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            remote = IShizukuAppearanceService.Stub.asInterface(binder); bound = true
            try { remote?.startMonitor(callback) } catch (_: Throwable) { onColour(null) }
        }
        override fun onServiceDisconnected(name: ComponentName?) { remote = null; bound = false; onColour(null) }
    }
    private val binderReceived = Shizuku.OnBinderReceivedListener { start() }
    private val binderDead = Shizuku.OnBinderDeadListener { remote = null; bound = false; onColour(null) }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) start() else onColour(null)
    }

    fun start() {
        Shizuku.addBinderReceivedListener(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        if (!isAuthorized()) { onColour(null); return }
        if (bound) return
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShizukuAppearanceUserService::class.java.name))
            .tag("appearance").processNameSuffix("appearance").version(1).daemon(false).debuggable(false)
        try { Shizuku.bindUserService(args, connection) } catch (_: Throwable) { onColour(null) }
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(binderReceived); Shizuku.removeBinderDeadListener(binderDead); Shizuku.removeRequestPermissionResultListener(permissionResult)
        try { remote?.stopMonitor() } catch (_: Throwable) {}
        if (bound) try { Shizuku.unbindUserService(Shizuku.UserServiceArgs(ComponentName(context.packageName, ShizukuAppearanceUserService::class.java.name)).tag("appearance").processNameSuffix("appearance").version(1).daemon(false).debuggable(false), connection, true) } catch (_: Throwable) {}
        remote = null; bound = false; onColour(null)
    }

    companion object {
        fun isInstalled(context: Context) = try { context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true } catch (_: Exception) { false }
        fun isRunning() = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        fun isAuthorized() = isRunning() && try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Throwable) { false }
        fun status(context: Context) = when { !isInstalled(context) -> "Not Installed  ›"; !isRunning() -> "Not Connected  ›"; !isAuthorized() -> "Not Connected  ›"; else -> "Connected" }
    }
}
