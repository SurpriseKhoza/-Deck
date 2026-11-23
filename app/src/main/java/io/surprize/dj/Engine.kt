package io.surprize.dj

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

enum class Mode { PLATTER, PADS, MIXER, FX }
val FX_NAMES = listOf("Bi-Filter", "Echo", "Reverb", "Flanger", "Phaser", "Bitcrush", "Trance Gate", "Roll", "Widen", "Instrumental", "Vocal Iso")

/** UI-facing deck state; all audio work happens in [DeckVoice] on the audio thread. */
class Deck(val color: Color, val idx: Int) {
    val voice = Audio.master.v[idx]
    var title by mutableStateOf("Tap + to load")
    var loading by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var tempo by mutableFloatStateOf(0f); var range by mutableFloatStateOf(8f)
    var vol by mutableFloatStateOf(1f); var gain = 1f
    var autoGainOn by mutableStateOf(true); private var autoGainV = 1f
    val eq = mutableStateListOf(.5f, .5f, .5f, .5f) // LOW MID HI FLT
    val hot = mutableStateListOf<Int?>(null, null, null, null, null, null, null, null) // ms
    var cuePoint = 0
    var loopOn by mutableStateOf(false); var loopBeats by mutableIntStateOf(4)
    var slip by mutableStateOf(false); var rev by mutableStateOf(false)
    var trackBpm by mutableFloatStateOf(0f)
    val bpm get() = if (trackBpm > 0f) trackBpm else 120f
    var key by mutableStateOf("")
    var track by mutableStateOf<Track?>(null)
    var peaks by mutableStateOf<FloatArray?>(null)
    var pos by mutableIntStateOf(0); var dur by mutableIntStateOf(0)
    var fxSel by mutableIntStateOf(0); var fxX by mutableFloatStateOf(.5f); var fxY by mutableFloatStateOf(.5f)
    var fxActive by mutableStateOf(false); var fxHold by mutableStateOf(false)
    private var job: Job? = null; private var slipPos = 0; private var slipT = 0L
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() {
        voice.pcm?.let { p -> pos = (voice.pos * 1000 / p.rate).toInt(); dur = (p.frames * 1000L / p.rate).toInt() }
        voice.dsp.beat = 60.0 / (bpm * speed()) * OUT_RATE
        h.postDelayed(this, 50) } }
    init { h.post(tick); voice.onStop = { playing = false } }

    fun speed() = 1f + tempo / 100f
    private fun rate() = voice.pcm?.rate ?: OUT_RATE
    fun frameOf(ms: Int) = ms * rate() / 1000.0
    fun posMs() = (voice.pos * 1000 / rate()).toInt()
    fun seekMs(ms: Int) = voice.seek(frameOf(ms))

    fun load(ctx: Context, uri: Uri, name: String? = null, t: Track? = null, play: Boolean = false) {
        job?.cancel(); voice.playing = false; playing = false; voice.loopOn = false; loopOn = false
        fxActive = false; fxHold = false; voice.dsp.fxActive = false
        title = "Loading…"; track = t; peaks = null; trackBpm = 0f; key = ""; loading = true
        job = Analyzer.scope.launch {
            val pcm = Decoder.decode(ctx, uri) { isActive }
            if (pcm == null) { title = "Can't open this file"; loading = false; track = null; return@launch }
            voice.pos = 0.0; voice.pcm = pcm; cuePoint = 0; pos = 0; dur = (pcm.frames * 1000L / pcm.rate).toInt()
            title = name ?: "Track"; loading = false
            applyTempo(); applyVol(); applyEq()
            if (play) toggle()
            val r = Analyzer.cached(uri) ?: Analysis.fromPcm(pcm)?.also { Analyzer.store(uri, it) }
            if (r != null) { peaks = r.peaks; trackBpm = r.bpm; key = r.key; autoGainV = r.gain; applyVol() }
        }
    }

    fun toggle() { val p = voice.pcm ?: return
        if (!voice.playing && voice.pos >= p.frames - 2) voice.seek(0.0)
        voice.playing = !voice.playing; playing = voice.playing }
    fun cue() { if (voice.playing) { voice.playing = false; playing = false; seekMs(cuePoint) } else cuePoint = posMs() }
    fun applyTempo() { voice.speed = speed().toDouble() }
    fun applyVol() { voice.vol = vol * gain * (if (autoGainOn) autoGainV else 1f) }
    fun applyEq() { val d = voice.dsp; d.eqLo = eq[0]; d.eqMid = eq[1]; d.eqHi = eq[2]; d.eqFlt = eq[3] }
    fun toggleReverse() { rev = !rev; voice.reverse = rev }

    fun scratchStart() { voice.scratchTarget = voice.pos; voice.scratch = true }
    fun scratchMove(dx: Float) { val p = voice.pcm ?: return; voice.scratchTarget = (voice.scratchTarget + dx * p.rate / 120.0).coerceIn(0.0, p.frames - 1.0) }
    fun scratchEnd() { voice.scratch = false }

    fun jump(beats: Int) { val p = voice.pcm ?: return; voice.seek(voice.pos + beats * 60.0 / bpm * p.rate) }
    private fun loopFrames() = 60.0 / bpm * loopBeats * rate()
    fun toggleLoop() { val p = voice.pcm ?: return
        if (!loopOn) { val s = voice.pos; voice.loopS = s; voice.loopE = min(s + loopFrames(), p.frames - 1.0); voice.loopOn = true; loopOn = true
            slipPos = posMs(); slipT = SystemClock.elapsedRealtime() }
        else { voice.loopOn = false; loopOn = false
            if (slip) seekMs(slipPos + ((SystemClock.elapsedRealtime() - slipT) * speed()).toInt()) } }
    fun changeLoop(b: Int) { loopBeats = b; val p = voice.pcm; if (loopOn && p != null) voice.loopE = min(voice.loopS + loopFrames(), p.frames - 1.0) }

    fun applyFx() { val d = voice.dsp
        if (fxSel == 0) { eq[3] = if (fxActive) fxX else .5f; applyEq(); d.fxActive = false }
        else { d.fx = fxSel; d.fxX = fxX; d.fxY = fxY; d.fxActive = fxActive } }
    fun selectFx(i: Int) { fxActive = false; fxHold = false; eq[3] = .5f; applyEq(); voice.dsp.fxActive = false; fxSel = i; voice.dsp.fx = i }
}

data class Track(val title: String, val artist: String, val album: String, val genre: String, val dur: Long, val uri: Uri)

fun scan(ctx: Context): List<Track> {
    val out = mutableListOf<Track>(); val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val cols = mutableListOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
    if (Build.VERSION.SDK_INT >= 30) cols.add(MediaStore.Audio.Media.GENRE)
    ctx.contentResolver.query(base, cols.toTypedArray(), "${MediaStore.Audio.Media.DURATION}>0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE")?.use { c ->
        while (c.moveToNext()) out += Track(c.getString(1) ?: "Unknown", c.getString(2) ?: "Unknown", c.getString(3) ?: "Unknown",
            if (c.columnCount > 5) c.getString(5) ?: "Unknown" else "Unknown", c.getLong(4),
            android.content.ContentUris.withAppendedId(base, c.getLong(0)))
    }
    return out
}
