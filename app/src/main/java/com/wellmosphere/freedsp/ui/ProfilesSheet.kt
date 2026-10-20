package com.wellmosphere.freedsp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.store.Profile
import com.wellmosphere.freedsp.ui.theme.Palette
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesSheet(
    profiles: List<Profile>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onLoad: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onImportFile: () -> Unit,
    onPaste: (String) -> Unit,
    onExport: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var name by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Palette.Panel) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Profiles", style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text("Name what you're hearing") })
                Button(onClick = { onSave(name); name = "" }, enabled = name.isNotBlank()) { Text("Save") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onImportFile) { Text("Open file") }
                FilledTonalButton(onClick = { clipboard.getText()?.text?.let(onPaste); onDismiss() }) { Text("Paste") }
                FilledTonalButton(onClick = onExport) { Text("Export") }
            }
            Text("Open or paste parametric EQ text, like an AutoEQ or squig.link export.", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            if (profiles.isEmpty()) {
                Text("Nothing saved yet. Save the EQ you're hearing, or bring one in from AutoEQ.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(vertical = 24.dp))
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                items(profiles, key = { it.name }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onLoad(p) }.padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            val active = p.eq.bands.count { it.gainDb != 0.0 }
                            Text("$active band${if (active == 1) "" else "s"} · preamp ${String.format(Locale.US, "%.1f", p.eq.preampDb)} dB",
                                style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        }
                        IconButton(onClick = { onDelete(p) }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${p.name}", tint = Palette.Muted) }
                    }
                }
            }
        }
    }
}
