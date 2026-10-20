package com.wellmosphere.freedsp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.eq.Eq
import com.wellmosphere.freedsp.eq.Limits
import com.wellmosphere.freedsp.ui.theme.Numbers
import com.wellmosphere.freedsp.ui.theme.Palette
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Simple mode: one vertical gain slider per band. Tap a slider to pick it ([picked]), then slide; only the
 * picked slider responds to dragging, so the page can scroll freely otherwise. Double-tap the picked one to zero it.
 */
@Composable
fun GraphicEq(eq: Eq, picked: Int?, onPick: (Int) -> Unit, onGain: (Int, Double) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        eq.bands.forEachIndexed { i, b ->
            GainSlider(b.gainDb, hz(b.freqHz), isPicked = picked == i, dimmed = picked != null && picked != i,
                onPick = { onPick(i) }, onChange = { onGain(i, it) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun GainSlider(
    gain: Double, label: String, isPicked: Boolean, dimmed: Boolean,
    onPick: () -> Unit, onChange: (Double) -> Unit, modifier: Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(gain)
    val change by rememberUpdatedState(onChange)
    val shown by animateFloatAsState(gain.toFloat(), spring(stiffness = 900f), label = "gain")
    val glow by animateFloatAsState(if (isPicked) 1f else 0f, label = "picked")
    val alpha by animateFloatAsState(if (dimmed) 0.45f else 1f, label = "dim")
    val labelColor by animateColorAsState(if (isPicked) Palette.Signal else Palette.Muted, label = "label")
    val range = Limits.GAIN
    val span = range.endInclusive - range.start

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (gain == 0.0) "0" else String.format(Locale.US, "%+.1f", gain),
            style = Numbers.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
            color = (if (gain == 0.0 && !isPicked) Palette.Muted else Palette.Signal).copy(alpha = alpha),
        )
        Canvas(
            Modifier.fillMaxWidth().height(220.dp).padding(vertical = 6.dp)
                // Not picked: a tap picks it and drags fall through to the page scroll.
                .pointerInput(isPicked) {
                    if (isPicked) {
                        val pad = 12.dp.toPx()
                        var exact = current
                        detectVerticalDragGestures(onDragStart = { exact = current }) { c, dy ->
                            c.consume()
                            exact = (exact - dy / (size.height - 2 * pad) * span).coerceIn(range)
                            val snapped = (exact * 2).roundToInt() / 2.0
                            if (snapped != current) {
                                if (floor(snapped) != floor(current)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                change(snapped)
                            }
                        }
                    }
                }
                .pointerInput(isPicked) {
                    // Taps are consumed here, so tapping the picked slider never releases it.
                    if (isPicked) detectTapGestures(onDoubleTap = { change(0.0) })
                    else detectTapGestures(onTap = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onPick() })
                },
        ) {
            val pad = 12.dp.toPx()
            val track = (6 + 2 * glow).dp.toPx()
            val x = size.width / 2
            fun yOf(g: Double) = (pad + (range.endInclusive - g) / span * (size.height - 2 * pad)).toFloat()
            if (glow > 0f) drawRoundRect(Palette.Signal.copy(alpha = 0.10f * glow), Offset(x - 16.dp.toPx(), 0f), Size(32.dp.toPx(), size.height), CornerRadius(16.dp.toPx()))
            drawRoundRect(Palette.Raised.copy(alpha = alpha), Offset(x - track / 2, pad), Size(track, size.height - 2 * pad), CornerRadius(track))
            val zero = yOf(0.0); val y = yOf(shown.toDouble())
            drawLine(Palette.Muted.copy(alpha = 0.5f * alpha), Offset(x - 9.dp.toPx(), zero), Offset(x + 9.dp.toPx(), zero), 1.5f)
            if (shown != 0f) drawRoundRect(Palette.Signal.copy(alpha = alpha), Offset(x - track / 2, minOf(zero, y)), Size(track, abs(zero - y)), CornerRadius(track))
            val r = (8 + 3 * glow).dp.toPx()
            if (glow > 0f) drawCircle(Palette.Signal.copy(alpha = 0.25f * glow), r * 1.9f, Offset(x, y))
            drawCircle(Palette.Ink, r + 3.dp.toPx(), Offset(x, y))
            drawCircle((if (shown != 0f || isPicked) Palette.Signal else Palette.Text).copy(alpha = alpha), r, Offset(x, y))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor.copy(alpha = alpha))
    }
}
