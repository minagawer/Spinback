package com.minagawer.spinback.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.minagawer.spinback.data.Track

/** Bridges a [MediaController] to Compose state. */
class PlayerConnection(private val context: Context) : Player.Listener {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    var currentTrackId by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var queueIndex by mutableIntStateOf(0)
        private set
    var queueSize by mutableIntStateOf(0)
        private set

    /** true / false while ⏭ / ⏮ is held to scan, null otherwise. */
    var scanningForward by mutableStateOf<Boolean?>(null)
        private set

    val positionMs: Long get() = controller?.currentPosition ?: 0L

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            if (future !== f) return@addListener
            controller = f.get().also {
                it.addListener(this)
                sync(it)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(this)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    fun play(tracks: List<Track>, startIndex: Int, shuffled: Boolean = false) {
        val c = controller ?: return
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0L)
        c.shuffleModeEnabled = shuffled
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playWhenReady) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem()

    // Like an iPod: restart the song if we're past the first few seconds.
    fun previous() = controller?.seekToPrevious()

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    /**
     * One tick of holding ⏭ / ⏮, like an iPod: scanning speeds up the longer it is held, and
     * running off the end carries on into the next song.
     */
    fun scan(forward: Boolean, heldMs: Long) {
        val c = controller ?: return
        if (c.currentMediaItem == null) return
        scanningForward = forward
        val rate = SCAN_RATES[(heldMs / 1_000).toInt().coerceIn(0, SCAN_RATES.lastIndex)]
        val step = rate * SCAN_TICK_MS
        val target = c.currentPosition + if (forward) step else -step
        when {
            target < 0 -> c.seekTo(0)
            c.duration > 0 && target >= c.duration -> if (c.hasNextMediaItem()) c.seekToNextMediaItem()
            else -> c.seekTo(target)
        }
    }

    fun endScan() {
        scanningForward = null
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    override fun onEvents(player: Player, events: Player.Events) = sync(player)

    private fun sync(player: Player) {
        currentTrackId = player.currentMediaItem?.mediaId
        // Whether it's meant to be playing, not whether sound is coming out this instant: seeking
        // briefly rebuffers, and isPlaying would flicker the play/pause icon on every step.
        isPlaying = player.playWhenReady &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED
        shuffle = player.shuffleModeEnabled
        repeatMode = player.repeatMode
        durationMs = player.duration.coerceAtLeast(0L)
        queueIndex = player.currentMediaItemIndex
        queueSize = player.mediaItemCount
    }
}

/** How often the wheel calls [PlayerConnection.scan] (its SCAN_INTERVAL_MS). */
private const val SCAN_TICK_MS = 200L

/** Scan speed (x realtime) for each second of holding. */
private val SCAN_RATES = longArrayOf(10, 14, 19, 27, 37, 40)

private fun Track.toMediaItem(): MediaItem {
    val uri = Uri.fromFile(file)
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setAlbumArtist(albumArtist)
                .setArtworkUri(artwork?.let(Uri::fromFile))
                .build()
        )
        .build()
}
