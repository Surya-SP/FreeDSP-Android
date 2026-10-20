package com.wellmosphere.freedsp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.ui.theme.Palette

/** A segmented choice whose highlight springs to the selected option. */
@Composable
fun PillSwitch(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 48.dp) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(Palette.Panel)
            .border(1.dp, Palette.Line, RoundedCornerShape(50)).padding(4.dp),
    ) {
        val each = maxWidth / options.size
        val x by animateDpAsState(each * selected, spring(dampingRatio = 0.75f, stiffness = 500f), label = "pill")
        Box(Modifier.offset(x = x).width(each).fillMaxHeight().clip(RoundedCornerShape(50)).background(Palette.Signal))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                val color by animateColorAsState(if (i == selected) Palette.Ink else Palette.Text, label = "pillText")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50))
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            if (i != selected) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = MaterialTheme.typography.labelLarge, color = color) }
            }
        }
    }
}
