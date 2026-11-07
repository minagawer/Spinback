package com.minagawer.spinback.data

import org.json.JSONObject
import java.io.File

data class Track(
    val id: String,
    val name: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val trackNumber: Int,
    val discNumber: Int,
    val durationMs: Long,
    val playCount: Int,
    val lastPlayed: Long?,
    val file: File,
    /** Album art extracted from the iTunes artwork cache, if any. */
    val artwork: File?,
    /** iTunes' "sort as" names, falling back to the shown ones. */
    val sortName: String = name,
    val sortArtist: String = artist,
    val sortAlbum: String = album,
)

data class Album(val title: String, val artist: String, val tracks: List<Track>) {
    val sortTitle: String get() = tracks.first().sortAlbum
}

data class Artist(val name: String, val albums: List<Album>, val tracks: List<Track>) {
    val sortName: String get() = tracks.first().sortArtist
}

data class Playlist(val name: String, val tracks: List<Track>)

class Library(val tracks: List<Track>, val playlists: List<Playlist>) {
    val byId: Map<String, Track> = tracks.associateBy { it.id }

    val songs: List<Track> = tracks.sortedWith(compareBy(IpodOrder) { it.sortName })

    val mostPlayed: List<Track> = tracks
        .filter { it.playCount > 0 }
        .sortedWith(compareByDescending<Track> { it.playCount }.thenByDescending {
            it.lastPlayed ?: 0
        })

    val albums: List<Album> = albumsOf(tracks) { it.albumArtist }

    val artists: List<Artist> = tracks
        .groupBy { it.artist }
        .map { (name, artistTracks) ->
            val artistAlbums = albumsOf(artistTracks) { name }
            Artist(name, artistAlbums, artistAlbums.flatMap { it.tracks })
        }
        .sortedWith(compareBy(IpodOrder) { it.sortName })

    /** Groups [tracks] into albums by title and [artistOf], in iPod order, each in disc and track order. */
    private fun albumsOf(tracks: List<Track>, artistOf: (Track) -> String): List<Album> = tracks
        .groupBy { artistOf(it) to it.album }
        .map { (key, albumTracks) ->
            Album(
                title = key.second,
                artist = key.first,
                tracks = albumTracks.sortedWith(compareBy({ it.discNumber }, { it.trackNumber })),
            )
        }
        .sortedWith(compareBy(IpodOrder) { it.sortTitle })

    companion object {
        const val FILE_NAME = "library.json"

        fun load(dir: File): Library? {
            val json = File(dir, FILE_NAME).takeIf { it.exists() }?.readText() ?: return null
            val root = JSONObject(json)

            val tracksJson = root.getJSONArray("tracks")
            val tracks = (0 until tracksJson.length()).map { i ->
                val t = tracksJson.getJSONObject(i)
                val artist = t.optStringOrNull("artist") ?: "不明なアーティスト"
                Track(
                    id = t.getString("id"),
                    name = t.getString("name"),
                    artist = artist,
                    albumArtist = t.optStringOrNull("albumArtist")
                        ?: if (t.optBoolean("compilation")) "Various Artists" else artist,
                    album = t.optStringOrNull("album") ?: "不明なアルバム",
                    trackNumber = t.optInt("trackNumber", 0),
                    discNumber = t.optInt("discNumber", 1),
                    durationMs = t.optLong("durationMs", 0),
                    playCount = t.optInt("playCount", 0),
                    lastPlayed = t.optLongOrNull("lastPlayed"),
                    file = File(dir, t.getString("file")),
                    artwork = t.optStringOrNull("artwork")?.let { File(dir, it) },
                ).let {
                    it.copy(
                        sortName = t.optStringOrNull("sortName") ?: it.name,
                        sortArtist = t.optStringOrNull("sortArtist") ?: it.artist,
                        sortAlbum = t.optStringOrNull("sortAlbum") ?: it.album,
                    )
                }
            }

            val byId = tracks.associateBy { it.id }
            val playlistsJson = root.getJSONArray("playlists")
            val playlists = (0 until playlistsJson.length()).map { i ->
                val p = playlistsJson.getJSONObject(i)
                val ids = p.getJSONArray("trackIds")
                Playlist(
                    p.getString("name"),
                    (0 until ids.length()).mapNotNull { byId[ids.getString(it)] })
            }
            return Library(tracks, playlists)
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).ifBlank { null }

        private fun JSONObject.optLongOrNull(key: String): Long? =
            if (isNull(key)) null else optLong(key)
    }
}
