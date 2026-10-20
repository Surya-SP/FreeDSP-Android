package com.wellmosphere.freedsp.ui.components

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
import kotlin.math.floor
import kotlin.math.roundToInt

/** Simple mode: one vertical gain slider per band, in half-dB steps. Double-tap a slider to zero it. */
@Composable
fun GraphicEq(eq: Eq, onGain: (Int, Double) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        eq.bands.forEachIndexed { i, b ->
            GainSlider(b.gainDb, hz(b.freqHz), { onGain(i, it) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun GainSlider(gain: Double, label: String, onChange: (Double) -> Unit, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(gain)
    val shown by animateFloatAsState(gain.toFloat(), spring(stiffness = 900f), label = "gain")
    val range = Limits.GAIN
    fun gainAt(y: Float, h: Float, pad: Float): Double {
        val t = ((y - pad) / (h - 2 * pad)).coerceIn(0f, 1f)
        return (range.endInclusive - t * (range.endInclusive - range.start)).let { (it * 2).roundToInt() / 2.0 }
    }
    fun set(v: Double) {
        if (v == current) return
        if (floor(v) != floor(current)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onChange(v)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (gain == 0.0) "0" else String.format(Locale.US, "%+.1f", gain),
            style = Numbers.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
            color = if (gain == 0.0) Palette.Muted else Palette.Signal,
        )
        Canvas(
            Modifier.fillMaxWidth().height(220.dp).padding(vertical = 6.dp)
                .pointerInput(Unit) {
                    val pad = 12.dp.toPx()
                    detectVerticalDragGestures { change, _ -> change.consume(); set(gainAt(change.position.y, size.height.toFloat(), pad)) }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { set(0.0) }) },
        ) {
            val pad = 12.dp.toPx()
            val track = 6.dp.toPx()
            val x = size.width / 2
            fun yOf(g: Double) = (pad + (range.endInclusive - g) / (range.endInclusive - range.start) * (size.height - 2 * pad)).toFloat()
            drawRoundRect(Palette.Raised, Offset(x - track / 2, pad), Size(track, size.height - 2 * pad), CornerRadius(track))
            val zero = yOf(0.0); val y = yOf(shown.toDouble())
            drawLine(Palette.Muted.copy(alpha = 0.5f), Offset(x - 9.dp.toPx(), zero), Offset(x + 9.dp.toPx(), zero), 1.5f)
            if (shown != 0f) drawRoundRect(Palette.Signal, Offset(x - track / 2, minOf(zero, y)), Size(track, kotlin.math.abs(zero - y)), CornerRadius(track))
            drawCircle(Palette.Ink, 11.dp.toPx(), Offset(x, y))
            drawCircle(if (shown != 0f) Palette.Signal else Palette.Text, 8.dp.toPx(), Offset(x, y))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
    }
}
