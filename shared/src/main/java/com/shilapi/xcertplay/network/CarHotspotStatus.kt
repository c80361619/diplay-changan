package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.compat.getSystemServiceCompat
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Reads (and on KitKat toggles) the head unit's own Wi-Fi hotspot, for the "Car hotspot" link.
 *
 * Modern Android only grants tethering over ADB (TETHER_PRIVILEGED), so the user turns the
 * hotspot on in the car settings there. KitKat has no such gate: the hidden setWifiApEnabled
 * needs only CHANGE_WIFI_STATE, which the app holds, so DiPlay can enable the saved hotspot
 * configuration itself.
 */
object CarHotspotStatus {
    private const val WIFI_AP_STATE_ENABLED = 13

    /**
     * True/false from the Wi-Fi AP state, or null when the firmware hides it (then callers must
     * not block the connection). Interface flags are not used: BYD keeps wlan1 up with an address
     * while tethering is off.
     */
    fun isEnabled(context: Context): Boolean? {
        val wifi = context.applicationContext.getSystemServiceCompat(WifiManager::class.java) ?: return null
        return runCatching {
            WifiManager::class.java.getMethod("getWifiApState").invoke(wifi) as Int == WIFI_AP_STATE_ENABLED
        }.recoverCatching {
            WifiManager::class.java.getMethod("isWifiApEnabled").invoke(wifi) as Boolean
        }.getOrNull()
    }

    /**
     * Enables the hotspot with its saved configuration (a null WifiConfiguration means "the
     * configuration the user last set in the car settings"). Returns true when the platform
     * accepted the request, false when it refused, null when this Android cannot do it (O+,
     * where tethering is privileged) or the call failed. The radio needs a few seconds to come
     * up; callers poll [isEnabled] or the hotspot interface afterwards.
     */
    fun enableIfPossible(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return null
        val wifi = context.applicationContext.getSystemServiceCompat(WifiManager::class.java) ?: return null
        return runCatching {
            WifiManager::class.java.getMethod(
                "setWifiApEnabled",
                WifiConfiguration::class.java,
                Boolean::class.javaPrimitiveType,
            ).invoke(wifi, null, true) as Boolean
        }.getOrNull()
    }
}
