package com.shilapi.xcertplay.compat

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process

/**
 * KitKat compatibility shims for platform methods introduced after API 19.
 * Service lookups compare class *names* so the callee class itself is never loaded on old devices.
 */
fun <T : Any> Context.getSystemServiceCompat(serviceClass: Class<T>): T? {
    if (Build.VERSION.SDK_INT >= 23) return getSystemService(serviceClass)
    val name = when (serviceClass.name) {
        "android.location.LocationManager" -> Context.LOCATION_SERVICE
        "android.media.AudioManager" -> Context.AUDIO_SERVICE
        "android.net.ConnectivityManager" -> Context.CONNECTIVITY_SERVICE
        "android.net.wifi.WifiManager" -> Context.WIFI_SERVICE
        "android.net.wifi.p2p.WifiP2pManager" -> Context.WIFI_P2P_SERVICE
        "android.app.AppOpsManager" -> Context.APP_OPS_SERVICE
        "android.hardware.usb.UsbManager" -> Context.USB_SERVICE
        "android.bluetooth.BluetoothManager" -> Context.BLUETOOTH_SERVICE
        "android.view.WindowManager" -> Context.WINDOW_SERVICE
        "android.hardware.display.DisplayManager" -> Context.DISPLAY_SERVICE
        "android.content.ClipboardManager" -> Context.CLIPBOARD_SERVICE
        else -> return null
    }
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    return getSystemService(name) as? T
}

/** Context.checkSelfPermission needs API 23; Context.checkPermission exists since API 1. */
fun Context.checkSelfPermissionCompat(permission: String): Boolean {
    if (Build.VERSION.SDK_INT >= 23) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
    return checkPermission(permission, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED
}

/**
 * Audio focus across API levels: AudioFocusRequest on 26+, the deprecated stream API below.
 * Callers pass usage/contentType as ints (compile-time constants, safe on KitKat): the modern
 * path builds an AudioAttributes from them, the legacy path maps usage to a stream type —
 * pre-26 audio policy has no usage routing.
 */
class AudioFocusHandle(
    private val manager: AudioManager,
    private val listener: AudioManager.OnAudioFocusChangeListener,
) {
    // AudioFocusRequest on API 26+; never touched below (lazy class loading keeps KitKat safe).
    private var request: AudioFocusRequest? = null

    /** Drops any held focus, then requests focus. Returns granted. */
    fun request(usage: Int, contentType: Int, gain: Int, handler: Handler): Boolean {
        abandon()
        return if (Build.VERSION.SDK_INT >= 26) {
            val attributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .build()
            val built = AudioFocusRequest.Builder(gain)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(listener, handler)
                .build()
            request = built
            manager.requestAudioFocus(built) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(listener, streamFor(usage), gain) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandon() {
        val current = request ?: return
        request = null
        manager.abandonAudioFocusRequestCompat(current)
    }

    private fun streamFor(usage: Int): Int = when (usage) {
        AudioAttributes.USAGE_VOICE_COMMUNICATION, AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING ->
            AudioManager.STREAM_VOICE_CALL
        else -> AudioManager.STREAM_MUSIC
    }
}

/** AudioFocusRequest type is API 26; KitKat never loads this class through the guarded paths. */
fun AudioManager.abandonAudioFocusRequestCompat(request: AudioFocusRequest) {
    if (Build.VERSION.SDK_INT >= 26) {
        abandonAudioFocusRequest(request)
    } else {
        @Suppress("DEPRECATION")
        abandonAudioFocus(null)
    }
}
