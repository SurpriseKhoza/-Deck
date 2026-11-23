package io.surprize.dj

import kotlin.math.*

const val OUT_RATE = 44100

/** RBJ biquad. types: 0 LP, 1 HP, 2 peak, 3 low-shelf, 4 high-shelf, 5 band-pass */
class Biquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
    fun set(type: Int, f: Double, q: Double, db: Double = 0.0) {
        val w = 2 * PI * f / OUT_RATE; val c = cos(w); val al = sin(w) / (2 * q); val A = 10.0.pow(db / 40); val k = 2 * sqrt(A) * al
        val co = when (type) {
            0 -> doubleArrayOf((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al)
            1 -> doubleArrayOf((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
            2 -> doubleArrayOf(1 + al * A, -2 * c, 1 - al * A, 1 + al / A, -2 * c, 1 - al / A)
            3 -> doubleArrayOf(A * ((A + 1) - (A - 1) * c + k), 2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - k),
                (A + 1) + (A - 1) * c + k, -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - k)
            4 -> doubleArrayOf(A * ((A + 1) + (A - 1) * c + k), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - k),
                (A + 1) - (A - 1) * c + k, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - k)
            else -> doubleArrayOf(al, 0.0, -al, 1 + al, -2 * c, 1 - al)
        }
        b0 = co[0] / co[3]; b1 = co[1] / co[3]; b2 = co[2] / co[3]; a1 = co[4] / co[3]; a2 = co[5] / co[3]
    }
    fun p(x: Double): Double { val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2; x2 = x1; x1 = x; y2 = y1; y1 = y; return y }
}

class Filt {
    val l = Biquad(); val r = Biquad()
    fun set(t: Int, f: Double, q: Double, db: Double = 0.0) { l.set(t, f, q, db); r.set(t, f, q, db) }
    fun run(L: FloatArray, R: FloatArray, n: Int) { for (i in 0 until n) { L[i] = l.p(L[i].toDouble()).toFloat(); R[i] = r.p(R[i].toDouble()).toFloat() } }
}

class Comb(size: Int) { private val b = FloatArray(size); private var i = 0; private var s = 0f
    fun p(x: Float, fb: Float, damp: Float): Float { val o = b[i]; s = o * (1 - damp) + s * damp; b[i] = x + s * fb; if (++i >= b.size) i = 0; return o } }
class Allpass(size: Int) { private val b = FloatArray(size); private var i = 0
    fun p(x: Float): Float { val o = b[i]; b[i] = x + o * .5f; if (++i >= b.size) i = 0; return o - x } }

/** Per-deck chain: 3-band EQ -> bi-filter -> one live effect (selected by fx index). */
class DeckDsp {
    @Volatile var eqLo = .5f; @Volatile var eqMid = .5f; @Volatile var eqHi = .5f; @Volatile var eqFlt = .5f
    @Volatile var fx = 0; @Volatile var fxActive = false; @Volatile var fxX = .5f; @Volatile var fxY = .5f
    @Volatile var beat = 22050.0   // samples per beat at the current speed

    private val lo = Filt(); private val mid = Filt(); private val hi = Filt(); private val flt = Filt()
    private var cLo = -9f; private var cMid = -9f; private var cHi = -9f; private var cFlt = -9f; private var fltOn = false
    private var ax = 0f; private var was = false
    private val eL = FloatArray(OUT_RATE * 2); private val eR = FloatArray(OUT_RATE * 2); private var ew = 0; private var curDelay = 22050.0
    private val combs = Array(2) { ch -> listOf(1116, 1188, 1277, 1356).map { Comb(it + ch * 23) } }
    private val aps = Array(2) { ch -> listOf(556, 441).map { Allpass(it + ch * 23) } }
    private val fl = FloatArray(4096); private val fr = FloatArray(4096); private var fw = 0; private var ph = 0.0
    private val apz = Array(2) { FloatArray(4) }; private val phLast = FloatArray(2)
    private val rl = FloatArray(OUT_RATE * 2); private val rr = FloatArray(OUT_RATE * 2); private var rw = 0
    private var rStart = 0; private var rLen = 1; private var rPos = 0; private var rFrozen = false
    private var gatePh = 0.0; private var gateG = 1f
    private var hL = 0f; private var hR = 0f; private var hc = 0
    private val iLpL = Biquad().apply { set(0, 120.0, .7) }; private val iLpR = Biquad().apply { set(0, 120.0, .7) }
    private val vBp = Biquad().apply { set(5, 1200.0, .5) }

    private fun db(v: Float) = if (v < .5f) -40.0 * (1 - 2 * v) else 6.0 * (2 * v - 1)

    private fun updateEq() {
        if (eqLo != cLo) { cLo = eqLo; lo.set(3, 100.0, .7, db(cLo)) }
        if (eqMid != cMid) { cMid = eqMid; mid.set(2, 1000.0, .8, db(cMid)) }
        if (eqHi != cHi) { cHi = eqHi; hi.set(4, 8000.0, .7, db(cHi)) }
        if (eqFlt != cFlt) { cFlt = eqFlt
            if (cFlt < .46f) { fltOn = true; flt.set(0, 20000.0 * 0.0075.pow(((.5f - cFlt) * 2).toDouble()), 1.0) }
            else if (cFlt > .54f) { fltOn = true; flt.set(1, 20.0 * 400.0.pow(((cFlt - .5f) * 2).toDouble()), 1.0) }
            else fltOn = false }
    }

    private fun resetFx() { eL.fill(0f); eR.fill(0f); fl.fill(0f); fr.fill(0f); rl.fill(0f); rr.fill(0f) }

    fun process(L: FloatArray, R: FloatArray, n: Int) {
        updateEq(); lo.run(L, R, n); mid.run(L, R, n); hi.run(L, R, n); if (fltOn) flt.run(L, R, n)
        val act = fxActive; val f = fx
        val target = if (f == 7) 1f else fxX
        val goal = if (act) target else 0f
        ax += (goal - ax) * (if (goal > ax) .3f else if (f in 1..4) .03f else .3f)
        if (act && !was && ax < .02f) resetFx()
        was = act
        if (f <= 0 || (!act && ax < .004f)) { rFrozen = false; return }
        val y = fxY
        when (f) {
            1 -> echo(L, R, n, y); 2 -> reverb(L, R, n, y); 3 -> flanger(L, R, n, y); 4 -> phaser(L, R, n, y)
            5 -> crush(L, R, n, y); 6 -> gate(L, R, n, y); 7 -> roll(L, R, n, y, act)
            8 -> for (i in 0 until n) { val m = (L[i] + R[i]) / 2; val s = (L[i] - R[i]) / 2 * (1 + ax * 3); L[i] = m + s; R[i] = m - s }
            9 -> for (i in 0 until n) { val lowL = iLpL.p(L[i].toDouble()).toFloat(); val lowR = iLpR.p(R[i].toDouble()).toFloat()
                var a = L[i] - lowL; var b = R[i] - lowR; val m = (a + b) / 2; a -= ax * m; b -= ax * m; L[i] = lowL + a; R[i] = lowR + b }
            10 -> for (i in 0 until n) { val m = vBp.p(((L[i] + R[i]) / 2).toDouble()).toFloat() * 2f
                L[i] = L[i] * (1 - ax) + m * ax; R[i] = R[i] * (1 - ax) + m * ax }
        }
    }

    private val echoDiv = doubleArrayOf(.25, .5, .75, 1.0, 1.5)
    private fun echo(L: FloatArray, R: FloatArray, n: Int, y: Float) {
        val tgt = (beat * echoDiv[min(4, (y * 5).toInt())]).coerceIn(300.0, eL.size - 2.0); val sz = eL.size
        for (i in 0 until n) {
            curDelay += (tgt - curDelay) * .0004
            var rp = ew - curDelay; if (rp < 0) rp += sz
            val i0 = rp.toInt(); val f = (rp - i0).toFloat(); val i1 = if (i0 + 1 >= sz) 0 else i0 + 1
            val yl = eL[i0] + (eL[i1] - eL[i0]) * f; val yr = eR[i0] + (eR[i1] - eR[i0]) * f
            val il = L[i]; val ir = R[i]
            eL[ew] = il + yl * .55f; eR[ew] = ir + yr * .55f
            L[i] = il + yl * ax; R[i] = ir + yr * ax
            if (++ew >= sz) ew = 0
        }
    }

    private fun reverb(L: FloatArray, R: FloatArray, n: Int, y: Float) {
        val fb = .7f + .28f * y
        for (i in 0 until n) { val inp = (L[i] + R[i]) * .015f; var sl = 0f; var sr = 0f
            for (c in combs[0]) sl += c.p(inp, fb, .35f); for (c in combs[1]) sr += c.p(inp, fb, .35f)
            for (a in aps[0]) sl = a.p(sl); for (a in aps[1]) sr = a.p(sr)
            L[i] += sl * 3f * ax; R[i] += sr * 3f * ax }
    }

    private fun flanger(L: FloatArray, R: FloatArray, n: Int, y: Float) {
        val inc = 2 * PI * (.1 + y * 2.0) / OUT_RATE; val sz = fl.size
        for (i in 0 until n) { ph += inc; val d = (1.0 + 4.0 * (.5 + .5 * sin(ph))) * OUT_RATE / 1000.0
            var rp = fw - d; if (rp < 0) rp += sz; val i0 = rp.toInt(); val f = (rp - i0).toFloat(); val i1 = if (i0 + 1 >= sz) 0 else i0 + 1
            val dl = fl[i0] + (fl[i1] - fl[i0]) * f; val dr = fr[i0] + (fr[i1] - fr[i0]) * f
            fl[fw] = L[i] + dl * .5f; fr[fw] = R[i] + dr * .5f
            L[i] += dl * ax * .8f; R[i] += dr * ax * .8f
            if (++fw >= sz) fw = 0 }
    }

    private fun phaser(L: FloatArray, R: FloatArray, n: Int, y: Float) {
        val inc = 2 * PI * (.2 + y * 3.0) / OUT_RATE
        for (i in 0 until n) { ph += inc; val fq = 300.0 * 2.0.pow(3.3 * (.5 + .5 * sin(ph)))
            val t = tan(PI * fq / OUT_RATE); val a = ((t - 1) / (t + 1)).toFloat()
            for (ch in 0..1) { val inp = if (ch == 0) L[i] else R[i]; var x = inp + phLast[ch] * .4f
                for (s in 0 until 4) { val o = a * x + apz[ch][s]; apz[ch][s] = x - a * o; x = o }
                phLast[ch] = x; val o = inp * (1 - .5f * ax) + x * .5f * ax
                if (ch == 0) L[i] = o else R[i] = o } }
    }

    private fun crush(L: FloatArray, R: FloatArray, n: Int, y: Float) {
        val bits = 16 - (ax * 11).toInt(); val lv = 2.0.pow(bits - 1.0).toFloat(); val k = 1 + (y * 20).toInt()
        for (i in 0 until n) { if (hc % k == 0) { hL = round(L[i] * lv) / lv; hR = round(R[i] * lv) / lv }; hc++; L[i] = hL; R[i] = hR }
    }

    private val gateDiv = doubleArrayOf(.25, .125, .0625)
    private fun gate(L: FloatArray, R: FloatArray, n: Int, y: Float) {
        val inc = 1.0 / (beat * gateDiv[min(2, (y * 3).toInt())]).coerceAtLeast(64.0)
        for (i in 0 until n) { gatePh += inc; if (gatePh >= 1.0) gatePh -= 1.0
            gateG += ((if (gatePh < .5) 1f else 1f - ax) - gateG) * .02f; L[i] *= gateG; R[i] *= gateG }
    }

    private val rollDiv = doubleArrayOf(.0625, .125, .25, .5, 1.0)
    private fun roll(L: FloatArray, R: FloatArray, n: Int, y: Float, act: Boolean) {
        val sz = rl.size
        if (act && !rFrozen) { rLen = max(256, (beat * rollDiv[min(4, (y * 5).toInt())]).toInt()).coerceAtMost(sz - 1)
            rStart = ((rw - rLen) % sz + sz) % sz; rPos = 0; rFrozen = true }
        if (!act && ax < .02f) rFrozen = false
        for (i in 0 until n) {
            if (rFrozen) { val idx = (rStart + rPos) % sz; if (++rPos >= rLen) rPos = 0
                L[i] = L[i] * (1 - ax) + rl[idx] * ax; R[i] = R[i] * (1 - ax) + rr[idx] * ax
            } else { rl[rw] = L[i]; rr[rw] = R[i]; if (++rw >= sz) rw = 0 }
        }
    }
}
