package com.wellmosphere.freedsp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.Cable
import com.wellmosphere.freedsp.EqViewModel
import com.wellmosphere.freedsp.UiState
import com.wellmosphere.freedsp.eq.FilterType
import com.wellmosphere.freedsp.eq.Limits
import com.wellmosphere.freedsp.ui.components.BandPicker
import com.wellmosphere.freedsp.ui.components.NoticeBar
import com.wellmosphere.freedsp.ui.components.Panel
import com.wellmosphere.freedsp.ui.components.ResponseGraph
import com.wellmosphere.freedsp.ui.components.ValueControl
import com.wellmosphere.freedsp.ui.components.hz
import com.wellmosphere.freedsp.ui.components.parseNumber
import com.wellmosphere.freedsp.ui.theme.Palette
import java.util.Locale
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun EqScreen(state: UiState, vm: EqViewModel, onAllowAccess: () -> Unit, onImport: () -> Unit, onExport: () -> Unit) {
    var profilesOpen by rememberSaveable { mutableStateOf(false) }
    var confirmWrite by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(state, onRead = vm::read, onProfiles = { profilesOpen = true }, onAllowAccess = onAllowAccess)
            AnimatedVisibility(state.restorePending) { RestoreCard(busy = state.busy != null, onRestore = vm::restore) }
            Panel(Modifier.padding(top = 2.dp)) {
                ResponseGraph(state.eq, state.jump, state.selected, vm::select, vm::updateBand, Modifier.fillMaxWidth().height(250.dp))
                Spacer(Modifier.height(10.dp))
                Headroom(state, onAuto = vm::autoHeadroom)
            }
            BandPicker(state.eq, state.selected, vm::select)
            BandEditor(state, vm)
            Panel {
                PreampControl(state, vm)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = vm::flatten) { Text("Make flat") }
                    if (state.unsaved) TextButton(onClick = vm::revert) { Text("Undo changes") }
                }
            }
            Spacer(Modifier.height(96.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NoticeBar(state.notice, vm::dismissNotice)
            WriteBar(state, onAllowAccess = onAllowAccess, onFix = vm::autoHeadroom, onWrite = { confirmWrite = true })
        }
    }

    if (profilesOpen) ProfilesSheet(state.profiles, onDismiss = { profilesOpen = false }, onSave = vm::saveProfile,
        onLoad = { vm.loadProfile(it); profilesOpen = false }, onDelete = vm::deleteProfile,
        onImportFile = onImport, onPaste = vm::import, onExport = onExport)

    if (confirmWrite) AlertDialog(
        onDismissRequest = { confirmWrite = false },
        title = { Text("Write to your cable?") },
        text = {
            Text(buildString {
                append("This replaces the EQ stored on the cable. It takes about a second: the inline buttons pause briefly, then the app reads it back to check.")
                if (state.firstWrite) append("\n\nFirst write on this cable: take your IEMs out and turn the volume down, just in case.")
            })
        },
        confirmButton = { Button(onClick = { confirmWrite = false; vm.write() }) { Text("Write") } },
        dismissButton = { TextButton(onClick = { confirmWrite = false }) { Text("Not now") } },
    )
}

@Composable
private fun Header(state: UiState, onRead: () -> Unit, onProfiles: () -> Unit, onAllowAccess: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("FreeDSP", style = MaterialTheme.typography.headlineMedium)
            val (dot, line) = when {
                state.busy != null -> Palette.Warm to state.busy
                state.cable == Cable.ABSENT -> Palette.Muted to "Plug in your cable"
                state.cable == Cable.NEEDS_ACCESS -> Palette.Warm to "Tap to allow USB access"
                state.unsaved -> Palette.Warm to "Changes not on the cable yet"
                else -> Palette.Signal to "Cable connected"
            }
            val color by animateColorAsState(dot, label = "status")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                AnimatedContent(line, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "statusText") {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (state.cable == Cable.NEEDS_ACCESS) TextButton(onClick = onAllowAccess, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("Allow access") }
        }
        IconButton(onClick = onRead, enabled = state.cable == Cable.READY && state.busy == null) {
            Icon(Icons.Default.Refresh, contentDescription = "Read the cable again")
        }
        IconButton(onClick = onProfiles) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Profiles") }
    }
}

@Composable
private fun RestoreCard(busy: Boolean, onRestore: () -> Unit) = Panel(color = MaterialTheme.colorScheme.errorContainer) {
    Text("Your cable's buttons may be paused", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
    Text("The app closed while it was talking to the cable. Restore them here, or unplug and replug the cable.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(vertical = 8.dp))
    FilledTonalButton(onClick = onRestore, enabled = !busy) { Text("Restore buttons") }
}

@Composable
private fun Headroom(state: UiState, onAuto: () -> Unit) {
    val peak = state.loudestDb
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (state.clips) "Loudest point +${f1(peak)} dB, this could distort" else "Loudest point ${f1(peak)} dB, no distortion",
            style = MaterialTheme.typography.labelMedium, color = if (state.clips) Palette.Warm else Palette.Muted, modifier = Modifier.weight(1f),
        )
        if (state.clips) TextButton(onClick = onAuto) { Text("Fix it") }
    }
}

@Composable
private fun BandEditor(state: UiState, vm: EqViewModel) {
    val i = state.selected
    val band = state.eq.bands[i]
    Panel {
        Text("Band ${i + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            FilterType.entries.forEachIndexed { n, type ->
                SegmentedButton(selected = band.type == type, onClick = { vm.updateBand(i, band.copy(type = type)) },
                    shape = SegmentedButtonDefaults.itemShape(n, FilterType.entries.size)) { Text(type.label) }
            }
        }
        Spacer(Modifier.height(12.dp))
        ValueControl("Frequency", band.freqHz, display = { "${hz(it)} Hz" },
            toSlider = { (log10(it / 20) / 3).toFloat() }, fromSlider = { 20 * 10.0.pow(it * 3.0) },
            step = { v, d -> v * 2.0.pow(d / 12.0) }, parse = ::parseNumber,
            onChange = { vm.updateBand(i, band.copy(freqHz = it)) })
        ValueControl("Gain", band.gainDb, display = { "${if (it > 0) "+" else ""}${f1(it)} dB" },
            toSlider = { ((it - Limits.GAIN.start) / (Limits.GAIN.endInclusive - Limits.GAIN.start)).toFloat() },
            fromSlider = { Limits.GAIN.start + it * (Limits.GAIN.endInclusive - Limits.GAIN.start) },
            step = { v, d -> v + d * 0.5 }, parse = ::parseNumber,
            onChange = { vm.updateBand(i, band.copy(gainDb = it)) })
        val q = Limits.qRange(band.type)
        ValueControl("Width (Q)", band.q, display = { String.format(Locale.US, "%.2f", it) },
            toSlider = { (ln(it / q.start) / ln(q.endInclusive / q.start)).toFloat() },
            fromSlider = { q.start * (q.endInclusive / q.start).pow(it.toDouble()) },
            step = { v, d -> v * 1.1.pow(d) }, parse = ::parseNumber,
            onChange = { vm.updateBand(i, band.copy(q = it)) })
    }
}

@Composable
private fun PreampControl(state: UiState, vm: EqViewModel) = ValueControl(
    "Preamp", state.eq.preampDb, display = { "${f1(it)} dB" },
    toSlider = { ((it - Limits.PREAMP.start) / (Limits.PREAMP.endInclusive - Limits.PREAMP.start)).toFloat() },
    fromSlider = { Limits.PREAMP.start + it * (Limits.PREAMP.endInclusive - Limits.PREAMP.start) },
    step = { v, d -> v + d * 0.5 }, parse = ::parseNumber, onChange = vm::setPreamp,
)

@Composable
private fun WriteBar(state: UiState, onAllowAccess: () -> Unit, onFix: () -> Unit, onWrite: () -> Unit) {
    val (label, action) = when {
        state.busy != null -> state.busy to null
        state.cable == Cable.ABSENT -> "Plug in your cable to write" to null
        state.cable == Cable.NEEDS_ACCESS -> "Allow USB access" to onAllowAccess
        state.restorePending -> "Restore the buttons first" to null
        state.clips -> "Fix distortion to write" to onFix
        else -> "Write to cable" to onWrite
    }
    Button(
        onClick = { action?.invoke() }, enabled = action != null,
        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (state.clips) Palette.Warm else Palette.Signal,
            disabledContainerColor = Palette.Raised, disabledContentColor = Palette.Muted),
    ) {
        if (state.busy != null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Muted)
        AnimatedContent(label, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "write") {
            Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = if (state.busy != null) 10.dp else 0.dp))
        }
    }
}

private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
