package com.husarp.ankigo.sound

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

/**
 * Measures a cached cry file (decode to 16-bit PCM, peak + RMS through [CryLevel.Stats]) and keeps the gain per species in its own
 * preferences file, under keys with the [CryLevel.VERSION] in them: a new version measures again, old keys are left alone.
 * Blocking: call it from the sound thread only.
 */
object CryDecoder {
    private const val MAX_SECONDS = 6 // cries are 0.4 to 2 s; a longer file is cut here
    private const val TIMEOUT_US = 10_000L

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("ankigo_cry_gain", Context.MODE_PRIVATE)
    private fun key(num: Int) = "v${CryLevel.VERSION}_$num"

    /** The stored gain of [num], or null when it was not measured yet. */
    fun stored(ctx: Context, num: Int): Float? {
        val p = prefs(ctx)
        val k = key(num)
        return if (p.contains(k)) p.getFloat(k, CryLevel.DEFAULT_GAIN) else null
    }

    /** Measures [file] and stores the gain of [num]; returns it, or null when the file cannot be decoded (nothing is stored then). */
    fun measureAndStore(ctx: Context, num: Int, file: File): Float? {
        val g = gainOf(file) ?: return null
        prefs(ctx).edit().putFloat(key(num), g).apply()
        return g
    }

    /** Decodes [file] and returns its gain, or null when it cannot be decoded or holds no sound. */
    fun gainOf(file: File): Float? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(file.path)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; format = f; break }
            }
            if (track < 0 || format == null) return null
            ex.selectTrack(track)
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            val stats = CryLevel.Stats()
            val info = MediaCodec.BufferInfo()
            val rate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 48_000
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            val limit = MAX_SECONDS.toLong() * rate * channels // samples in total
            var seen = 0L
            var inDone = false
            var outDone = false
            var idle = 0
            while (!outDone && seen < limit && idle < 500) {
                var progressed = false
                if (!inDone) {
                    val ib = c.dequeueInputBuffer(TIMEOUT_US)
                    if (ib >= 0) {
                        val buf = c.getInputBuffer(ib)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { c.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { c.queueInputBuffer(ib, 0, n, ex.sampleTime, 0); ex.advance() }
                        progressed = true
                    }
                }
                val ob = c.dequeueOutputBuffer(info, TIMEOUT_US)
                if (ob >= 0) {
                    if (info.size > 0) {
                        val sb = c.getOutputBuffer(ob)!!.order(ByteOrder.nativeOrder()).asShortBuffer()
                        sb.limit(info.size / 2)
                        val arr = ShortArray(sb.remaining())
                        sb.get(arr)
                        stats.add(arr)
                        seen += arr.size
                    }
                    c.releaseOutputBuffer(ob, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    progressed = true
                } else if (ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED || ob == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) progressed = true
                idle = if (progressed) 0 else idle + 1
            }
            if (stats.rmsLin <= 0.0) return null
            return stats.gain()
        } catch (_: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Throwable) { }
            try { codec?.release() } catch (_: Throwable) { }
            try { ex.release() } catch (_: Throwable) { }
        }
    }
}
