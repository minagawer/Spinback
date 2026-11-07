package com.minagawer.spinback.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minagawer.spinback.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ArtworkCache {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val missing = mutableSetOf<String>()

    // Tracks of an album share one artwork file, so keying by it shares the bitmap too.
    fun key(track: Track, sizePx: Int) = "${(track.artwork ?: track.file).path}@$sizePx"

    fun cached(key: String): Bitmap? = cache.get(key)

    fun load(track: Track, sizePx: Int): Bitmap? {
        val key = key(track, sizePx)
        cache.get(key)?.let { return it }
        synchronized(missing) { if (key in missing) return null }

        val bytes = runCatching {
            track.artwork?.takeIf { it.exists() }?.readBytes()
                ?: MediaMetadataRetriever().use { it.setDataSource(track.file.path); it.embeddedPicture }
        }.getOrNull()
        val bitmap = bytes?.let { decode(it, sizePx) }
        if (bitmap != null) cache.put(key, bitmap) else synchronized(missing) { missing += key }
        return bitmap
    }

    private fun decode(bytes: ByteArray, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/** Album art from the iTunes artwork cache or the audio file, with music notes as a fallback. */
@Composable
fun Artwork(track: Track, size: Dp, modifier: Modifier = Modifier, shape: Shape? = null) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val key = ArtworkCache.key(track, sizePx)
    val bitmap by produceState(initialValue = ArtworkCache.cached(key), key) {
        // The state outlives a change of track, so drop the previous cover before loading this one.
        value = ArtworkCache.cached(key)
        if (value == null) {
            value = withContext(Dispatchers.IO) { ArtworkCache.load(track, sizePx) }
        }
    }
    Box(
        modifier
            .size(size)
            .clip(shape ?: RoundedCornerShape(if (size > 100.dp) 12.dp else 4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                image.asImageBitmap(),
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            NoArtwork(Modifier.fillMaxSize())
        }
    }
}

// A blue-grey square, lighter at the top, with a faint sheen across its upper left.
private val NoArtworkBackground = Brush.verticalGradient(
    listOf(Color(0xFF9CA6B8), Color(0xFF7F8BA6), Color(0xFF6E7C9C)),
)
private val NoArtworkSheen = Color.White.copy(alpha = 0.07f)

/** The iPod's stand-in for missing art: big white beamed notes on blue-grey. */
@Composable
private fun NoArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier.background(NoArtworkBackground)) {
        val s = size.width
        val sheen = Path().apply {
            moveTo(0f, 0f)
            lineTo(s, 0f)
            lineTo(s, 0.35f * s)
            lineTo(0f, 0.75f * s)
            close()
        }
        drawPath(sheen, NoArtworkSheen)
        drawBeamedNotes(
            Brush.verticalGradient(
                listOf(Color.White, Color(0xFFDCE1E8)),
                startY = 0.1f * s,
                endY = 0.85f * s,
            ),
        )
    }
}

/**
 * A pair of beamed eighth notes filling a square canvas, traced from the iPod's own missing-art
 * square: a broad beam rising to the right over two slim stems, with wide, tilted heads. Shared by
 * the missing-art square and the all-songs tile.
 */
internal fun DrawScope.drawBeamedNotes(brush: Brush) {
    // Laid out on a 24-unit grid, then scaled to the canvas.
    val u = size.width / 24f
    val beamAndStems = Path().apply {
        moveTo(8f * u, 4.5f * u)
        lineTo(18f * u, 2.4f * u)
        lineTo(18f * u, 16.4f * u)
        lineTo(16.7f * u, 16.4f * u)
        lineTo(16.7f * u, 7.25f * u)
        lineTo(9.3f * u, 8.75f * u)
        lineTo(9.3f * u, 17.7f * u)
        lineTo(8f * u, 17.7f * u)
        close()
    }
    drawPath(beamAndStems, brush)
    // The heads sit at the foot of each stem, flush with its right side.
    val head = Size(5.5f * u, 4.4f * u)
    listOf(Offset(6.6f * u, 17.7f * u), Offset(15.4f * u, 16.4f * u)).forEach { c ->
        rotate(-20f, pivot = c) {
            drawOval(brush, Offset(c.x - head.width / 2, c.y - head.height / 2), head)
        }
    }
}
