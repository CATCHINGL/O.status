package com.catch7ng.ostatus

import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Keep
class ShizukuAppearanceUserService : IShizukuAppearanceService.Stub() {
    private val running = AtomicBoolean(false)
    private var process: Process? = null
    private var callback: IAppearanceCallback? = null
    private val context = ArrayDeque<TraceLine>()
    private val settle = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "OStatus-AutoColour").apply { isDaemon = true } }
    private val lock = Any()
    private var pending: ScheduledFuture<*>? = null
    private var focusCheck: ScheduledFuture<*>? = null
    private var generation = 0L
    private var pendingColour: Boolean? = null
    private var pendingWindow: String? = null
    @Volatile private var focusedWindow: String? = null
    @Volatile private var lastColour: Boolean? = null
    @Volatile private var lastCommitAt = 0L

    override fun startMonitor(cb: IAppearanceCallback?) {
        callback = cb
        lastColour?.let { try { cb?.onColourChanged(it) } catch (_: Throwable) {} }
        if (!running.compareAndSet(false, true)) return
        Thread(::runLogcat, "OStatus-AutoColourEvents").apply { isDaemon = true }.start()
    }

    private fun runLogcat() {
        try {
            process = ProcessBuilder("logcat", "-v", "threadtime", "WindowManager:D", "*:S").redirectErrorStream(true).start()
            BufferedReader(InputStreamReader(process!!.inputStream)).use { reader ->
                while (running.get()) reader.readLine()?.let(::consume) ?: break
            }
        } catch (_: Throwable) {
        } finally { running.set(false) }
    }

    private fun consume(line: String) {
        val p = parse(line) ?: return
        extractFocusedWindow(p.msg)?.let { newWindow ->
            if (newWindow != focusedWindow) {
                focusedWindow = newWindow
                synchronized(lock) {
                    if (pendingWindow != null && pendingWindow != newWindow) cancelPending()
                    scheduleFocusCheck(newWindow)
                }
            }
        }
        synchronized(context) { context.addLast(p); while (context.size > 40) context.removeFirst() }
        if (!p.msg.contains("updateSystemBarAttributes", true) || !p.msg.contains("statusBarAprRegions=", true)) return
        val black = p.msg.substringAfter("statusBarAprRegions=", "").contains("LIGHT_STATUS_BARS", true)
        val prior = synchronized(context) { context.toList().dropLast(1) }
        val header = prior.asReversed().firstOrNull {
            it.pid == p.pid && it.tid == p.tid && p.at - it.at in 0..2000 &&
                it.msg.contains("updateSystemBarAttributes", true) && it.msg.contains("win=Window{", true)
        } ?: return
        val sourceWindow = Regex("""(?i)\bwin=Window\{([0-9a-f]+)\b""").find(header.msg)?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT) ?: return
        if (sourceWindow != focusedWindow) return
        schedule(black, sourceWindow)
    }


    // One-shot initial sync when focus changes. This is event-triggered, not polling.
    // It fixes apps that set their status-bar appearance before WindowManager reports the new focus.
    private fun scheduleFocusCheck(window: String) {
        focusCheck?.cancel(false)
        focusCheck = settle.schedule({ checkFocusedAppearance(window) }, 120L, TimeUnit.MILLISECONDS)
    }

    private fun checkFocusedAppearance(window: String) {
        if (focusedWindow != window || !running.get()) return
        val black = readCurrentAppearance() ?: return
        if (focusedWindow != window || !running.get()) return
        synchronized(lock) {
            // A current snapshot is authoritative for the newly focused window.
            cancelPending()
            if (black == lastColour) return
            lastColour = black
            lastCommitAt = System.currentTimeMillis()
        }
        try { callback?.onColourChanged(black) } catch (_: Throwable) {}
    }

    private fun readCurrentAppearance(): Boolean? {
        return try {
            val snapshot = ProcessBuilder("dumpsys", "window").redirectErrorStream(true).start()
            var result: Boolean? = null
            BufferedReader(InputStreamReader(snapshot.inputStream)).useLines { lines ->
                for (line in lines) {
                    if (!line.contains("mLastAppearance=", true)) continue
                    result = line.substringAfter("mLastAppearance=", "")
                        .substringBefore(' ').contains("LIGHT_STATUS_BARS", true)
                    break
                }
            }
            try { snapshot.waitFor(2, TimeUnit.SECONDS) } catch (_: Throwable) {}
            if (snapshot.isAlive) snapshot.destroy()
            result
        } catch (_: Throwable) { null }
    }

    private fun schedule(black: Boolean, window: String) = synchronized(lock) {
        pending?.cancel(false); generation++
        val g = generation; pendingColour = black; pendingWindow = window
        pending = settle.schedule({ commit(g) }, 120L, TimeUnit.MILLISECONDS)
    }

    private fun commit(g: Long) {
        val colour: Boolean; val window: String
        synchronized(lock) {
            if (g != generation) return
            colour = pendingColour ?: return; window = pendingWindow ?: return
            pending = null; pendingColour = null; pendingWindow = null
        }
        if (window != focusedWindow || colour == lastColour) return
        val now = System.currentTimeMillis()
        if (lastColour != null && lastCommitAt > 0L && now - lastCommitAt < 400L) return
        lastColour = colour; lastCommitAt = now
        try { callback?.onColourChanged(colour) } catch (_: Throwable) {}
    }

    private fun cancelPending() {
        generation++; pending?.cancel(false); pending = null; pendingColour = null; pendingWindow = null
    }

    private fun extractFocusedWindow(msg: String): String? {
        if (!msg.contains("Changing focus", true)) return null
        return Regex("""(?i)\bto\s+Window\{([0-9a-f]+)\s+""").find(msg)?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT)
    }

    private data class TraceLine(val at: Long, val pid: String, val tid: String, val msg: String)
    private fun parse(line: String): TraceLine? {
        val m = Regex("""^(\d\d-\d\d) (\d\d):(\d\d):(\d\d)\.(\d{3})\s+(\d+)\s+(\d+)\s+[VDIWEF]\s+WindowManager:\s?(.*)$""").find(line) ?: return null
        val at = (((m.groupValues[2].toLong() * 60 + m.groupValues[3].toLong()) * 60 + m.groupValues[4].toLong()) * 1000 + m.groupValues[5].toLong())
        return TraceLine(at, m.groupValues[6], m.groupValues[7], m.groupValues[8])
    }

    override fun stopMonitor() { running.set(false); synchronized(lock) { focusCheck?.cancel(false); focusCheck = null; cancelPending() }; try { process?.destroy() } catch (_: Throwable) {}; process = null; callback = null }
    @Keep fun destroy() { stopMonitor(); settle.shutdownNow(); System.exit(0) }
}
