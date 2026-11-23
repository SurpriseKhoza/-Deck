package io.surprize.dj

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

/** Decoded audio: interleaved stereo 16-bit. */
class Pcm(val d: ShortArray, val frames: Int, val rate: Int)

class DeckVoice {
    @Volatile var pcm: Pcm? = null
    @Volatile var playing = false
    @Volatile var pos = 0.0            // source frames
    @Volatile var speed = 1.0
    @Volatile var reverse = false
    @Volatile var loopOn = false; @Volatile var loopS = 0.0; @Volatile var loopE = 0.0
    @Volatile var vol = 1f
    @Volatile var scratch = false; @Volatile var scratchTarget = 0.0
    @Volatile var onStop: (() -> Unit)? = null
    private var brakeK = -1.0
    private var step = 0.0
    val dsp = DeckDsp()

    fun seek(frame: Double) { pos = frame.coerceIn(0.0, max(0, (pcm?.frames ?: 1) - 1).toDouble()) }
    /** Slow down to a stop over about [sec] seconds (vinyl brake). */
    fun brake(sec: Double) { if (playing) brakeK = 3.0 / (sec * OUT_RATE) }
    /** Reverse spin-back, then stop. */
    fun spinBack() { if (playing) { step = -3.0 * (pcm?.rate ?: OUT_RATE) / OUT_RATE; brakeK = 3.0 / (.8 * OUT_RATE) } }

    fun render(L: FloatArray, R: FloatArray, n: Int) {
        val p = pcm
        if (p == null || p.frames < 2) { L.fill(0f, 0, n); R.fill(0f, 0, n); return }
        val d = p.d; val ratio = p.rate.toDouble() / OUT_RATE; val maxF = (p.frames - 1).toDouble()
        var s = step; var ps = pos
        for (i in 0 until n) {
            val tgt: Double; val k: Double
            if (scratch) { tgt = ((scratchTarget - ps) * .02).coerceIn(-10.0, 10.0); k = .2 }
            else if (brakeK > 0) { tgt = 0.0; k = brakeK }
            else if (playing) { tgt = ratio * speed * (if (reverse) -1.0 else 1.0); k = .0015 }
            else { tgt = 0.0; k = .004 }
            s += (tgt - s) * k; ps += s
            if (brakeK > 0 && abs(s) < .01) { brakeK = -1.0; s = 0.0; playing = false; onStop?.invoke() }
            if (loopOn && !scratch) { if (s > 0 && ps >= loopE) ps -= (loopE - loopS) else if (s < 0 && ps < loopS) ps += (loopE - loopS) }
            if (ps >= maxF) { ps = maxF; if (s > 0) { s = 0.0; if (playing) { playing = false; onStop?.invoke() } } }
            if (ps < 0) { ps = 0.0; if (s < 0) { s = 0.0; if (playing && reverse) { playing = false; onStop?.invoke() } } }
            val i0 = ps.toInt(); val fr = (ps - i0).toFloat(); val i1 = if (i0 + 1 < p.frames) i0 + 1 else i0
            L[i] = (d[2 * i0] + (d[2 * i1] - d[2 * i0]) * fr) / 32768f
            R[i] = (d[2 * i0 + 1] + (d[2 * i1 + 1] - d[2 * i0 + 1]) * fr) / 32768f
        }
        step = s; pos = ps
        dsp.process(L, R, n)
    }
}

class WavWriter(f: java.io.File) {
    private val raf = java.io.RandomAccessFile(f, "rw"); private var bytes = 0L
    private val buf = ByteArray(65536); private var bn = 0; private var closed = false
    init { raf.setLength(0); raf.write(ByteArray(44)) }
    @Synchronized fun write(s: ShortArray, n: Int) {
        if (closed) return
        for (i in 0 until n) { if (bn + 2 > buf.size) flush(); buf[bn++] = s[i].toByte(); buf[bn++] = (s[i].toInt() shr 8).toByte() }
    }
    private fun flush() { raf.write(buf, 0, bn); bytes += bn; bn = 0 }
    @Synchronized fun close() {
        if (closed) return; closed = true; flush(); val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt((36 + bytes).toInt()).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(2)
            .putInt(OUT_RATE).putInt(OUT_RATE * 4).putShort(4).putShort(16).put("data".toByteArray()).putInt(bytes.toInt())
        raf.seek(0); raf.write(h.array()); raf.close()
    }
}

/** 8 built-in pad sounds, synthesised in code — no sample files, no downloads. */
class Sampler {
    val names = listOf("KICK", "SNARE", "HAT", "CLAP", "HORN", "SIREN", "LASER", "RISER")
    @Volatile var vol = .9f
    val data = Array(8) { synth(it) }
    private val q = ConcurrentLinkedQueue<Int>(); private val idx = IntArray(16) { -1 }; private val ps = IntArray(16)
    fun trigger(i: Int) { q.add(i) }
    fun mix(L: FloatArray, R: FloatArray, n: Int) {
        while (true) { val t = q.poll() ?: break; for (k in idx.indices) if (idx[k] < 0) { idx[k] = t; ps[k] = 0; break } }
        for (k in idx.indices) { if (idx[k] < 0) continue
            val s = data[idx[k]]; var p = ps[k]; var i = 0
            while (i < n && p < s.size) { L[i] += s[p] * vol; R[i] += s[p] * vol; p++; i++ }
            ps[k] = p; if (p >= s.size) idx[k] = -1 }
    }
    private fun synth(k: Int): FloatArray {
        val rnd = java.util.Random(7L + k); val sr = OUT_RATE.toDouble()
        fun nz() = rnd.nextFloat() * 2 - 1
        return when (k) {
            0 -> { var ph = 0.0; FloatArray((.4 * sr).toInt()) { i -> val t = i / sr; ph += 2 * PI * (45 + 105 * exp(-t * 18)) / sr; (sin(ph) * exp(-t * 7)).toFloat() } }
            1 -> FloatArray((.25 * sr).toInt()) { i -> val t = i / sr; (nz() * exp(-t * 18) * .6 + sin(2 * PI * 190 * t) * exp(-t * 22) * .5).toFloat() }
            2 -> { var last = 0f; FloatArray((.1 * sr).toInt()) { i -> val t = i / sr; val x = nz(); val o = (x - last) * exp(-t * 55).toFloat() * .7f; last = x; o } }
            3 -> FloatArray((.25 * sr).toInt()) { i -> val t = i / sr
                val b = listOf(0.0, .012, .024).sumOf { t0 -> if (t >= t0) exp(-(t - t0) * 60) else 0.0 }
                (nz() * (b * .5 + exp(-t * 18) * .4)).toFloat() }
            4 -> FloatArray((.9 * sr).toInt()) { i -> val t = i / sr
                var v = 0.0; for (h in 1..6) v += (sin(2 * PI * h * 466 * t) + sin(2 * PI * h * 622 * t)) / h
                (v * .18 * min(1.0, t * 100) * min(1.0, (.9 - t) * 10) * (.85 + .15 * sin(2 * PI * 9 * t))).toFloat() }
            5 -> { var ph = 0.0; FloatArray((1.5 * sr).toInt()) { i -> val t = i / sr; ph += 2 * PI * (800 + 400 * sin(2 * PI * 1.2 * t)) / sr
                (sin(ph) * .5 * min(1.0, (1.5 - t) * 4)).toFloat() } }
            6 -> { var ph = 0.0; FloatArray((.35 * sr).toInt()) { i -> val t = i / sr; ph += 2 * PI * (150 + 3000 * exp(-t * 9)) / sr; (sin(ph) * exp(-t * 4) * .6).toFloat() } }
            else -> { var y = 0f; FloatArray((2 * sr).toInt()) { i -> val t = i / sr; val a = (t / 2).toFloat()
                y += (nz() - y) * (.02f + .9f * a); y * a * a * 1.2f } }
        }
    }
}

/** Both decks + crossfader + sampler -> soft-limited 16-bit stereo. Called from the audio thread. */
class Master {
    companion object { const val BLOCK = 512 }
    val v = arrayOf(DeckVoice(), DeckVoice())
    val sampler = Sampler()
    @Volatile var cf = .5f
    @Volatile var tap: WavWriter? = null
    private val bl = Array(2) { FloatArray(BLOCK) }; private val br = Array(2) { FloatArray(BLOCK) }
    private val ml = FloatArray(BLOCK); private val mr = FloatArray(BLOCK); private val prev = floatArrayOf(0f, 0f)

    private fun clip(x: Float): Float { val a = abs(x); return if (a < .8f) x else sign(x) * (.8f + .2f * tanh((a - .8f) / .2f)) }

    fun render(out: ShortArray, frames: Int) {
        var off = 0
        while (off < frames) {
            val n = min(BLOCK, frames - off)
            for (d in 0..1) v[d].render(bl[d], br[d], n)
            val g = floatArrayOf(min(1f, 2f * (1f - cf)) * v[0].vol, min(1f, 2f * cf) * v[1].vol)
            for (i in 0 until n) { val t = (i + 1f) / n
                val g0 = prev[0] + (g[0] - prev[0]) * t; val g1 = prev[1] + (g[1] - prev[1]) * t
                ml[i] = bl[0][i] * g0 + bl[1][i] * g1; mr[i] = br[0][i] * g0 + br[1][i] * g1 }
            prev[0] = g[0]; prev[1] = g[1]
            sampler.mix(ml, mr, n)
            for (i in 0 until n) { out[2 * (off + i)] = (clip(ml[i]) * 32767f).toInt().toShort(); out[2 * (off + i) + 1] = (clip(mr[i]) * 32767f).toInt().toShort() }
            off += n
        }
        tap?.write(out, frames * 2)
    }
}
