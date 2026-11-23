package io.surprize.dj

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Bg = Color(0xFF2A2D35); val Btn = Color(0xFF555A66); val ListBg = Color(0xFF303030)
val Yellow = Color(0xFFF0C930); val Blue = Color(0xFF06A5E0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); Analyzer.init(this); Audio.out.start()
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { DeckApp() } }
    }
    override fun onDestroy() { super.onDestroy(); Audio.out.stop() }
}

@Composable fun Brand() = Text("\$DECK", fontSize = 22.sp, fontWeight = FontWeight.Light, color = Color.Gray, modifier = Modifier.padding(horizontal = 14.dp))

@Composable
fun PlusBtn(c: Color, onClick: () -> Unit) =
    Box(Modifier.size(44.dp).border(2.dp, Color.White, CircleShape).clip(CircleShape).clickable { onClick() }, Alignment.Center) { Text("+", color = c, fontSize = 28.sp) }

@Composable
fun RoundBtn(t: String, on: Boolean, onClick: () -> Unit) =
    Box(Modifier.padding(3.dp).size(38.dp).border(1.5.dp, if (on) Color.White else Color.Gray, CircleShape).clip(CircleShape).clickable { onClick() }, Alignment.Center) {
        Text(t, fontSize = 9.sp, color = if (on) Color.White else Color.Gray) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.DBtn(t: String, c: Color, sub: String = "", onLong: (() -> Unit)? = null, onClick: () -> Unit) =
    Box(Modifier.padding(horizontal = 3.dp).width(72.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(c)
        .combinedClickable(onLongClick = onLong, onClick = onClick), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t, fontSize = 13.sp, color = if (c == Btn) Color.White else Color.Black)
            if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = Yellow) } }

@Composable
fun Panel(d: Deck, mode: Mode, left: Boolean, onWave: () -> Unit, onGear: () -> Unit) = when (mode) {
    Mode.PLATTER -> Box(Modifier.fillMaxSize()) { Platter(d, left)
        Box(Modifier.align(if (left) Alignment.TopEnd else Alignment.TopStart)) { RoundBtn(if (left) "WAVE" else "⚙", false) { if (left) onWave() else onGear() } } }
    Mode.PADS -> Pads(d, left); Mode.MIXER -> Mixer(d, left); Mode.FX -> Fx(d)
}

@Composable
fun VFader(label: String, v: Float, mod: Modifier, onChange: (Float) -> Unit) {
    Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
            detectDragGestures(onDragStart = { onChange((1f - it.y / size.height).coerceIn(0f, 1f)) }) { c, _ -> onChange((1f - c.position.y / size.height).coerceIn(0f, 1f)) } }) {
            val cx = size.width / 2; val th = 40.dp.toPx()
            drawRoundRect(Color.Black, Offset(cx - 5.dp.toPx(), 0f), Size(10.dp.toPx(), size.height), CornerRadius(5.dp.toPx()))
            drawRoundRect(Color(0xFFE6E6E6), Offset(cx - 14.dp.toPx(), (1f - v) * (size.height - th)), Size(28.dp.toPx(), th), CornerRadius(3.dp.toPx())) }
        Text(label, fontSize = 9.sp, color = Color.Gray) }
}

@Composable
fun Platter(d: Deck, left: Boolean) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(d.playing) { while (d.playing) { withFrameNanos { angle += 3f } } }
    val tempo = @Composable { VFader("%.1f%%".format(d.tempo), (d.tempo + d.range) / (2f * d.range), Modifier.width(34.dp).fillMaxHeight().padding(vertical = 4.dp)) { d.tempo = it * 2f * d.range - d.range; d.applyTempo() } }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (left) tempo()
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(d.title, maxLines = 1, fontSize = 10.sp, color = Color.White)
            Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(d.color).border(2.dp, Color.Black, CircleShape).rotate(angle)
                .pointerInput(Unit) { detectDragGestures(onDragStart = { d.scratchStart() }, onDragEnd = { d.scratchEnd() }, onDragCancel = { d.scratchEnd() }) { _, drag -> angle += drag.x * .3f; d.scratchMove(drag.x) } }, Alignment.Center) {
                Box(Modifier.fillMaxSize(.36f).clip(CircleShape).background(Color.Black.copy(alpha = .6f)), Alignment.Center) { Text("\$", color = Color.White, fontSize = 22.sp) } }
        }
        if (!left) tempo()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HotPad(d: Deck, i: Int, mod: Modifier) =
    Box(mod.clip(RoundedCornerShape(6.dp)).background(if (d.hot[i] != null) d.color else Btn)
        .combinedClickable(onClick = { val p = d.hot[i]; if (p == null) d.hot[i] = d.posMs() else d.seekMs(p) }, onLongClick = { d.hot[i] = null }),
        Alignment.Center) { Text("${i + 1}", fontSize = 20.sp, color = Color.White) }

@Composable
fun Pads(d: Deck, left: Boolean) {
    val gap = Arrangement.spacedBy(5.dp)
    Column(Modifier.fillMaxSize().padding(6.dp), gap) {
        val slip = @Composable { SmallBtn("SLIP", d.slip) { d.slip = !d.slip } }
        val x = @Composable { SmallBtn("X", false) { for (i in d.hot.indices) d.hot[i] = null } }
        Row(Modifier.height(28.dp), gap) { if (left) { slip(); x() } else { x(); slip() } }
        Row(Modifier.weight(1f), gap) {
            Button({ d.changeLoop(maxOf(1, d.loopBeats / 2)) }, Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = Btn)) { Text("◀") }
            Button({ d.toggleLoop() }, Modifier.weight(2f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = if (d.loopOn) d.color else Btn)) { Text("⟳ ${d.loopBeats}", fontSize = 22.sp) }
            Button({ d.changeLoop(minOf(32, d.loopBeats * 2)) }, Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = Btn)) { Text("▶") }
        }
        for (r in 0 until 2) Row(Modifier.weight(1.3f), gap) { for (c in 0 until 4) HotPad(d, r * 4 + c, Modifier.weight(1f).fillMaxHeight()) }
    }
}

@Composable
fun SmallBtn(t: String, on: Boolean, onClick: () -> Unit) =
    Button(onClick, Modifier.fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = if (on) Yellow else Btn), contentPadding = PaddingValues(horizontal = 10.dp)) { Text(t, fontSize = 9.sp) }

@Composable
fun Mixer(d: Deck, left: Boolean) {
    val order = if (left) listOf("VOL", "LOW", "MID", "HI", "FLT") else listOf("FLT", "LOW", "MID", "HI", "VOL")
    Column(Modifier.fillMaxSize().padding(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("GAIN", fontSize = 9.sp, color = Color.Gray)
            Slider(d.gain / 2f, { d.gain = it * 2f; d.applyVol() }, Modifier.height(24.dp)) }
        Row(Modifier.weight(1f)) { for (n in order) {
            val idx = listOf("LOW", "MID", "HI", "FLT").indexOf(n)
            VFader(n, if (idx < 0) d.vol else d.eq[idx], Modifier.weight(1f).fillMaxHeight()) {
                if (idx < 0) { d.vol = it; d.applyVol() } else { d.eq[idx] = it; d.applyEq() } } } }
    }
}

@Composable
fun Thumb(t: Track) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, t.uri) {
        value = if (Build.VERSION.SDK_INT >= 29) withContext(Dispatchers.IO) {
            try { ctx.contentResolver.loadThumbnail(t.uri, android.util.Size(96, 96), null).asImageBitmap() } catch (_: Exception) { null } } else null }
    Box(Modifier.size(46.dp).background(Color(0xFF1C1C1C))) { bmp?.let { Image(it, null, Modifier.fillMaxSize()) } }
}

