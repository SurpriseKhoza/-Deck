package io.surprize.dj

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*

/** Caches analysis results (BPM + key are remembered across launches) and runs library-wide analysis in the background. */
object Analyzer {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val bpms = mutableStateMapOf<String, Float>()
    val keys = mutableStateMapOf<String, String>()
    var progress by mutableStateOf("")
    private val peaksCache = java.util.Collections.synchronizedMap(HashMap<String, FloatArray>())
    private val gains = java.util.Collections.synchronizedMap(HashMap<String, Float>())
    private var pb: android.content.SharedPreferences? = null
    private var pk: android.content.SharedPreferences? = null

    fun init(ctx: Context) {
        if (pb != null) return
        pb = ctx.getSharedPreferences("bpm", 0); pk = ctx.getSharedPreferences("keys", 0)
        pb!!.all.forEach { (k, v) -> (v as? Float)?.let { bpms[k] = it } }
        pk!!.all.forEach { (k, v) -> (v as? String)?.let { keys[k] = it } }
    }

    fun store(uri: Uri, r: Analysis.Result) {
        val k = uri.toString(); peaksCache[k] = r.peaks; gains[k] = r.gain; bpms[k] = r.bpm; keys[k] = r.key
        pb?.edit()?.putFloat(k, r.bpm)?.apply(); pk?.edit()?.putString(k, r.key)?.apply()
    }

    fun cached(uri: Uri): Analysis.Result? { val k = uri.toString()
        val p = peaksCache[k]; val b = bpms[k]; val key = keys[k]; val g = gains[k]
        return if (p != null && b != null && key != null && g != null) Analysis.Result(p, b, key, g) else null }

    suspend fun analyzeUri(ctx: Context, uri: Uri): Analysis.Result? {
        cached(uri)?.let { return it }
        return withContext(Dispatchers.Default) {
            val pcm = Decoder.decode(ctx, uri) { isActive } ?: return@withContext null
            Analysis.fromPcm(pcm)?.also { store(uri, it) } }
    }
}
