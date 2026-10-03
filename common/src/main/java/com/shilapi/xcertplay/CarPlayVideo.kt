package com.shilapi.xcertplay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.shilapi.xcertplay.airplay.VideoInCar
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.BydNavigationOutputs
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.orchestration.CarPlayVideoListener
import java.util.concurrent.Executors

/**
 * iOS 27 video in car (see [VideoInCar]). The iPhone hands the car a media URL (insertPlayQueueItem)
 * and drives it (setRate, seek, stop). KitKat build: the parked-video player UI (media3
 * CarPlayVideoActivity) is not shipped — the protocol state machine stays so the iPhone gets correct
 * answers, but nothing opens on the car screen.
 */
internal object CarPlayVideo : CarPlayVideoListener {
    private const val TAG = "DiPlay-Video"
    const val SKIP_MILLIS = 10_000

    private val main = Handler(Looper.getMainLooper())
    private val sender = Executors.newSingleThreadExecutor { Thread(it, "diplay-video-reply").apply { isDaemon = true } }
    @Volatile private var appContext: Context? = null
    @Volatile private var controller: CarPlayController? = null

    // Main-thread state. The stream and item are also read by loader threads.
    @Volatile private var streamId: Long? = null
    @Volatile private var itemUuid: Any? = null
    var url: String? = null
        private set
    /** The iPhone called the item streaming (HLS) rather than a file. */
    var streaming = false
        private set
    var startMillis = 0
        private set
    var playing = false
        private set
    var pendingSeekMillis: Int? = null

    fun attach(context: Context, next: CarPlayController) {
        appContext = context.applicationContext
        controller = next
        next.videoListener = this
    }

    override fun readParked(): Boolean? = appContext?.let(BydNavigationOutputs::parked)

    override fun onVideoAllowedChanged(allowed: Boolean) {
        if (!allowed) main.post { closePlayer("the car left P") }
    }

    override fun onVideoSessionEnded() {
        main.post {
            stop()
            streamId = null
        }
    }

    override fun onVideoUiRequested() {
        main.post { show() }
    }

    override fun onVideoMessage(streamId: Long, message: Map<String, Any?>) {
        main.post { handle(streamId, message) }
    }

    /**
     * A steering-wheel media key (CarPlayMediaButton index) while the player is on screen. The KitKat
     * build never opens the player, so every key falls through to the normal CarPlay path.
     */
    fun onMediaKey(index: Int): Boolean = false

    /** Play or pause from the car (wheel or on-screen button); tells the iPhone at once. Main thread. */
    fun setPlaying(next: Boolean) {
        playing = next
        streamId?.let { reply(it, VideoInCar.playbackStateNotification(next, itemUuid)) }
    }

    /**
     * The car's player cannot play the item, for example protected video: tell the iPhone as Apple's
     * receiver does, say so on the car screen and go back to CarPlay instead of showing black. Main thread.
     */
    fun onPlayerFailed(code: Int) {
        Log.w(TAG, "item cannot play here code=$code")
        streamId?.let { reply(it, VideoInCar.errorNotification(itemUuid, code)) }
        appContext?.let { Toast.makeText(it, R.string.video_cannot_play, Toast.LENGTH_LONG).show() }
        stop()
    }

    /** The player closed on the car (Back, or the car left P): pause, so the iPhone shows it paused. */
    fun onPlayerClosed(positionMillis: Int?) {
        positionMillis?.let { startMillis = it }
        if (playing) setPlaying(false)
    }

    private fun handle(streamId: Long, message: Map<String, Any?>) {
        this.streamId = streamId
        val request = message["kind"] == "request"
        when (val type = message["type"] as? String) {
            "insertPlayQueueItem" -> {
                // An app's own scheme would be loaded through the iPhone; no local player in this build.
                val item = VideoInCar.parseItem(message, iphoneLoadsAppSchemes = true)
                if (item == null) {
                    Log.w(TAG, "queue item this player cannot play")
                    return
                }
                Log.i(TAG, "queue item app=${(message["item"] as? Map<*, *>)?.get("clientBundleID")}")
                url = item.url
                itemUuid = item.uuid
                val queued = message["item"] as? Map<*, *>
                streaming = queued?.get("mediaType") == "streaming"
                Log.i(TAG, "queue item host=${android.net.Uri.parse(item.url).host} mediaType=${queued?.get("mediaType")}")
                startMillis = item.startMillis
            }
            "setRate" -> {
                playing = ((message["rate"] as? Number)?.toDouble() ?: 0.0) > 0.0
                // No local player opens; the state machine still tracks the iPhone's intent.
            }
            "seek" -> {
                VideoInCar.seekMillis(message)?.let {
                    pendingSeekMillis = it
                }
                if (request) reply(streamId, VideoInCar.seekResponse(message["messageID"]))
            }
            "playbackInfo" -> if (request) {
                reply(streamId, VideoInCar.playbackInfoResponse(message["messageID"], itemUuid, playerState()))
            }
            "property" -> if (request) {
                reply(streamId, VideoInCar.propertyResponse(message["messageID"], message["property"], playerState()))
            }
            "unhandledURL" -> onUrlLoaded(message)
            "stop", "removePlayQueueItem" -> stop()
            "setProperty" -> Unit
            else -> Log.i(TAG, "video message $type keys=${message.keys}")
        }
    }

    /** What would have been answered to the removed iPhone URL resolver; logged and dropped now. */
    private fun onUrlLoaded(message: Map<String, Any?>) {
        Log.i(TAG, "iPhone unhandledURL answer dropped (no local player) keys=${message.keys}")
    }

    // With no local player the item stays paused where it was, so the iPhone keeps its position.
    private fun playerState(): VideoInCar.PlayerState? =
        url?.let { VideoInCar.PlayerState(false, false, startMillis / 1000.0, 0.0, 0.0) }

    private fun reply(streamId: Long, message: Map<String, Any?>) {
        val target = controller ?: return
        sender.execute {
            // A reply that cannot be sent must never take CarPlay down with it.
            val sent = runCatching { target.sendVideoMessage(streamId, message) }
                .onFailure { Log.w(TAG, "reply ${message["type"]} failed: ${it.javaClass.simpleName}") }
                .getOrDefault(false)
            if (!sent) Log.w(TAG, "reply ${message["type"]} not sent")
        }
    }

    private fun show() {
        Log.i(TAG, "video player requested; the KitKat build ships no parked-video UI")
    }

    private fun closePlayer(reason: String) {
        Log.i(TAG, "video player close requested (none open): $reason")
    }

    private fun stop() {
        url = null
        itemUuid = null
        playing = false
        pendingSeekMillis = null
    }
}
