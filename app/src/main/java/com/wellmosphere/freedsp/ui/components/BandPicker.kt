package com.wellmosphere.freedsp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.eq.Eq
import com.wellmosphere.freedsp.ui.theme.Palette

/** The nine bands as chips: number, frequency and a dot when the band is doing something. */
@Composable
fun BandPicker(eq: Eq, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        eq.bands.forEachIndexed { i, b ->
            val on = i == selected
            val bg by animateColorAsState(if (on) Palette.Signal else Palette.Raised, label = "chip")
            val fg by animateColorAsState(if (on) Palette.Ink else Palette.Text, label = "chipText")
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).background(bg).clickable { onSelect(i) }
                    .widthIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${i + 1}${if (b.gainDb != 0.0) " •" else ""}", style = MaterialTheme.typography.labelLarge, color = fg)
                Text(hz(b.freqHz), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
            }
        }
    }
}

fun hz(f: Double): String = if (f >= 1000) {
    val k = f / 1000
    if (k >= 10 || k == Math.floor(k)) "${"%.0f".format(k)}k" else "${"%.1f".format(k)}k"
} else "${f.toInt()}"
