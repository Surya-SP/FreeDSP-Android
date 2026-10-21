package com.wellmosphere.freedsp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.Notice
import com.wellmosphere.freedsp.ui.theme.Palette
import kotlinx.coroutines.delay

/** A short message that slides up and leaves on its own; problems and undoable ones stay longer. Tap to dismiss. */
@Composable
fun NoticeBar(notice: Notice?, onDismiss: (Long) -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(notice?.id) {
        notice ?: return@LaunchedEffect
        delay(if (notice.problem || notice.undo) 7000 else 3500)
        onDismiss(notice.id)
    }
    val last = remember { mutableStateOf(notice) }
    if (notice != null) last.value = notice
    AnimatedVisibility(notice != null, modifier, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val n = last.value ?: return@AnimatedVisibility
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (n.problem) Palette.Warm else Palette.Raised).clickable { onDismiss(n.id) }
                .padding(start = 16.dp, end = if (n.undo) 4.dp else 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(n.text, style = MaterialTheme.typography.bodyMedium, color = if (n.problem) Palette.Ink else Palette.Text, modifier = Modifier.weight(1f))
            if (n.undo) TextButton(onClick = onUndo, modifier = Modifier.height(32.dp)) { Text("Undo", color = Palette.Signal) }
        }
    }
}
