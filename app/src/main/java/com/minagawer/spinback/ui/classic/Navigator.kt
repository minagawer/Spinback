package com.minagawer.spinback.ui.classic

import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.minagawer.spinback.data.Album
import com.minagawer.spinback.data.Artist
import com.minagawer.spinback.data.Library
import com.minagawer.spinback.data.Track
import com.minagawer.spinback.playback.PlayerConnection
import kotlin.math.abs

private const val SCRUB_STEP_MS = 5_000L
private val SLEEP_TIMER_MINUTES = listOf(15, 30, 60, 90, 120)

data class MenuItem(
    val label: String,
    val detail: String? = null,
    /** A second, smaller line under the label, like an album's song count. */
    val subtitle: String? = null,
    val artwork: Track? = null,
    /** Shows the music-note tile in place of artwork, for "all songs" in an artist's album list. */
    val allSongsTile: Boolean = false,
    /** Shows a ">" because selecting it opens another menu. */
    val opens: Boolean = true,
    /** The song this row plays, so the row can mark it while it's playing. */
    val trackId: String? = null,
    val onSelect: () -> Unit,
)

sealed class Page(val title: String)

open class MenuPage(
    title: String,
    private val entries: List<MenuItem> = emptyList(),
    val showArtwork: Boolean = false,
) : Page(title) {
    open val items: List<MenuItem> get() = entries
    var selected by mutableIntStateOf(0)
    val listState = LazyListState()
}

enum class NowPlayingMode { Progress, Volume, Scrub }

class NowPlayingPage : Page("再生中") {
    var mode by mutableStateOf(NowPlayingMode.Progress)
    var volume by mutableFloatStateOf(0f)
        private set

    /** Bumped on every wheel input, so the screen can drop back to the progress bar after a pause. */
    var lastInteraction by mutableLongStateOf(0L)
        private set

    fun toggleScrub() {
        mode = if (mode == NowPlayingMode.Scrub) NowPlayingMode.Progress else NowPlayingMode.Scrub
        lastInteraction = SystemClock.uptimeMillis()
    }

    /** Returns whether anything changed, i.e. the wheel wasn't already at the end of its range. */
    fun rotate(steps: Int, player: PlayerConnection, audio: AudioManager): Boolean {
        lastInteraction = SystemClock.uptimeMillis()
        if (mode == NowPlayingMode.Scrub) {
            val from = player.positionMs
            val to = (from + steps * SCRUB_STEP_MS).coerceIn(0L, player.durationMs)
            player.seekTo(to)
            return to != from
        } else {
            mode = NowPlayingMode.Volume
            val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val direction = if (steps > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            repeat(abs(steps).coerceAtMost(2)) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            }
            val after = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            volume = after.toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            return after != before
        }
    }
}

/** The iPod-style menu stack: MENU goes back, the center button selects. */
class Navigator(
    private val library: Library,
    private val player: PlayerConnection,
) {
    private val stack = mutableStateListOf<Page>(mainMenu())

    val current: Page get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun back() {
        if (canGoBack) stack.removeAt(stack.lastIndex)
    }

    fun home() {
        while (canGoBack) back()
    }

    fun select() {
        when (val page = current) {
            is MenuPage -> page.items.getOrNull(page.selected)?.onSelect?.invoke()
            is NowPlayingPage -> page.toggleScrub()
        }
    }

    /** Returns whether the turn moved anything, so the wheel stays silent at the end of a list. */
    fun rotate(steps: Int, audio: AudioManager): Boolean =
        when (val page = current) {
            is MenuPage -> {
                val from = page.selected
                page.selected = (from + steps).coerceIn(0, (page.items.size - 1).coerceAtLeast(0))
                page.selected != from
            }

            is NowPlayingPage -> page.rotate(steps, player, audio)
        }

    fun openNowPlaying() {
        if (current !is NowPlayingPage) stack += NowPlayingPage()
    }

    private fun open(page: Page) {
        stack += page
    }

    private fun play(tracks: List<Track>, index: Int, shuffled: Boolean = false) {
        player.play(tracks, index, shuffled)
        openNowPlaying()
    }

    private fun mainMenu(): MenuPage {
        val fixed = listOf(
            MenuItem("ミュージック") { open(musicMenu()) },
            MenuItem("よく聴いた") { open(mostPlayedMenu()) },
            MenuItem("曲をシャッフル", opens = false) { shuffleAll() },
        )
        return object : MenuPage("Spinback", showArtwork = true) {
            override val items: List<MenuItem>
                get() {
                    // Shows the minutes left while the timer runs.
                    val sleepTimer = MenuItem(
                        "スリープタイマー",
                        detail = player.sleepMinutesLeft?.let { "${it}分" },
                    ) { open(sleepTimerMenu()) }
                    val nowPlaying = MenuItem("再生中") { openNowPlaying() }
                    return fixed + sleepTimer + if (player.currentTrackId != null) listOf(nowPlaying) else emptyList()
                }
        }
    }

    private fun mostPlayedMenu() =
        trackList("よく聴いた", library.mostPlayed, showPlayCount = true)

    private fun shuffleAll() {
        if (library.songs.isNotEmpty()) play(
            library.songs,
            library.songs.indices.random(),
            shuffled = true
        )
    }

    private fun sleepTimerMenu(): MenuPage {
        fun option(label: String, minutes: Int) = MenuItem(label, opens = false) {
            player.setSleepTimer(minutes)
            back()
        }
        return MenuPage(
            "スリープタイマー",
            listOf(option("オフ", 0)) +
                    SLEEP_TIMER_MINUTES.map { option("${it}分", it) },
        )
    }

    private fun musicMenu() = MenuPage(
        "ミュージック",
        listOf(
            MenuItem("プレイリスト") { open(playlistsMenu()) },
            MenuItem("アーティスト") { open(artistsMenu()) },
            MenuItem("アルバム") { open(albumsMenu()) },
            MenuItem("曲") {
                open(trackList("曲", library.songs, showArtist = true))
            },
        ),
        // Like the main menu, the Music menu keeps the cover on the right half.
        showArtwork = true,
    )

    private fun playlistsMenu() = MenuPage(
        "プレイリスト",
        library.playlists.map { p -> MenuItem(p.name) { open(trackList(p.name, p.tracks)) } },
    )

    private fun artistsMenu() = MenuPage(
        "アーティスト",
        library.artists.map { a -> MenuItem(a.name) { open(artistMenu(a)) } },
    )

    // Across all albums, each is labelled with its artist; under an artist, with its size.
    private fun albumsMenu() = MenuPage(
        "アルバム",
        library.albums.map { albumItem(it, it.artist) },
    )

    private fun artistMenu(artist: Artist): MenuPage {
        val allSongs = MenuItem(
            "全曲",
            subtitle = songCount(artist.tracks.size),
            allSongsTile = true,
        ) { open(trackList(artist.name, artist.tracks)) }
        return MenuPage(
            artist.name,
            listOf(allSongs) + artist.albums.map { albumItem(it, songCount(it.tracks.size)) },
        )
    }

    private fun albumItem(album: Album, subtitle: String) =
        MenuItem(album.title, subtitle = subtitle, artwork = album.tracks.first()) {
            open(trackList(album.title, album.tracks))
        }

    private fun songCount(n: Int) = "$n 曲"

    private fun trackList(
        title: String,
        tracks: List<Track>,
        showPlayCount: Boolean = false,
        showArtist: Boolean = false,
    ) = MenuPage(
        title,
        tracks.mapIndexed { i, track ->
            MenuItem(
                track.name,
                detail = if (showPlayCount) "${track.playCount}" else null,
                subtitle = if (showArtist) track.artist else null,
                opens = false,
                trackId = track.id,
            ) { play(tracks, i) }
        },
    )
}
