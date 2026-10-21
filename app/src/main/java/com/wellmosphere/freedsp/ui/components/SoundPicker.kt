package com.wellmosphere.freedsp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.store.Profile
import com.wellmosphere.freedsp.ui.theme.Palette

/** What a profile's ⋮ can do. */
class ProfileActions(
    val rename: (String) -> Unit,
    val duplicate: (String) -> Unit,
    val export: (String) -> Unit,
    val delete: (String) -> Unit,
)

/**
 * Two dropdowns, Preset and Profile; you hear exactly one of them. The one in use is lit and names what's
 * applied; the other just says what it holds. Presets open the full presets page ([onPresets]); profiles
 * open a menu with their actions and a way to save what you hear. A dot marks what the cable is playing.
 */
@Composable
fun SoundPicker(
    preset: String?,
    presetEdited: Boolean,
    profiles: List<Profile>,
    active: String,
    /** The profile the cable is playing, for the menu, and whether it's playing what you're on. */
    playing: String?,
    currentOnCable: Boolean,
    onPresets: () -> Unit,
    onSelect: (String) -> Unit,
    onSave: () -> Unit,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
) {
    val onPreset = preset != null
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SourceField(
            "Preset", if (onPreset) (if (presetEdited) "$preset · edited" else preset!!) else "Presets",
            lit = onPreset, playing = onPreset && currentOnCable,
            modifier = Modifier.weight(1f), onClick = onPresets,
        )
        Box(Modifier.weight(1f)) {
            var open by remember { mutableStateOf(false) }
            SourceField(
                "Profile",
                when {
                    !onPreset -> active
                    presetEdited -> "Save your changes"
                    else -> "Profiles"
                },
                lit = !onPreset, playing = !onPreset && currentOnCable, open = open,
                hint = onPreset && presetEdited, onClick = { open = true },
            )
            ProfileMenu(open, profiles, active.takeIf { !onPreset }, playing, { open = false }, onSelect, onSave, actions)
        }
    }
}

@Composable
private fun SourceField(
    label: String, value: String, lit: Boolean, playing: Boolean, modifier: Modifier = Modifier,
    open: Boolean = false, hint: Boolean = false, onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (lit) Palette.Signal.copy(alpha = 0.14f) else Palette.Panel, label = "fieldBg")
    val edge by animateColorAsState(if (lit) Palette.Signal.copy(alpha = 0.55f) else Palette.Line, label = "fieldEdge")
    val fg by animateColorAsState(if (lit) Palette.Text else if (hint) Palette.Signal else Palette.Muted, label = "fieldFg")
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "caret")
    Row(
        modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(18.dp)).background(bg)
            .border(1.dp, edge, RoundedCornerShape(18.dp)).springClick(onClick = onClick)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // The one not in use is a single word ("Presets"); its label would only repeat it.
            if (lit) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (lit) Palette.Signal else Palette.Muted)
                AnimatedVisibility(playing, enter = fadeIn(), exit = fadeOut()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Signal))
                        Text(" on cable", style = MaterialTheme.typography.labelSmall, color = Palette.Signal)
                    }
                }
            }
            AnimatedContent(value, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "fieldValue") {
                Text(it, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = if (lit) Palette.Text else Palette.Muted, modifier = Modifier.rotate(turn))
    }
}

@Composable
private fun ProfileMenu(
    open: Boolean, profiles: List<Profile>, current: String?, playing: String?,
    close: () -> Unit, onSelect: (String) -> Unit, onSave: () -> Unit, actions: ProfileActions,
) {
    // Which profile has its actions showing; one at a time, inline, so there's never a menu on a menu.
    var tools by remember { mutableStateOf<String?>(null) }
    val dismiss = { tools = null; close() }
    DropdownMenu(
        expanded = open, onDismissRequest = dismiss, modifier = Modifier.width(280.dp),
        shape = RoundedCornerShape(20.dp), containerColor = Palette.Raised, border = BorderStroke(1.dp, Palette.Line),
    ) {
        Text("Your profiles", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        if (profiles.isEmpty()) Text(
            "Nothing saved yet. Find a sound you like, then keep it here.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        profiles.forEach { p ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (p.name == current) Palette.Text else Palette.Text.copy(alpha = 0.85f))
                        if (p.name == playing) Text("On your cable", style = MaterialTheme.typography.labelSmall, color = Palette.Signal)
                    }
                },
                leadingIcon = { Box(Modifier.size(20.dp)) { if (p.name == current) Icon(Icons.Default.Check, null, tint = Palette.Signal) } },
                trailingIcon = {
                    IconButton(onClick = { tools = if (tools == p.name) null else p.name }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More for ${p.name}", tint = if (tools == p.name) Palette.Signal else Palette.Muted)
                    }
                },
                onClick = { dismiss(); onSelect(p.name) },
            )
            AnimatedVisibility(tools == p.name) {
                // Four equal slots across the menu, so every action fits whatever the font size.
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 4.dp)) {
                    Tool("Rename", Modifier.weight(1f)) { dismiss(); actions.rename(p.name) }
                    Tool("Copy", Modifier.weight(1f)) { dismiss(); actions.duplicate(p.name) }
                    Tool("Export", Modifier.weight(1f)) { dismiss(); actions.export(p.name) }
                    Tool("Delete", Modifier.weight(1f), Palette.Alert) { dismiss(); actions.delete(p.name) }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Palette.Line)
        DropdownMenuItem(text = { Text("+  Save what you hear as a profile", color = Palette.Signal) }, onClick = { dismiss(); onSave() })
    }
}

@Composable
private fun Tool(label: String, modifier: Modifier, color: Color = Palette.Text, onClick: () -> Unit) =
    TextButton(onClick = onClick, modifier = modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, softWrap = false)
    }
