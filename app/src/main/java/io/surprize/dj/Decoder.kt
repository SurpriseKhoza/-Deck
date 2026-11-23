package io.surprize.dj

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Decodes any Android-supported audio file (mp3, aac, flac, wav, ogg…) into stereo 16-bit PCM in memory. 100% on-device. */
object Decoder {
    fun decode(ctx: Context, uri: Uri, alive: () -> Boolean = { true }): Pcm? {
        val ex = MediaExtractor()
        try { ex.setDataSource(ctx, uri, null) } catch (_: Exception) { ex.release(); return null }
        var ti = -1
        for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio") == true) { ti = i; break }
        if (ti < 0) { ex.release(); return null }
        ex.selectTrack(ti)
        val fmt = ex.getTrackFormat(ti); val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        var buf = ShortArray(2 * maxOf((durUs * rate / 1_000_000L).toInt() + rate, rate * 10))
        var frames = 0
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(fmt, null, null, 0); codec.start()
            val info = MediaCodec.BufferInfo(); var inDone = false; var outDone = false
            while (!outDone) {
                if (!alive()) return null
                if (!inDone) { val ii = codec.dequeueInputBuffer(5000)
                    if (ii >= 0) { val sz = ex.readSampleData(codec.getInputBuffer(ii)!!, 0)
                        if (sz < 0) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(ii, 0, sz, ex.sampleTime, 0); ex.advance() } } }
                val oi = codec.dequeueOutputBuffer(info, 5000)
                if (oi >= 0) {
                    val bb = codec.getOutputBuffer(oi)!!; bb.position(info.offset); bb.limit(info.offset + info.size)
                    val sb = bb.order(ByteOrder.nativeOrder()).asShortBuffer(); val total = sb.remaining() / ch
                    if ((frames + total) * 2 > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, (frames + total) * 2))
                    for (f in 0 until total) { val l = sb.get(f * ch); val r = if (ch > 1) sb.get(f * ch + 1) else l
                        buf[2 * frames] = l; buf[2 * frames + 1] = r; frames++ }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat; rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1) }
            }
        } catch (_: Exception) { return null
        } finally { try { codec.stop() } catch (_: Exception) {}; codec.release(); ex.release() }
        return if (frames > rate / 2) Pcm(buf, frames, rate) else null
    }
}
