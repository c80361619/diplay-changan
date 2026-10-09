package com.shilapi.xcertplay.airplay

import android.view.KeyEvent

/**
 * Hardware media keys → CarPlay media HID presses (indices into [AirPlayHid]'s media report).
 *
 * Hardware play and pause keys both map to the toggle: BYD picks PLAY or PAUSE from its own idea of
 * the play state, and a wrong guess would make the button do nothing. Explicit play and pause
 * commands from media controllers use [PLAY] and [PAUSE].
 */
object CarPlayMediaButton {
    const val PLAY = 1
    const val PAUSE = 2
    const val PLAY_PAUSE = 3
    const val NEXT = 4
    const val PREVIOUS = 5

    /** BYD's steering-wheel play/pause key; the firmware normally rewrites it to MEDIA_PLAY/PAUSE. */
    const val KEYCODE_BYD_AUTO_MEDIA_PLAY_PAUSE = 353

    /** BYD's steering-wheel voice key: a short press, and the code the wheel sends for a long press. */
    const val KEYCODE_BYD_AUTO_MEDIA_VOICE = 304
    const val KEYCODE_BYD_AUTO_MEDIA_VOICE_LONG = 312

    /**
     * Whether [keyCode] is a voice key that opens Siri. The BYD wheel sends each press as an
     * instant down/up pair, so a long press arrives as its own key rather than as a held one.
     */
    fun opensSiri(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
        keyCode == KEYCODE_BYD_AUTO_MEDIA_VOICE || keyCode == KEYCODE_BYD_AUTO_MEDIA_VOICE_LONG

    /** The CarPlay press for [keyCode], or null when the key is not a media key CarPlay handles. */
    fun forKeyCode(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_CHANNEL_UP,
        272, // KEYCODE_MEDIA_SKIP_FORWARD (API 21)
        274, // KEYCODE_MEDIA_STEP_FORWARD (API 21)
        -> NEXT

        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_CHANNEL_DOWN,
        273, // KEYCODE_MEDIA_SKIP_BACKWARD (API 21)
        275, // KEYCODE_MEDIA_STEP_BACKWARD (API 21)
        -> PREVIOUS

        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KEYCODE_BYD_AUTO_MEDIA_PLAY_PAUSE,
        -> PLAY_PAUSE

        else -> null
    }

    /** Translates broadcast command strings (e.g. musicservicecommand) to CarPlay media buttons. */
    fun forCommand(command: String?): Int? = when (command?.lowercase()) {
        "next", "skip_next", "forward" -> NEXT
        "prev", "previous", "skip_prev", "backward", "rewind" -> PREVIOUS
        "play", "pause", "play_pause", "togglepause", "stop" -> PLAY_PAUSE
        else -> null
    }
}
