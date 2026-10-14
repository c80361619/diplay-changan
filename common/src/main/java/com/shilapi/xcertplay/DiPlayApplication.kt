package com.shilapi.xcertplay

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.multidex.MultiDex
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * KitKat (API 19) needs legacy multidex — the app exceeds 64K method references with
 * BouncyCastle and jmdns on board. MultiDex.install must run before any second-dex class loads.
 *
 * The crash logger is installed BEFORE MultiDex and uses only framework classes: if the
 * secondary dex fails to load (linearAlloc limits on old head units), the handler must still
 * be able to write the stack somewhere a user can fetch without ADB.
 */
class DiPlayApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        installCrashLogger()
        MultiDex.install(this)
    }

    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                startedActivityCount++
                AssistiveBallOverlay.onDiPlayForeground()
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivityCount--
                if (startedActivityCount <= 0) {
                    startedActivityCount = 0
                    AssistiveBallOverlay.onDiPlayBackground(this@DiPlayApplication)
                }
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            writeCrashLog(thread, error)
            previous?.uncaughtException(thread, error)
        }
    }

    // Deliberately framework-only: no Kotlin stdlib helpers, no app classes — those may live
    // in the secondary dex that just failed to load.
    private fun writeCrashLog(thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = StringBuilder()
        report.append("=== DiPlay crash ").append(stamp)
        report.append(" sdk=").append(Build.VERSION.SDK_INT)
        report.append(" device=").append(Build.DEVICE)
        report.append(" model=").append(Build.MODEL)
        report.append(" brand=").append(Build.BRAND)
        report.append(" fingerprint=").append(Build.FINGERPRINT)
        report.append(" thread=").append(thread.name).append(" ===\n")
        report.append(Log.getStackTraceString(error)).append("\n\n")
        val bytes = report.toString().toByteArray(Charsets.UTF_8)
        val download = File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS), "diplay-crash.log")
        val internal = File(filesDir, "diplay-crash.log")
        for (target in arrayOf(download, internal)) {
            try {
                target.parentFile?.mkdirs()
                FileOutputStream(target, true).use { it.write(bytes) }
                Log.w("DiPlay", "crash log written to " + target.absolutePath)
                return
            } catch (_: Exception) {
                // try the next location
            }
        }
    }
}
