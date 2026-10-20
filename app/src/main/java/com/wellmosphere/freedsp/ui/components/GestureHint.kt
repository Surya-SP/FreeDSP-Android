package com.wellmosphere.freedsp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.ui.theme.Palette

/** One line of how-to under an interactive control; glows while something is picked. */
@Composable
fun GestureHint(text: String, active: Boolean, modifier: Modifier = Modifier) {
    val dot by animateColorAsState(if (active) Palette.Signal else Palette.Line, label = "hintDot")
    Row(modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        AnimatedContent(text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "hint") {
            Text(it, style = MaterialTheme.typography.labelMedium, color = if (active) Palette.Text else Palette.Muted, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
