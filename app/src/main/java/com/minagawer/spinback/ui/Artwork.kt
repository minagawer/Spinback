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
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** Album art from the iTunes artwork cache or the audio file, with a stack of blank cases as a fallback. */
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

private val NoArtworkBackground =
    Brush.verticalGradient(listOf(Color(0xFFEEEFF1), Color(0xFFDDDFE2)))
private val CaseBack = Color(0xFFD8DADD)
private val CaseBackEdge = Color(0xFFC6C8CC)
private val CaseFront = Color(0xFFE9EAEC)
private val CaseMark = Color(0xFF8E9196)

/** The iPod's stand-in for missing art: faint blank CD cases, the front one marked with a note. */
@Composable
private fun NoArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier.background(NoArtworkBackground)) {
        val s = size.width
        fun box(l: Float, t: Float, r: Float, b: Float) =
            Offset(l * s, t * s) to Size((r - l) * s, (b - t) * s)

        val (backAt, backSize) = box(0.14f, 0.13f, 0.61f, 0.60f)
        drawRect(CaseBack, backAt, backSize)
        drawRect(CaseBackEdge, backAt, backSize, style = Stroke(maxOf(1f, s * 0.012f)))

        val (frontAt, frontSize) = box(0.37f, 0.27f, 0.86f, 0.76f)
        drawRect(CaseFront, frontAt, frontSize)
        val edge = maxOf(1.5f, s * 0.025f)
        drawRect(
            CaseMark,
            frontAt + Offset(edge / 2, edge / 2),
            Size(frontSize.width - edge, frontSize.height - edge),
            style = Stroke(edge),
        )

        // A small eighth note: head, stem, and a flag curling down to the right.
        val stemRight = 0.665f * s
        val stemW = 0.024f * s
        val top = 0.35f * s
        val foot = 0.63f * s
        rotate(-20f, pivot = Offset(stemRight - 0.04f * s, foot)) {
            drawOval(
                CaseMark,
                Offset(stemRight - 0.085f * s, foot - 0.03f * s),
                Size(0.085f * s, 0.06f * s)
            )
        }
        drawRect(CaseMark, Offset(stemRight - stemW, top), Size(stemW, foot - top))
        val flag = Path().apply {
            moveTo(stemRight - stemW, top)
            cubicTo(0.70f * s, 0.40f * s, 0.745f * s, 0.43f * s, 0.725f * s, 0.51f * s)
            cubicTo(0.72f * s, 0.46f * s, 0.69f * s, 0.43f * s, stemRight, 0.425f * s)
            close()
        }
        drawPath(flag, CaseMark)
    }
}
