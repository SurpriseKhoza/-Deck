package io.surprize.dj

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Environment
import android.os.Process
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One low-latency AudioTrack fed by [Master] from a dedicated high-priority thread. */
class AudioOut(private val master: Master) {
    @Volatile private var run = false
    fun start() {
        if (run) return; run = true
        val min = AudioTrack.getMinBufferSize(OUT_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val b = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(OUT_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(maxOf(min, Master.BLOCK * 4 * 3)).setTransferMode(AudioTrack.MODE_STREAM)
        if (Build.VERSION.SDK_INT >= 26) b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        val t = b.build(); t.play()
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val out = ShortArray(Master.BLOCK * 2)
            while (run) { master.render(out, Master.BLOCK); t.write(out, 0, out.size) }
            t.stop(); t.release()
        }.apply { name = "deck-audio"; start() }
    }
    fun stop() { run = false }
}

object Audio { val master = Master(); val out = AudioOut(master) }

/** Records the master output to a 16-bit WAV in the app's Music folder. */
object Recorder {
    var file: File? = null
    fun toggle(ctx: Context): Boolean {
        val m = Audio.master; val w = m.tap
        if (w != null) { m.tap = null; w.close(); return false }
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir
        val f = File(dir, "Deck-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".wav")
        file = f; m.tap = WavWriter(f); return true
    }
}
