package com.wellmosphere.freedsp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellmosphere.freedsp.eq.Band
import com.wellmosphere.freedsp.eq.Eq
import com.wellmosphere.freedsp.eq.Response
import com.wellmosphere.freedsp.ui.theme.Palette
import kotlin.math.log10
import kotlin.math.pow

private const val RANGE_DB = 12.0
private val GRID_HZ = listOf(50, 100, 200, 500, 1000, 2000, 5000, 10000)
private val LABEL_HZ = mapOf(100 to "100", 1000 to "1k", 10000 to "10k")
private val CURVE_HZ = Response.logSpaced(181)

/**
 * The EQ curve (bands only; the preamp is the dashed line). When [interactive], tap a node to pick a band
 * and drag it to move frequency and gain; otherwise bands show as quiet dots. [jump] changes animate the
 * whole curve; drags follow the finger exactly.
 */
@Composable
fun ResponseGraph(
    eq: Eq,
    jump: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMove: (Int, Band) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val shown = rememberEqTransition(eq, jump)
    val latest by rememberUpdatedState(eq)
    val haptics = LocalHapticFeedback.current
    val text = rememberTextMeasurer()
    val pulse by animateFloatAsState(selected.toFloat(), tween(220), label = "selection")

    Canvas(
        if (!interactive) modifier else modifier
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    nearest(latest, p, size.width.toFloat(), size.height.toFloat(), 40.dp.toPx())?.let {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(it)
                    }
                }
            }
            .pointerInput(Unit) {
                var active = -1
                detectDragGestures(
                    onDragStart = { p ->
                        active = nearest(latest, p, size.width.toFloat(), size.height.toFloat(), 48.dp.toPx()) ?: -1
                        if (active >= 0) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSelect(active) }
                    },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                ) { change, _ ->
                    if (active < 0) return@detectDragGestures
                    change.consume()
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    val band = latest.bands[active]
                    onMove(active, band.copy(freqHz = freqAtX(change.position.x, w), gainDb = gainAtY(change.position.y, h)))
                }
            },
    ) {
        grid(text)
        curve(shown)
        preamp(shown.preampDb)
        shown.bands.forEachIndexed { i, b ->
            if (interactive) node(i, b, isSelected = i == selected, emphasis = (1f - kotlin.math.abs(pulse - i)).coerceIn(0f, 1f), text = text)
            else drawCircle(if (b.gainDb == 0.0) Palette.Muted.copy(alpha = 0.6f) else Palette.Signal, 3.5.dp.toPx(),
                Offset(xOf(b.freqHz, size.width), yOf(b.gainDb, size.height)))
        }
    }
}

private fun xOf(f: Double, w: Float) = (log10(f / 20.0) / 3.0 * w).toFloat()
private fun yOf(db: Double, h: Float) = (h / 2 - db / RANGE_DB * (h / 2 - 18f)).toFloat()
private fun freqAtX(x: Float, w: Float) = 20.0 * 10.0.pow((x / w).coerceIn(0f, 1f) * 3.0)
private fun gainAtY(y: Float, h: Float) = (h / 2 - y) / (h / 2 - 18f) * RANGE_DB

private fun nearest(eq: Eq, p: Offset, w: Float, h: Float, reach: Float): Int? = eq.bands.indices
    .map { it to (Offset(xOf(eq.bands[it].freqHz, w), yOf(eq.bands[it].gainDb, h)) - p).getDistance() }
    .filter { it.second <= reach }.minByOrNull { it.second }?.first

private fun DrawScope.grid(text: androidx.compose.ui.text.TextMeasurer) {
    val label = TextStyle(color = Palette.Muted, fontSize = 10.sp)
    for (hz in GRID_HZ) {
        val x = xOf(hz.toDouble(), size.width)
        drawLine(Palette.Line.copy(alpha = if (hz in LABEL_HZ) 0.9f else 0.45f), Offset(x, 0f), Offset(x, size.height), 1f)
        LABEL_HZ[hz]?.let { drawText(text, it, Offset(x + 4f, size.height - 16.dp.toPx()), label) }
    }
    for (db in listOf(-12, -6, 0, 6, 12)) {
        val y = yOf(db.toDouble(), size.height)
        drawLine(if (db == 0) Palette.Muted.copy(alpha = 0.5f) else Palette.Line.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1f)
        if (db != 0) drawText(text, if (db > 0) "+$db" else "$db", Offset(6f, y - 14.dp.toPx()), label)
    }
}

private fun DrawScope.curve(eq: Eq) {
    val db = Response.curve(eq, CURVE_HZ)
    val line = Path(); val fill = Path()
    val zero = yOf(0.0, size.height)
    for (i in db.indices) {
        val x = i / (db.size - 1f) * size.width
        val y = yOf(db[i].coerceIn(-RANGE_DB - 2, RANGE_DB + 2), size.height)
        if (i == 0) { line.moveTo(x, y); fill.moveTo(x, zero); fill.lineTo(x, y) } else { line.lineTo(x, y); fill.lineTo(x, y) }
    }
    fill.lineTo(size.width, zero); fill.close()
    drawPath(fill, Brush.verticalGradient(listOf(Palette.Signal.copy(alpha = 0.22f), Palette.Signal.copy(alpha = 0.02f), Palette.Signal.copy(alpha = 0.22f))))
    drawPath(line, Palette.Signal.copy(alpha = 0.18f), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
    drawPath(line, Palette.Signal, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.preamp(db: Double) {
    if (db == 0.0) return
    val y = yOf(db, size.height)
    drawLine(Palette.Warm.copy(alpha = 0.8f), Offset(0f, y), Offset(size.width, y), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
}

private fun DrawScope.node(i: Int, b: Band, isSelected: Boolean, emphasis: Float, text: androidx.compose.ui.text.TextMeasurer) {
    val c = Offset(xOf(b.freqHz, size.width), yOf(b.gainDb, size.height))
    val r = (9 + 4 * emphasis).dp.toPx()
    if (isSelected) drawCircle(Palette.Signal.copy(alpha = 0.18f), r * 2.1f, c)
    drawCircle(if (isSelected) Palette.Signal else Palette.Raised, r, c)
    drawCircle(if (isSelected) Palette.Signal else Palette.Muted, r, c, style = Stroke(1.5.dp.toPx()))
    val label = text.measure("${i + 1}", TextStyle(color = if (isSelected) Palette.Ink else Palette.Text, fontSize = 11.sp))
    drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f))
}

private class TransitionMemory(var shown: Eq, var from: Eq)

/** Blends from the previously shown EQ to [target] whenever [jump] changes; otherwise returns [target] as is. */
@Composable
private fun rememberEqTransition(target: Eq, jump: Int): Eq {
    val progress = remember { Animatable(1f) }
    val memory = remember { TransitionMemory(target, target) }
    LaunchedEffect(jump) {
        memory.from = memory.shown
        progress.snapTo(0f)
        progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    val t = progress.value.toDouble()
    val result = if (t >= 1.0) target else Eq(memory.from.bands.zip(target.bands) { a, b ->
        Band(
            freqHz = 10.0.pow(log10(a.freqHz) + (log10(b.freqHz) - log10(a.freqHz)) * t),
            gainDb = a.gainDb + (b.gainDb - a.gainDb) * t,
            q = a.q + (b.q - a.q) * t,
            type = if (t < 0.5) a.type else b.type,
        )
    }, memory.from.preampDb + (target.preampDb - memory.from.preampDb) * t)
    memory.shown = result
    return result
}
