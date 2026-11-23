package io.surprize.dj

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Mix { val queue = mutableStateListOf<Track>(); var armed by mutableStateOf(false) }

fun mmss(ms: Long) = "%02d:%02d".format(ms / 60000, ms / 1000 % 60)

@Composable
fun rememberArt(uri: Uri?, px: Int): State<ImageBitmap?> {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(null, uri) {
        value = if (uri != null && Build.VERSION.SDK_INT >= 29) withContext(Dispatchers.IO) {
            try { ctx.contentResolver.loadThumbnail(uri, android.util.Size(px, px), null).asImageBitmap() } catch (_: Exception) { null } } else null }
}

@Composable
fun DeckApp() {
    val ctx = LocalContext.current
    val a = remember { Deck(Yellow, 0) }; val b = remember { Deck(Blue, 1) }
    var mode by remember { mutableStateOf(Mode.PLATTER) }
    var cf by remember { mutableFloatStateOf(.5f) }
    var picking by remember { mutableStateOf<Deck?>(null) }
    var zoom by remember { mutableStateOf(false) }
    var gear by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    var rec by remember { mutableStateOf(false) }; var recSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(rec) { recSec = 0; while (rec) { delay(1000); recSec++ } }
    fun mixXf(v: Float) { cf = v; Audio.master.cf = v }
    fun sync(me: Deck, o: Deck) {
        me.tempo = (if (me.trackBpm > 0f && o.trackBpm > 0f) (o.trackBpm * o.speed() / me.trackBpm - 1f) * 100f else o.tempo).coerceIn(-me.range, me.range)
        me.applyTempo() }
    fun bpmText(d: Deck) = if (d.trackBpm > 0f) "%.1f".format(d.trackBpm * d.speed()) else ""

    // Automix: start the queue, and fade into the next song when the playing one is nearly over
    LaunchedEffect(Unit) { while (true) { delay(500)
        if (!Mix.armed || Mix.queue.isEmpty() || a.loading || b.loading) continue
        val cur = listOf(a, b).firstOrNull { it.playing }
        if (cur == null) { val t = Mix.queue.removeAt(0); a.load(ctx, t.uri, t.title, t, true); mixXf(0f) }
        else if (cur.dur > 0 && cur.dur - cur.pos < 10000) { val o = if (cur === a) b else a
            if (!o.playing) { val t = Mix.queue.removeAt(0); o.load(ctx, t.uri, t.title, t, true)
                val from = cf; val to = if (o === b) 1f else 0f
                repeat(40) { i -> mixXf(from + (to - from) * (i + 1) / 40f); delay(200) } } }
        if (Mix.queue.isEmpty()) Mix.armed = false } }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                DeckHead(a, true, zoom, Modifier.weight(1f)) { picking = a }
                Row(Modifier.clickable { rec = Recorder.toggle(ctx) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (rec) Color.Red else Color(0xFF771111)))
                    Text(if (rec) " REC ${mmss(recSec * 1000L)} " else " REC ", fontSize = 9.sp, color = if (rec) Color.White else Color.Gray) }
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color.Black).clickable { tools = true }, Alignment.Center) { Text("≣", color = Color.White) }
                DeckHead(b, false, zoom, Modifier.weight(1f)) { picking = b }
            }
            Row(Modifier.weight(1f)) {
                Box(Modifier.weight(1f)) { Panel(a, mode, true, { zoom = !zoom }, { gear = true }) }
                Column(Modifier.width(130.dp).fillMaxHeight().padding(vertical = 6.dp), Arrangement.SpaceBetween, Alignment.CenterHorizontally) {
                    Row { for ((m, l) in listOf(Mode.PADS to "LOOP", Mode.MIXER to "MIX", Mode.FX to "FX"))
                        RoundBtn(l, mode == m) { mode = if (mode == m) Mode.PLATTER else m } }
                    Box(Modifier.width(24.dp).weight(1f).padding(vertical = 8.dp).background(Color.Black))
                    RoundBtn("▦", false) { mode = Mode.PLATTER }
                }
                Box(Modifier.weight(1f)) { Panel(b, mode, false, { zoom = !zoom }, { gear = true }) }
            }
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DBtn("CUE", Btn) { a.cue() }; DBtn("▶", if (a.playing) Yellow else Btn, onLong = { a.voice.brake(1.0) }) { a.toggle() }
                DBtn("SYNC", Btn, bpmText(a)) { sync(a, b) }
                Slider(cf, { mixXf(it) }, Modifier.weight(1f).padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Blue, inactiveTrackColor = Yellow))
                DBtn("SYNC", Btn, bpmText(b)) { sync(b, a) }
                DBtn("CUE", Btn) { b.cue() }; DBtn("▶", if (b.playing) Blue else Btn, onLong = { b.voice.brake(1.0) }) { b.toggle() }
            }
        }
        picking?.let { d -> Songs(d) { picking = null } }
    }
    if (tools) Tools(a, b) { tools = false }
    if (gear) AlertDialog({ gear = false }, confirmButton = { TextButton({ gear = false }) { Text("OK") } }, title = { Text("Settings") },
        text = { Column { for ((d, n) in listOf(a to "Left deck", b to "Right deck")) {
            Text("$n BPM %.1f (used by loops & sync)".format(d.bpm), fontSize = 12.sp)
            Slider(d.bpm, { d.trackBpm = it }, valueRange = 60f..200f) } } })
}

@Composable
fun DeckHead(d: Deck, left: Boolean, zoom: Boolean, mod: Modifier, open: () -> Unit) {
    val art by rememberArt(d.track?.uri, 128)
    val btn = @Composable {
        Box(Modifier.size(44.dp).border(2.dp, Color.White, CircleShape).clip(CircleShape).clickable { open() }, Alignment.Center) {
            art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text("+", color = d.color, fontSize = 28.sp) } }
    val time = @Composable { Text("-" + mmss((d.dur - d.pos).toLong().coerceAtLeast(0)), fontSize = 11.sp, color = d.color, modifier = Modifier.padding(horizontal = 6.dp)) }
    Row(mod.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        if (left) btn()
        if (d.track == null) Box(Modifier.weight(1f), Alignment.Center) { Brand() }
        else { if (left) time()
            Box(Modifier.weight(1f).fillMaxHeight()) { Wave(d, zoom)
                Text("${d.track?.title}  ${d.track?.artist}  ${d.key}", maxLines = 1, fontSize = 9.sp, color = Color.White, modifier = Modifier.align(Alignment.Center)) }
            if (!left) time() }
        if (!left) btn()
    }
}

@Composable
fun Wave(d: Deck, zoom: Boolean) {
    Canvas(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        val pk = d.peaks ?: return@Canvas; val n = pk.size; val frac = if (d.dur > 0) d.pos.toFloat() / d.dur else 0f
        if (!zoom) { val w = size.width / n
            for (i in 0 until n) { val h = pk[i] * size.height; drawRect(d.color, Offset(i * w, (size.height - h) / 2), Size(maxOf(w, 1f), h)) }
            drawRect(Color.White, Offset(frac * size.width, 0f), Size(2.dp.toPx(), size.height))
        } else { val span = n * .08f; var px = 0f
            while (px < size.width) { val idx = (frac * n + (px / size.width - .5f) * span * 2).toInt()
                if (idx in 0 until n) { val h = pk[idx] * size.height; drawRect(d.color, Offset(px, (size.height - h) / 2), Size(2.dp.toPx(), h)) }
                px += 3.dp.toPx() }
            drawRect(Color.White, Offset(size.width / 2, 0f), Size(2.dp.toPx(), size.height)) }
    }
}

@Composable
fun Fx(d: Deck) {
    val names = FX_NAMES
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().padding(6.dp), Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E2026)).pointerInput(Unit) {
            awaitEachGesture {
                fun upd(o: Offset) { d.fxX = (o.x / size.width).coerceIn(0f, 1f); d.fxY = (1f - o.y / size.height).coerceIn(0f, 1f); d.fxActive = true; d.applyFx() }
                val down = awaitFirstDown(); upd(down.position)
                do { val ev = awaitPointerEvent(); val ch = ev.changes.firstOrNull()
                    if (ch != null && ch.pressed) { upd(ch.position); ch.consume() }
                } while (ev.changes.any { it.pressed })
                if (!d.fxHold) { d.fxActive = false; d.applyFx() }   // 🔒 on: effect stays where your finger left it
            } }) {
            val step = 7.dp.toPx(); var y = step / 2
            while (y < size.height) { var x = step / 2; while (x < size.width) { drawCircle(Color(0xFF2E323A), 1.dp.toPx(), Offset(x, y)); x += step }; y += step }
            if (d.fxActive) { val p = Offset(d.fxX * size.width, (1f - d.fxY) * size.height); val r = 70.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(d.color.copy(alpha = .35f), Color.Transparent), p, r), r, p)
                drawCircle(d.color, 24.dp.toPx(), p, style = Stroke(2.dp.toPx())); drawCircle(d.color, 14.dp.toPx(), p) }
        }
        Column(Modifier.width(60.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A2D35)), Arrangement.SpaceEvenly, Alignment.CenterHorizontally) {
            Text(names[d.fxSel], fontSize = 10.sp, color = d.color)
            Box { Text("⌄", fontSize = 26.sp, color = d.color, modifier = Modifier.clickable { menu = true }.padding(8.dp))
                DropdownMenu(menu, { menu = false }) { names.forEachIndexed { i, n -> DropdownMenuItem({ Text(n) }, { menu = false; d.selectFx(i) }) } } }
            Text(if (d.fxHold) "🔒" else "🔓", fontSize = 22.sp, modifier = Modifier.clickable { d.fxHold = !d.fxHold; if (!d.fxHold) { d.fxActive = false; d.applyFx() } }.padding(8.dp))
        }
    }
}

@Composable fun HdrBtn(t: String, onClick: () -> Unit) = Text(t, fontSize = 10.sp, color = Color.White, modifier = Modifier.clickable { onClick() }.padding(horizontal = 10.dp))

@Composable
fun Songs(d: Deck, close: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val list = rememberLazyListState()
    BackHandler(onBack = close)
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var tracks by remember { mutableStateOf(listOf<Track>()) }
    var tab by remember { mutableIntStateOf(1) }; var sort by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }; var searching by remember { mutableStateOf(false) }
    var filt by remember { mutableStateOf<((Track) -> Boolean)?>(null) }
    val sp = remember { ctx.getSharedPreferences("pl", 0) }
    fun readPl() = sp.all.mapValues { (it.value as String).split("\n").filter { s -> s.isNotEmpty() } }
    var pls by remember { mutableStateOf(readPl()) }; var newPl by remember { mutableStateOf(false) }; var plName by remember { mutableStateOf("") }
    fun addTo(n: String, u: Uri) { sp.edit().putString(n, ((pls[n] ?: listOf()) + u.toString()).joinToString("\n")).apply(); pls = readPl() }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) tracks = scan(ctx) }
    LaunchedEffect(Unit) { if (ctx.checkSelfPermission(perm) == 0) tracks = scan(ctx) else ask.launch(perm) }
    val tabs = listOf("PLAYLISTS", "SONGS", "ALBUMS", "ARTISTS", "GENRES"); val sortNames = listOf("TITLE", "ARTIST", "LENGTH")
    val shown = tracks.filter { (filt?.invoke(it) ?: true) && (it.title.contains(q, true) || it.artist.contains(q, true)) }
        .sortedWith(when (sort) { 0 -> compareBy { it.title.lowercase() }; 1 -> compareBy { it.artist.lowercase() }; else -> compareBy { it.dur } })
    Box(Modifier.fillMaxSize().background(ListBg)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Yellow, Color(0xFFFBC02D))))) {
                Row(Modifier.height(54.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("☰", fontSize = 24.sp, color = Color.White, modifier = Modifier.clickable { close() }.padding(end = 16.dp))
                    Column(Modifier.weight(1f)) { Text("Songs", color = Color.White, fontSize = 16.sp)
                        Text(Analyzer.progress.ifEmpty { "${tracks.size} tracks" }, fontSize = 10.sp, color = Color(0xCCFFFFFF)) }
                    if (searching) TextField(q, { q = it }, Modifier.width(180.dp).height(48.dp), singleLine = true)
                    HdrBtn("SEARCH") { searching = !searching; if (!searching) q = "" }
                    HdrBtn("${sortNames[sort]} ⇅") { sort = (sort + 1) % 3 }
                    HdrBtn("ANALYSE SONGS") { if (Analyzer.progress.isEmpty()) { val all = tracks
                        Analyzer.scope.launch { all.forEachIndexed { i, t -> Analyzer.progress = "Analysing ${i + 1}/${all.size}"; Analyzer.analyzeUri(ctx, t.uri) }; Analyzer.progress = "" } } }
                    HdrBtn("ADD TO AUTOMIX") { Mix.queue.addAll(shown); Mix.armed = true; Toast.makeText(ctx, "${shown.size} tracks in automix", Toast.LENGTH_SHORT).show() }
                }
                Row(Modifier.height(34.dp)) { tabs.forEachIndexed { i, t ->
                    Column(Modifier.width(100.dp).fillMaxHeight().clickable { tab = i; if (i == 1) filt = null }, Arrangement.SpaceBetween, Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(8.dp)); Text(t, fontSize = 10.sp, color = Color.White)
                        Box(Modifier.fillMaxWidth().height(3.dp).background(if (tab == i) Color.White else Color.Transparent)) } } }
            }
            if (tab == 1) LazyColumn(Modifier.weight(1f), list) { items(shown) { t ->
                var menu by remember { mutableStateOf(false) }
                val load = { d.load(ctx, t.uri, t.title, t); close() }
                val art by rememberArt(t.uri, 96); val bpm = Analyzer.bpms[t.uri.toString()]
                Row(Modifier.fillMaxWidth().clickable { load() }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(Color(0xFF1C1C1C))) { art?.let { Image(it, null, Modifier.fillMaxSize()) } }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(t.title, maxLines = 1, fontSize = 14.sp, color = Color.White)
                        Text("${t.album} - ${t.title}   ${mmss(t.dur)}" + (bpm?.let { "   %.1f BPM".format(it) } ?: "") + (Analyzer.keys[t.uri.toString()]?.let { "  $it" } ?: ""), maxLines = 1, fontSize = 9.sp, color = Color.Gray) }
                    Box { Text("⋯", fontSize = 22.sp, color = Color.White, modifier = Modifier.clickable { menu = true }.padding(12.dp))
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Load to deck") }, { menu = false; load() })
                            DropdownMenuItem({ Text("Add to automix") }, { menu = false; Mix.queue.add(t); Mix.armed = true })
                            pls.keys.forEach { n -> DropdownMenuItem({ Text("Add to $n") }, { menu = false; addTo(n, t.uri) }) } } }
                } } }
            else if (tab == 0) Column(Modifier.weight(1f)) {
                Button({ newPl = true }, Modifier.padding(10.dp)) { Text("NEW PLAYLIST") }
                LazyColumn { items(pls.toList()) { (n, us) ->
                    Column(Modifier.fillMaxWidth().clickable { filt = { it.uri.toString() in us }; tab = 1 }.padding(12.dp)) {
                        Text(n, color = Color.White, fontSize = 14.sp); Text("${us.size} tracks", color = Color.Gray, fontSize = 9.sp) } } } }
            else { val key: (Track) -> String = when (tab) { 2 -> { t -> t.album }; 3 -> { t -> t.artist }; else -> { t -> t.genre } }
                LazyColumn(Modifier.weight(1f)) { items(tracks.groupBy(key).toList().sortedBy { it.first.lowercase() }) { (name, ts) ->
                    Column(Modifier.fillMaxWidth().clickable { filt = { key(it) == name }; tab = 1 }.padding(12.dp)) {
                        Text(name, color = Color.White, fontSize = 14.sp, maxLines = 1); Text("${ts.size} tracks", color = Color.Gray, fontSize = 9.sp) } } } }
        }
        if (tab == 1) Box(Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 74.dp).size(52.dp).clip(CircleShape).background(Color(0xFFFF5722))
            .clickable { scope.launch { list.animateScrollToItem(0) } }, Alignment.Center) { Text("↑", color = Color.White, fontSize = 24.sp) }
    }
    if (newPl) AlertDialog({ newPl = false }, title = { Text("New playlist") }, text = { TextField(plName, { plName = it }, singleLine = true) },
        confirmButton = { TextButton({ if (plName.isNotBlank()) { sp.edit().putString(plName.trim(), "").apply(); pls = readPl() }; plName = ""; newPl = false }) { Text("CREATE") } })
}

@Composable
fun TBtn(t: String, on: Boolean = false, onClick: () -> Unit) =
    Button(onClick, Modifier.height(34.dp), colors = ButtonDefaults.buttonColors(containerColor = if (on) Yellow else Btn),
        contentPadding = PaddingValues(horizontal = 8.dp)) { Text(t, fontSize = 10.sp, color = if (on) Color.Black else Color.White) }

@Composable
fun ToolsDeck(d: Deck, mod: Modifier) {
    Column(mod.verticalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp)) {
        Text(if (d.idx == 0) "LEFT DECK" else "RIGHT DECK", color = d.color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("BPM " + (if (d.trackBpm > 0f) "%.1f".format(d.trackBpm) else "--") + "   KEY " + d.key.ifEmpty { "--" }, color = Color.White, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { TBtn("BRAKE") { d.voice.brake(1.0) }; TBtn("SPIN BACK") { d.voice.spinBack() }; TBtn("REVERSE", d.rev) { d.toggleReverse() } }
        Text("BEAT JUMP", color = Color.Gray, fontSize = 9.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { for (j in listOf(-16, -4, -1, 1, 4, 16)) TBtn(if (j > 0) "+$j" else "$j") { d.jump(j) } }
        Text("TEMPO RANGE", color = Color.Gray, fontSize = 9.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { for (r in listOf(8f, 16f, 50f)) TBtn("±${r.toInt()}%", d.range == r) { d.range = r; d.tempo = d.tempo.coerceIn(-r, r); d.applyTempo() } }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("AUTO-GAIN", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Switch(d.autoGainOn, { d.autoGainOn = it; d.applyVol() }) }
    }
}

@Composable
fun Tools(a: Deck, b: Deck, close: () -> Unit) {
    val s = Audio.master.sampler
    var sv by remember { mutableFloatStateOf(s.vol) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(.96f).fillMaxHeight(.9f), shape = RoundedCornerShape(12.dp), color = Color(0xFF23262D)) {
            Row(Modifier.padding(12.dp), Arrangement.spacedBy(14.dp)) {
                ToolsDeck(a, Modifier.weight(1f))
                Column(Modifier.weight(1.1f), Arrangement.spacedBy(6.dp), Alignment.CenterHorizontally) {
                    Text("SAMPLER", color = Color.Gray, fontSize = 10.sp)
                    for (r in 0 until 2) Row(Modifier.weight(1f), Arrangement.spacedBy(6.dp)) { for (c in 0 until 4) { val i = r * 4 + c
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Btn).pointerInput(Unit) {
                            awaitEachGesture { awaitFirstDown(); s.trigger(i); do { val ev = awaitPointerEvent() } while (ev.changes.any { it.pressed }) } }, Alignment.Center) {
                            Text(s.names[i], fontSize = 10.sp, color = Color.White) } } }
                    Slider(sv, { sv = it; s.vol = it })
                    TextButton(close) { Text("DONE") }
                }
                ToolsDeck(b, Modifier.weight(1f))
            } } }
}
