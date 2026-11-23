package io.surprize.dj

import kotlin.math.*

/** Pure-Kotlin track analysis: waveform peaks, BPM, musical key (Camelot), auto-gain. */
object Analysis {
    class Result(val peaks: FloatArray, val bpm: Float, val key: String, val gain: Float)

    fun fromPcm(p: Pcm): Result? {
        val hop = p.rate / 200; val len = p.frames / hop; if (len < 800) return null
        val env = FloatArray(len)
        for (e in 0 until len) { var acc = 0.0; for (j in 0 until hop) { val f = e * hop + j; val m = (p.d[2 * f] + p.d[2 * f + 1]) / 65536.0; acc += m * m }; env[e] = sqrt(acc / hop).toFloat() }
        val bins = 500; val peaks = FloatArray(bins)
        for (b in 0 until bins) { var m = 0f; for (i in b * len / bins until (b + 1) * len / bins) m = max(m, env[i]); peaks[b] = m }
        val mx = peaks.max().coerceAtLeast(1e-6f); for (i in peaks.indices) peaks[i] = sqrt(peaks[i] / mx)
        var ms = 0.0; for (x in env) ms += x * x; val rms = sqrt(ms / len).toFloat().coerceAtLeast(1e-4f)
        return Result(peaks, bpm(env), key(p), (.12f / rms).coerceIn(.5f, 2f))
    }

    fun bpm(env: FloatArray): Float {
        val len = env.size; val o = FloatArray(len) { if (it == 0) 0f else max(0f, env[it] - env[it - 1]) }
        val maxLag = min(344, len / 2); val ac = FloatArray(maxLag + 2)
        for (l in 66..maxLag) { var s = 0f; var i = l; while (i < len) { s += o[i] * o[i - l]; i++ }; ac[l] = s / (len - l) }
        var best = 66; var bs = -1f
        for (l in 66..min(172, maxLag)) { val sc = ac[l] + if (2 * l <= maxLag) .5f * ac[2 * l] else 0f; if (sc > bs) { bs = sc; best = l } }
        val a0 = ac[best - 1]; val a1 = ac[best]; val a2 = ac[best + 1]; val den = a0 - 2 * a1 + a2
        val lag = if (den != 0f) best + .5f * (a0 - a2) / den else best.toFloat()
        return (12000f / lag * 10).roundToInt() / 10f
    }

    private val major = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val minor = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
    private val camMaj = listOf("8B", "3B", "10B", "5B", "12B", "7B", "2B", "9B", "4B", "11B", "6B", "1B")
    private val camMin = listOf("5A", "12A", "7A", "2A", "9A", "4A", "11A", "6A", "1A", "8A", "3A", "10A")

    /** Chroma (Goertzel on 60 semitones) over up to ~60 s from the middle of the track, matched to Krumhansl profiles. */
    fun key(p: Pcm): String {
        val dec = max(1, p.rate / 11025); val r = p.rate / dec; val total = p.frames / dec
        val win = 8192; if (total < win * 4) return ""
        val chroma = DoubleArray(12); val x = DoubleArray(win)
        val start = (total * .2).toInt().coerceAtMost(total - win - 1); val hopW = r; var w = 0
        val freqs = DoubleArray(60) { 440.0 * 2.0.pow((36 + it - 69) / 12.0) }; val coef = DoubleArray(60) { 2 * cos(2 * PI * freqs[it] / r) }
        var pos = start
        while (pos + win < total && w < 60) {
            for (i in 0 until win) { var a = 0.0; for (j in 0 until dec) { val f = (pos + i) * dec + j; a += (p.d[2 * f] + p.d[2 * f + 1]) / 65536.0 }
                x[i] = a / dec * (.5 - .5 * cos(2 * PI * i / win)) }
            for (k in 0 until 60) { var s1 = 0.0; var s2 = 0.0; val c = coef[k]
                for (i in 0 until win) { val s0 = x[i] + c * s1 - s2; s2 = s1; s1 = s0 }
                chroma[(36 + k) % 12] += sqrt(max(0.0, s1 * s1 + s2 * s2 - c * s1 * s2)) }
            pos += hopW; w++
        }
        fun corr(prof: DoubleArray, t: Int): Double { val a = DoubleArray(12) { chroma[(it + t) % 12] }; val ma = a.average(); val mb = prof.average()
            var n = 0.0; var da = 0.0; var db = 0.0; for (i in 0 until 12) { n += (a[i] - ma) * (prof[i] - mb); da += (a[i] - ma).pow(2); db += (prof[i] - mb).pow(2) }
            return n / sqrt(da * db + 1e-12) }
        var best = -2.0; var name = ""
        for (t in 0 until 12) { val a = corr(major, t); if (a > best) { best = a; name = camMaj[t] }; val b = corr(minor, t); if (b > best) { best = b; name = camMin[t] } }
        return name
    }
}
