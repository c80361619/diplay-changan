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
        val action = intent.action ?: return
        if (action == Intent.ACTION_MEDIA_BUTTON) {
            @Suppress("DEPRECATION")
            val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
            val index = CarPlayMediaButton.forKeyCode(event.keyCode)
            if (index != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                android.util.Log.i("DiPlay-MediaKeys", "MediaButton received: ${KeyEvent.keyCodeToString(event.keyCode)} -> HID $index")
                CarPlayMediaKeys.onHardwareMediaButton(index, KeyEvent.keyCodeToString(event.keyCode))
                if (isOrderedBroadcast) abortBroadcast()
            }
        } else if (action == "com.android.music.musicservicecommand") {
            val cmd = intent.getStringExtra("command")
            val index = CarPlayMediaButton.forCommand(cmd)
            if (index != null) {
                android.util.Log.i("DiPlay-MediaKeys", "MusicServiceCommand received: $cmd -> HID $index")
                CarPlayMediaKeys.onHardwareMediaButton(index, "musicservicecommand:$cmd")
                if (isOrderedBroadcast) abortBroadcast()
            }
        }
    }
}
