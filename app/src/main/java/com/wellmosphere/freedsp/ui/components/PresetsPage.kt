package com.wellmosphere.freedsp.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.eq.Eq
import com.wellmosphere.freedsp.eq.Preset
import com.wellmosphere.freedsp.eq.Presets
import com.wellmosphere.freedsp.eq.Response
import com.wellmosphere.freedsp.ui.theme.Palette
import kotlinx.coroutines.delay

private val MINI_HZ = Response.logSpaced(64)

/**
 * Built-in presets as a full-screen page: it scrolls on its own and closes only with ✕ or Back, so a long
 * scroll can never be mistaken for a dismiss. [current] marks the applied preset, [onCable] the one the cable plays.
 * Show it inside an AnimatedVisibility for the slide-up.
 */
@Composable
fun PresetsPage(current: (Preset) -> Boolean, onCable: String?, onPick: (Preset) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    // Cards cascade in once per opening; scrolling them back into view doesn't replay it.
    val entered = remember { mutableSetOf<String>() }
    LazyVerticalGrid(
        GridCells.Fixed(2),
        // Swallow taps that land between cards so they never reach the screen underneath.
        modifier = Modifier.fillMaxSize().background(Palette.Ink).pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Presets", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close presets") }
                }
                Text("Tap one to try it, then write it to your cable. Every preset stays inside what the cable can play, and none of them distort.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
            }
        }
        itemsIndexed(Presets.ALL, key = { _, p -> p.name }) { i, p ->
            PresetCard(p, current(p), p.name == onCable, delayMs = if (entered.add(p.name)) 60L + i * 28L else null) { onPick(p) }
        }
    }
}

@Composable
private fun PresetCard(p: Preset, current: Boolean, playing: Boolean, delayMs: Long?, onClick: () -> Unit) {
    val enter = remember { Animatable(if (delayMs == null) 1f else 0f) }
    LaunchedEffect(Unit) { if (delayMs != null) { delay(delayMs); enter.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) } }
    Column(
        Modifier.graphicsLayer { alpha = enter.value; translationY = (1 - enter.value) * 40f }
            .clip(RoundedCornerShape(20.dp)).background(Palette.Panel)
            .border(1.dp, if (current) Palette.Signal.copy(alpha = 0.5f) else Palette.Line, RoundedCornerShape(20.dp))
            .springClick(onClick = onClick).padding(14.dp),
    ) {
        MiniCurve(p.eq, Modifier.fillMaxWidth().height(44.dp))
        Text(p.name + if (current) "  ✓" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
        Text(if (playing) "On your cable" else p.blurb, style = MaterialTheme.typography.labelMedium, color = if (playing) Palette.Signal else Palette.Muted,
            maxLines = 2, minLines = 2, modifier = Modifier.padding(top = 2.dp))
    }
}

/** A small, label-free drawing of an EQ's shape. */
@Composable
fun MiniCurve(eq: Eq, modifier: Modifier = Modifier) {
    val db = remember(eq) { Response.curve(eq, MINI_HZ) }
    Canvas(modifier) {
        val mid = size.height / 2
        fun y(v: Double) = (mid - v / 7.0 * (mid - 3f)).toFloat()
        drawLine(Palette.Line, Offset(0f, mid), Offset(size.width, mid), 1f)
        val line = Path()
        db.forEachIndexed { i, v -> val x = i / (db.size - 1f) * size.width; if (i == 0) line.moveTo(x, y(v)) else line.lineTo(x, y(v)) }
        val fill = Path().apply { addPath(line); lineTo(size.width, mid); lineTo(0f, mid); close() }
        drawPath(fill, Brush.verticalGradient(listOf(Palette.Signal.copy(alpha = 0.25f), Palette.Signal.copy(alpha = 0.03f))))
        drawPath(line, Palette.Signal, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}
