package com.wellmosphere.freedsp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.ui.theme.Palette

/**
 * A labelled pick-one field with a dropdown: the options (current one ticked), an import action at the end,
 * and a remove button on options that [removable] allows.
 */
@Composable
fun ChoiceField(
    label: String,
    current: String?,
    placeholder: String,
    options: List<String>,
    importLabel: String,
    onPick: (String) -> Unit,
    onImport: () -> Unit,
    onRemove: (String) -> Unit,
    removable: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Raised)
                .border(1.dp, Palette.Line, RoundedCornerShape(16.dp))
                .springClick { if (options.isEmpty()) onImport() else open = true }.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                Text(current ?: placeholder, style = MaterialTheme.typography.bodyMedium, color = if (current == null) Palette.Signal else Palette.Text, maxLines = 1)
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Palette.Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Box(Modifier.size(20.dp)) { if (name == current) Icon(Icons.Default.Check, null, tint = Palette.Signal) } },
                    trailingIcon = if (removable(name)) ({
                        IconButton(onClick = { open = false; onRemove(name) }) { Icon(Icons.Default.Close, contentDescription = "Remove $name", tint = Palette.Muted) }
                    }) else null,
                    onClick = { open = false; onPick(name) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Palette.Line)
            DropdownMenuItem(text = { Text(importLabel, color = Palette.Signal) }, onClick = { open = false; onImport() })
        }
    }
}
