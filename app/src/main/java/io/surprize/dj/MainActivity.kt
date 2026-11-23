package io.surprize.dj

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

val Bg = Color(0xFF2A2D35); val Btn = Color(0xFF555A66); val ListBg = Color(0xFF303030)
val Yellow = Color(0xFFF0C930); val Blue = Color(0xFF06A5E0)
val KeyTop = Color(0xFF666B77); val KeyBot = Color(0xFF4B505B); val Edge = Color(0xFF3A3E48)
val Ring = Color(0xFF8A8F99)
val KeyBrush = Brush.verticalGradient(listOf(KeyTop, KeyBot))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); Analyzer.init(this); Audio.out.start()
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { DeckApp() } }
    }
    override fun onDestroy() { super.onDestroy(); Audio.out.stop() }
}

@Composable fun Brand() = Text("\$DECK", fontSize = 26.sp, fontWeight = FontWeight.Light, color = Ring, modifier = Modifier.padding(horizontal = 14.dp))

enum class Ico { LOOP, MIX, FX, GRID, WAVE, GEAR, PLUS, MENU, SEARCH, CHEVRON, LOCK, UNLOCK }

/** All icons are drawn in code, so there are no image assets and no icon library. */
@Composable
fun Glyph(i: Ico, color: Color, sz: Dp = 20.dp) {
    Canvas(Modifier.size(sz)) {
        val w = size.width
        val st = Stroke(width = w * .09f, cap = StrokeCap.Round)
        when (i) {
            Ico.PLUS -> {
                drawLine(color, Offset(w * .5f, w * .12f), Offset(w * .5f, w * .88f), strokeWidth = w * .12f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .12f, w * .5f), Offset(w * .88f, w * .5f), strokeWidth = w * .12f, cap = StrokeCap.Round)
            }
            Ico.MENU -> for (k in 0..3) { val y = w * (.2f + .2f * k)
                drawLine(color, Offset(w * .15f, y), Offset(w * .85f, y), strokeWidth = w * .09f, cap = StrokeCap.Round) }
            Ico.GRID -> for (r in 0..2) for (c in 0..2)
                drawRoundRect(color, Offset(w * (.08f + .31f * c), w * (.08f + .31f * r)), Size(w * .23f, w * .23f), CornerRadius(w * .04f))
            Ico.MIX -> { val xs = floatArrayOf(.68f, .34f, .62f)
                for (k in 0..2) { val y = w * (.25f + .25f * k)
                    drawLine(color, Offset(w * .1f, y), Offset(w * .9f, y), strokeWidth = w * .07f, cap = StrokeCap.Round)
                    drawRoundRect(color, Offset(w * xs[k] - w * .09f, y - w * .1f), Size(w * .18f, w * .2f), CornerRadius(w * .03f)) } }
            Ico.WAVE -> { val hs = floatArrayOf(.25f, .55f, .9f, .5f, .75f, .35f, .2f)
                for (k in hs.indices) { val x = w * (.1f + .13f * k); val h = hs[k] * w * .45f
                    drawLine(color, Offset(x, w * .5f - h), Offset(x, w * .5f + h), strokeWidth = w * .07f, cap = StrokeCap.Round) } }
            Ico.GEAR -> {
                drawCircle(color, w * .26f, Offset(w * .5f, w * .5f), style = Stroke(w * .12f))
                for (k in 0 until 8) { val a = k * Math.PI / 4
                    drawLine(color, Offset(w * .5f + (cos(a) * w * .3).toFloat(), w * .5f + (sin(a) * w * .3).toFloat()),
                        Offset(w * .5f + (cos(a) * w * .44).toFloat(), w * .5f + (sin(a) * w * .44).toFloat()), strokeWidth = w * .13f) } }
            Ico.LOOP -> {
                val r = w * .3f; val c = w * .5f
                drawArc(color, 200f, 140f, false, Offset(c - r, c - r), Size(2 * r, 2 * r), style = st)
                drawArc(color, 20f, 140f, false, Offset(c - r, c - r), Size(2 * r, 2 * r), style = st)
                for (deg in floatArrayOf(340f, 160f)) {
                    val a = Math.toRadians(deg.toDouble()); val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    val px = c + r * ca; val py = c + r * sa
                    val p = Path().apply {
                        moveTo(px - sa * w * .17f, py + ca * w * .17f)
                        lineTo(px + ca * w * .13f, py + sa * w * .13f)
                        lineTo(px - ca * w * .13f, py - sa * w * .13f); close() }
                    drawPath(p, color) } }
            Ico.SEARCH -> {
                drawCircle(color, w * .26f, Offset(w * .42f, w * .42f), style = Stroke(w * .1f))
                drawLine(color, Offset(w * .62f, w * .62f), Offset(w * .88f, w * .88f), strokeWidth = w * .12f, cap = StrokeCap.Round) }
            Ico.CHEVRON -> {
                drawLine(color, Offset(w * .2f, w * .35f), Offset(w * .5f, w * .68f), strokeWidth = w * .1f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .5f, w * .68f), Offset(w * .8f, w * .35f), strokeWidth = w * .1f, cap = StrokeCap.Round) }
            Ico.LOCK, Ico.UNLOCK -> {
                val open = i == Ico.UNLOCK; val top = if (open) w * .02f else w * .12f
                drawRoundRect(color, Offset(w * .2f, w * .45f), Size(w * .6f, w * .42f), CornerRadius(w * .06f))
                drawArc(color, 180f, 180f, false, Offset(w * .3f, top), Size(w * .4f, w * .5f), style = st)
                drawLine(color, Offset(w * .3f, top + w * .25f), Offset(w * .3f, w * .45f), strokeWidth = w * .09f)
                if (!open) drawLine(color, Offset(w * .7f, top + w * .25f), Offset(w * .7f, w * .45f), strokeWidth = w * .09f) }
            else -> {}
        }
    }
}

@Composable
fun IconBtn(i: Ico, on: Boolean, onClick: () -> Unit) =
    Box(Modifier.padding(3.dp).size(38.dp).clip(CircleShape).background(if (on) Color(0xFF3B3F4A) else Color.Transparent)
        .border(1.5.dp, if (on) Color.White else Ring, CircleShape).clickable { onClick() }, Alignment.Center) {
        if (i == Ico.FX) Text("FX", fontSize = 13.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = Color.White)
        else Glyph(i, Color.White, 20.dp) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.DBtn(t: String, c: Color, sub: String = "", onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val grey = c == Btn
    val brush = if (grey) KeyBrush else Brush.verticalGradient(listOf(lerp(c, Color.White, .18f), c))
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.padding(horizontal = 3.dp).width(72.dp).fillMaxHeight().clip(shape).background(brush).border(1.dp, Edge, shape)
        .combinedClickable(onLongClick = onLong, onClick = onClick), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (t == "▶") { val ink = if (grey) Color.White else Color.Black
                Canvas(Modifier.size(18.dp)) { val p = Path().apply { moveTo(size.width * .15f, size.height * .05f); lineTo(size.width * .95f, size.height * .5f); lineTo(size.width * .15f, size.height * .95f); close() }
                    drawPath(p, ink) } }
            else Text(t, fontSize = 14.sp, color = if (grey) Color.White else Color.Black)
            if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = Yellow) } }
}

@Composable
fun Panel(d: Deck, mode: Mode, left: Boolean, onWave: () -> Unit, onGear: () -> Unit) = when (mode) {
    Mode.PLATTER -> Box(Modifier.fillMaxSize()) { Platter(d, left)
        Box(Modifier.align(if (left) Alignment.TopStart else Alignment.TopEnd).padding(4.dp)) {
            IconBtn(if (left) Ico.WAVE else Ico.GEAR, false) { if (left) onWave() else onGear() } } }
    Mode.PADS -> Pads(d, left); Mode.MIXER -> Mixer(d, left); Mode.FX -> Fx(d)
}

/** Vertical fader: thick black track and a wide labelled cap, like the reference. */
@Composable
fun VFader(label: String, v: Float, modifier: Modifier, cap: Color = Color(0xFFE9E9E9), ink: Color = Color(0xFF4A4A4A),
           capW: Dp = 44.dp, track: Dp = 9.dp, onChange: (Float) -> Unit) {
    val capH = 22.dp
    val cb by rememberUpdatedState(onChange)
    BoxWithConstraints(modifier.pointerInput(Unit) {
        val capPx = capH.toPx()
        fun at(y: Float) = (1f - (y - capPx / 2) / (size.height - capPx)).coerceIn(0f, 1f)
        detectDragGestures(onDragStart = { cb(at(it.y)) }) { c, _ -> cb(at(c.position.y)) }
    }, contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxHeight().width(track).clip(RoundedCornerShape(track / 2)).background(Color.Black))
        Box(Modifier.offset(y = (maxHeight - capH) * (1f - v)).size(capW, capH).clip(RoundedCornerShape(3.dp)).background(cap), Alignment.Center) {
            Text(label, fontSize = 9.sp, color = ink, maxLines = 1) }
    }
}

@Composable
fun Knob(v: Float, color: Color, onChange: (Float) -> Unit) {
    val cb by rememberUpdatedState(onChange); val cur by rememberUpdatedState(v)
    Canvas(Modifier.size(34.dp).pointerInput(Unit) {
        detectDragGestures { c, drag -> c.consume(); cb((cur - drag.y / 300f).coerceIn(0f, 1f)) } }) {
        val r = size.minDimension / 2 - 4.dp.toPx(); val ctr = center
        drawArc(Color(0xFF3A3E48), 135f, 270f, false, Offset(ctr.x - r, ctr.y - r), Size(2 * r, 2 * r), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        val a = Math.toRadians((135f + 270f * v).toDouble()); val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
        drawLine(color, Offset(ctr.x + dx * r * .3f, ctr.y + dy * r * .3f), Offset(ctr.x + dx * r * .95f, ctr.y + dy * r * .95f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
fun Platter(d: Deck, left: Boolean) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(d.playing) { while (d.playing) { withFrameNanos { angle += 3f } } }
    val tempo = @Composable {
        Box(Modifier.width(56.dp).fillMaxHeight().padding(top = 50.dp, bottom = 10.dp)) {
            VFader("%.1f%%".format(d.tempo), (d.tempo + d.range) / (2f * d.range), Modifier.fillMaxSize(), capW = 54.dp, track = 5.dp) {
                d.tempo = it * 2f * d.range - d.range; d.applyTempo() }
            Box(Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd).size(7.dp).clip(CircleShape).background(d.color)) }
    }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (left) tempo()
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val s = minOf(maxWidth, maxHeight) - 4.dp
            Box(Modifier.size(s).clip(CircleShape).background(d.color).border(2.dp, Color.Black, CircleShape).rotate(angle)
                .pointerInput(Unit) { detectDragGestures(onDragStart = { d.scratchStart() }, onDragEnd = { d.scratchEnd() }, onDragCancel = { d.scratchEnd() }) { _, drag -> angle += drag.x * .3f; d.scratchMove(drag.x) } }, Alignment.Center) {
                Box(Modifier.fillMaxSize(.36f).clip(CircleShape).background(Color.Black.copy(alpha = .55f)), Alignment.Center) { Text("\$", color = Color.White, fontSize = 22.sp) } }
        }
        if (!left) tempo()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HotPad(d: Deck, i: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.clip(shape).background(if (d.hot[i] != null) Brush.verticalGradient(listOf(lerp(d.color, Color.White, .15f), d.color)) else KeyBrush)
        .border(1.dp, Edge, shape)
        .combinedClickable(onClick = { val p = d.hot[i]; if (p == null) d.hot[i] = d.posMs() else d.seekMs(p) }, onLongClick = { d.hot[i] = null }),
        Alignment.Center) { Text("${i + 1}", fontSize = 20.sp, color = Color.White) }
}

@Composable
fun Tri(pointLeft: Boolean, modifier: Modifier, onClick: () -> Unit) =
    Box(modifier.clickable { onClick() }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val p = Path().apply {
                if (pointLeft) { moveTo(w * .92f, h * .1f); lineTo(w * .08f, h * .5f); lineTo(w * .92f, h * .9f) }
                else { moveTo(w * .08f, h * .1f); lineTo(w * .92f, h * .5f); lineTo(w * .08f, h * .9f) }
                close() }
            drawPath(p, Color(0xFF565B66)) } }

@Composable
fun Pads(d: Deck, left: Boolean) {
    val gap = Arrangement.spacedBy(5.dp)
    Column(Modifier.fillMaxSize().padding(6.dp), gap) {
        val slip = @Composable { SmallBtn("SLIP", d.slip) { d.slip = !d.slip } }
        val x = @Composable { SmallBtn("X", false) { for (i in d.hot.indices) d.hot[i] = null } }
        Row(Modifier.height(28.dp), gap) { if (left) { slip(); x() } else { x(); slip() } }
        Row(Modifier.weight(1f), gap) {
            Tri(true, Modifier.weight(1f).fillMaxHeight()) { d.changeLoop(maxOf(1, d.loopBeats / 2)) }
            val shape = RoundedCornerShape(6.dp)
            Box(Modifier.weight(2f).fillMaxHeight().clip(shape)
                .background(if (d.loopOn) Brush.verticalGradient(listOf(lerp(d.color, Color.White, .15f), d.color)) else KeyBrush)
                .border(1.dp, Edge, shape).clickable { d.toggleLoop() }, Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Ico.LOOP, Color.White, 26.dp); Spacer(Modifier.width(6.dp))
                    Text("${d.loopBeats}", fontSize = 26.sp, color = Color.White) } }
            Tri(false, Modifier.weight(1f).fillMaxHeight()) { d.changeLoop(minOf(32, d.loopBeats * 2)) }
        }
        for (r in 0 until 2) Row(Modifier.weight(1.3f), gap) { for (c in 0 until 4) HotPad(d, r * 4 + c, Modifier.weight(1f).fillMaxHeight()) }
    }
}

@Composable
fun SmallBtn(t: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(Modifier.fillMaxHeight().clip(shape).background(if (on) Yellow else Color(0xFF2F323A)).border(1.dp, Edge, shape)
        .clickable { onClick() }.padding(horizontal = 14.dp), Alignment.Center) {
        Text(t, fontSize = 9.sp, color = if (on) Color.Black else Color(0xFFB0B4BD)) }
}

@Composable
fun Mixer(d: Deck, left: Boolean) {
    val order = if (left) listOf("VOL", "LOW", "MID", "HI", "FLT") else listOf("FLT", "LOW", "MID", "HI", "VOL")
    var g by remember { mutableFloatStateOf(d.gain) }
    Row(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
        for (n in order) {
            val idx = listOf("LOW", "MID", "HI", "FLT").indexOf(n)
            val flt = n == "FLT"
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(56.dp), Alignment.Center) {
                    if (n == "VOL") Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Knob(g / 2f, d.color) { g = it * 2f; d.gain = g; d.applyVol() }
                        Text("GAIN", fontSize = 8.sp, color = Color.Gray) } }
                VFader(if (flt) "FLT." else n, if (idx < 0) d.vol else d.eq[idx], Modifier.weight(1f).fillMaxWidth(),
                    cap = if (flt) Color(0xFF6E737E) else Color(0xFFE9E9E9), ink = if (flt) Color.White else Color(0xFF4A4A4A)) {
                    if (idx < 0) { d.vol = it; d.applyVol() } else { d.eq[idx] = it; d.applyEq() } }
            }
        }
    }
}
