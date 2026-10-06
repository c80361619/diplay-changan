package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton

/**
 * KitKat's global media-button receiver (below API 21 there is no MediaSession). The head unit's
 * AVRCP play/pause/next/previous arrives here while [CarPlayMediaKeys] has registered the
 * component, and is forwarded to the iPhone as a CarPlay media HID press.
 */
class DiPlayMediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        @Suppress("DEPRECATION")
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        val index = CarPlayMediaButton.forKeyCode(event.keyCode) ?: return
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            CarPlayMediaKeys.onHardwareMediaButton(index, KeyEvent.keyCodeToString(event.keyCode))
        }
    }
}
