package com.minagawer.spinback.ui.classic

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** The wheel's "click": a few milliseconds of synthesized tick, played over whatever is playing. */
class Clicker(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    @Volatile
    private var loaded = false
    private val soundId: Int

    init {
        // Rewritten on every start (it's tiny), so a changed sound never loses to a cached file.
        val file = File(context.cacheDir, "click-v1.wav")
        file.writeBytes(synthesizeClick())
        pool.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
        soundId = pool.load(file.path, 1)
    }

    fun click() {
        if (loaded) pool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}

private const val SAMPLE_RATE = 44_100

/** -1 dBFS, leaving headroom for the system's resampling to 48kHz. */
private const val PEAK = 0.891

/** A short decaying burst of a high tone plus noise, normalized to [PEAK], as 16-bit mono WAV. */
private fun synthesizeClick(): ByteArray {
    val count = SAMPLE_RATE * 10 / 1000
    val noise = Random(1)
    val wave = DoubleArray(count) { i ->
        val t = i.toDouble() / SAMPLE_RATE
        (0.6 * sin(2 * PI * 3_500 * t) + 0.4 * (noise.nextDouble() * 2 - 1)) * exp(-t / 0.0016)
    }
    val gain = PEAK * Short.MAX_VALUE / wave.maxOf { abs(it) }
    val samples = ShortArray(count) { (wave[it] * gain).toInt().toShort() }
    val dataSize = count * 2
    return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
        putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(dataSize)
        samples.forEach(::putShort)
    }.array()
}
