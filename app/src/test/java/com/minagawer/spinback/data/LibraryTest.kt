package com.minagawer.spinback.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun load(json: String): Library? {
        File(folder.root, Library.FILE_NAME).writeText(json)
        return Library.load(folder.root, unknownArtist = "?artist", unknownAlbum = "?album")
    }

    private fun track(id: String, name: String, extra: String = "") =
        """{"id": "$id", "name": "$name", "file": "music/$id.m4a"$extra}"""

    private fun library(vararg tracks: String, playlists: String = "[]") =
        load("""{"tracks": [${tracks.joinToString()}], "playlists": $playlists}""")!!

    @Test
    fun noFileMeansNoLibraryYet() {
        assertNull(Library.load(folder.root, "?artist", "?album"))
    }

    @Test
    fun missingNamesFallBack() {
        val t = library(track("a", "Song")).tracks.single()
        assertEquals("?artist", t.artist)
        assertEquals("?artist", t.albumArtist)
        assertEquals("?album", t.album)
        assertEquals(1, t.discNumber)
        assertEquals(File(folder.root, "music/a.m4a"), t.file)
    }

    @Test
    fun compilationsGoUnderVariousArtists() {
        val t = library(
            track(
                "a",
                "Song",
                """, "artist": "Alpha", "compilation": true"""
            )
        ).tracks.single()
        assertEquals("Alpha", t.artist)
        assertEquals("Various Artists", t.albumArtist)
    }

    @Test
    fun sortNamesDefaultToTheShownOnes() {
        val tracks = library(
            track("a", "The Song", """, "artist": "The Band", "album": "LP", "sortName": "Song""""),
            track("b", "Other"),
        ).tracks
        assertEquals("Song", tracks[0].sortName)
        assertEquals("The Band", tracks[0].sortArtist)
        assertEquals("LP", tracks[0].sortAlbum)
        assertEquals("Other", tracks[1].sortName)
    }

    @Test
    fun albumsHoldTheirTracksInDiscAndTrackOrder() {
        val album = """, "artist": "Alpha", "album": "LP""""
        val lib = library(
            track("c", "Third", "$album, \"discNumber\": 2, \"trackNumber\": 1"),
            track("b", "Second", "$album, \"discNumber\": 1, \"trackNumber\": 2"),
            track("a", "First", "$album, \"discNumber\": 1, \"trackNumber\": 1"),
        )
        assertEquals(listOf("First", "Second", "Third"), lib.albums.single().tracks.map { it.name })
        assertEquals(
            listOf("First", "Second", "Third"),
            lib.artists.single().tracks.map { it.name })
    }

    @Test
    fun mostPlayedSkipsUnplayedAndBreaksTiesByLastPlayed() {
        val lib = library(
            track("a", "Never", """, "playCount": 0"""),
            track("b", "Older", """, "playCount": 5, "lastPlayed": 100"""),
            track("c", "Newer", """, "playCount": 5, "lastPlayed": 200"""),
            track("d", "Most", """, "playCount": 9"""),
        )
        assertEquals(listOf("Most", "Newer", "Older"), lib.mostPlayed.map { it.name })
    }

    @Test
    fun playlistsSkipTracksThatWerentExported() {
        val lib = library(
            track("a", "Kept"),
            playlists = """[{"name": "Mix", "trackIds": ["a", "gone"]}]""",
        )
        assertEquals(listOf("Kept"), lib.playlists.single().tracks.map { it.name })
    }
}
