package com.wellmosphere.freedsp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.store.Profile
import com.wellmosphere.freedsp.ui.theme.Palette

/** What a long press on a profile can do. */
class ProfileActions(
    val rename: (String) -> Unit,
    val duplicate: (String) -> Unit,
    val export: (String) -> Unit,
    val delete: (String) -> Unit,
)

/**
 * Profiles as chips: tap to switch, press and hold for more. A dot marks the one the cable is playing.
 * The last chip makes a new profile.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileBar(
    profiles: List<Profile>,
    active: String,
    playing: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(profiles, key = { it.name }) { p ->
            var menu by remember { mutableStateOf(false) }
            val on = p.name == active
            val bg by animateColorAsState(if (on) Palette.Text else Palette.Panel, label = "profileBg")
            val fg by animateColorAsState(if (on) Palette.Ink else Palette.Text, label = "profileFg")
            Box(Modifier.animateItem()) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(bg)
                        .border(1.dp, if (on) bg else Palette.Line, RoundedCornerShape(50))
                        .combinedClickable(onClick = { onSelect(p.name) }, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (p.name == playing) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Palette.Signal))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(p.name, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; actions.rename(p.name) })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; actions.duplicate(p.name) })
                    DropdownMenuItem(text = { Text("Export as text") }, onClick = { menu = false; onSelect(p.name); actions.export(p.name) })
                    DropdownMenuItem(text = { Text("Delete", color = Palette.Alert) }, onClick = { menu = false; actions.delete(p.name) })
                }
            }
        }
        item(key = "+new") {
            Text(
                "+  New", style = MaterialTheme.typography.labelLarge, color = Palette.Signal,
                modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Palette.Signal.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .combinedClickable(onClick = onNew).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
