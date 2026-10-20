package com.wellmosphere.freedsp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.Cable
import com.wellmosphere.freedsp.EqViewModel
import com.wellmosphere.freedsp.Mode
import com.wellmosphere.freedsp.UiState
import com.wellmosphere.freedsp.eq.FilterType
import com.wellmosphere.freedsp.eq.Limits
import com.wellmosphere.freedsp.ui.components.BandPicker
import com.wellmosphere.freedsp.ui.components.GraphicEq
import com.wellmosphere.freedsp.ui.components.NameDialog
import com.wellmosphere.freedsp.ui.components.NoticeBar
import com.wellmosphere.freedsp.ui.components.Panel
import com.wellmosphere.freedsp.ui.components.ProfileActions
import com.wellmosphere.freedsp.ui.components.ProfileBar
import com.wellmosphere.freedsp.ui.components.ResponseGraph
import com.wellmosphere.freedsp.ui.components.ValueControl
import com.wellmosphere.freedsp.ui.components.hz
import com.wellmosphere.freedsp.ui.components.parseNumber
import com.wellmosphere.freedsp.ui.theme.Palette
import java.util.Locale
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow

/** A pending name prompt: a new profile, or renaming [from]. */
private data class Naming(val from: String?)

@Composable
fun EqScreen(state: UiState, vm: EqViewModel, onAllowAccess: () -> Unit, onImport: () -> Unit, onExport: () -> Unit) {
    var confirmWrite by rememberSaveable { mutableStateOf(false) }
    var naming by remember { mutableStateOf<Naming?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(state, onAllowAccess, Modifier.padding(horizontal = 20.dp)) { close ->
                DropdownMenuItem(text = { Text("Open EQ file") }, onClick = { close(); onImport() })
                DropdownMenuItem(text = { Text("Paste EQ text") }, onClick = { close(); clipboard.getText()?.text?.let { vm.import(it, null) } ?: vm.say("Copy some parametric EQ text first.", true) })
                DropdownMenuItem(text = { Text("Read the cable again") }, enabled = state.cable == Cable.READY && state.busy == null, onClick = { close(); vm.read() })
            }
            ProfileBar(
                state.profiles, state.active, state.playing, onSelect = vm::switchTo, onNew = { naming = Naming(null) },
                actions = ProfileActions(rename = { naming = Naming(it) }, duplicate = vm::duplicate, export = { onExport() }, delete = { deleting = it }),
                modifier = Modifier.padding(start = 20.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AnimatedVisibility(state.restorePending) { RestoreCard(busy = state.busy != null, onRestore = vm::restore) }
                AnimatedVisibility(state.onCable != null && state.playing == null && state.busy == null) { ForeignEqCard(vm::saveCableAsProfile) }
                Panel {
                    ResponseGraph(state.eq, state.jump, state.selected, vm::select, vm::updateBand,
                        Modifier.fillMaxWidth().height(230.dp), interactive = state.mode == Mode.PARAMETRIC)
                    Spacer(Modifier.height(8.dp))
                    PreampLine(state)
                }
                ModeSwitch(state.mode, vm::setMode)
                AnimatedContent(
                    state.mode, label = "mode",
                    transitionSpec = {
                        val dir = if (targetState == Mode.PARAMETRIC) 1 else -1
                        (slideInHorizontally { it / 6 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 * dir } + fadeOut())
                    },
                ) { mode ->
                    when (mode) {
                        Mode.SIMPLE -> Panel { GraphicEq(state.eq, vm::setGain) }
                        Mode.PARAMETRIC -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BandPicker(state.eq, state.selected, vm::select)
                            BandEditor(state, vm)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = vm::flatten) { Text("Reset to flat", color = Palette.Muted) }
                }
            }
            Spacer(Modifier.height(104.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NoticeBar(state.notice, vm::dismissNotice)
            WriteBar(state, onAllowAccess = onAllowAccess, onWrite = { confirmWrite = true })
        }
    }

    naming?.let { n ->
        NameDialog(if (n.from == null) "New profile" else "Rename profile", n.from ?: "", if (n.from == null) "Create" else "Rename") { name ->
            naming = null
            if (name != null) if (n.from == null) vm.newProfile(name) else vm.rename(n.from, name)
        }
    }
    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"$name\"?") },
            text = { Text("This only removes it from your phone. Whatever is on the cable stays there.") },
            confirmButton = { TextButton(onClick = { deleting = null; vm.delete(name) }) { Text("Delete", color = Palette.Alert) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
    if (confirmWrite) AlertDialog(
        onDismissRequest = { confirmWrite = false },
        title = { Text("Write \"${state.active}\" to your cable?") },
        text = {
            Text(buildString {
                append("It replaces the EQ stored on the cable and takes a couple of seconds. The inline buttons pause briefly and the cable restarts once to keep it.")
                if (state.firstWrite) append("\n\nFirst write on this cable: take your IEMs out and turn the volume down, just in case.")
            })
        },
        confirmButton = { Button(onClick = { confirmWrite = false; vm.write() }) { Text("Write") } },
        dismissButton = { TextButton(onClick = { confirmWrite = false }) { Text("Not now") } },
    )
}

@Composable
private fun Header(state: UiState, onAllowAccess: () -> Unit, modifier: Modifier, menu: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("FreeDSP", style = MaterialTheme.typography.headlineMedium)
            val (dot, line) = when {
                state.busy != null -> Palette.Warm to state.busy
                state.cable == Cable.ABSENT -> Palette.Muted to "Plug in your cable"
                state.cable == Cable.NEEDS_ACCESS -> Palette.Warm to "USB access needed"
                state.playing != null -> Palette.Signal to "Playing \"${state.playing}\""
                state.onCable == null -> Palette.Signal to "Cable connected"
                else -> Palette.Warm to "Playing an EQ that isn't saved here"
            }
            val color by animateColorAsState(dot, label = "status")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                AnimatedContent(line, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "statusText") {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (state.cable == Cable.NEEDS_ACCESS) FilledTonalButton(onClick = onAllowAccess) { Text("Allow") }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
        }
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
private fun ForeignEqCard(onSave: () -> Unit) = Panel {
    Text("Your cable has an EQ that isn't saved here", style = MaterialTheme.typography.titleMedium)
    Text("Keep it as a profile so you can come back to it. Pick any profile above to replace it.",
        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(vertical = 6.dp))
    FilledTonalButton(onClick = onSave) { Text("Save it as a profile") }
}

@Composable
private fun PreampLine(state: UiState) = Text(
    when {
        state.clips -> "Too much boost to keep it clean. Lower a few bands."
        state.eq.preampDb == 0.0 -> "Preamp 0 dB · nothing here can distort"
        else -> "Preamp ${f1(state.eq.preampDb)} dB · set for you so nothing distorts"
    },
    style = MaterialTheme.typography.labelMedium, color = if (state.clips) Palette.Warm else Palette.Muted,
    modifier = Modifier.padding(horizontal = 4.dp),
)

@Composable
private fun ModeSwitch(mode: Mode, onChange: (Mode) -> Unit) = SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
    listOf(Mode.SIMPLE to "Simple", Mode.PARAMETRIC to "Parametric").forEachIndexed { i, (m, label) ->
        SegmentedButton(selected = mode == m, onClick = { onChange(m) }, shape = SegmentedButtonDefaults.itemShape(i, 2), icon = {}) { Text(label) }
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
                    shape = SegmentedButtonDefaults.itemShape(n, FilterType.entries.size), icon = {}) { Text(type.label) }
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
private fun WriteBar(state: UiState, onAllowAccess: () -> Unit, onWrite: () -> Unit) {
    val (label, action) = when {
        state.busy != null -> state.busy to null
        state.cable == Cable.ABSENT -> "Plug in your cable to write" to null
        state.cable == Cable.NEEDS_ACCESS -> "Allow USB access" to onAllowAccess
        state.restorePending -> "Restore the buttons first" to null
        state.clips -> "Lower a few bands to write" to null
        state.activeOnCable -> "\"${state.active}\" is on your cable  ✓" to null
        else -> "Write \"${state.active}\" to cable" to onWrite
    }
    Button(
        onClick = { action?.invoke() }, enabled = action != null,
        modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Signal, contentColor = Palette.Ink,
            disabledContainerColor = Palette.Raised, disabledContentColor = if (state.activeOnCable) Palette.Signal else Palette.Muted),
    ) {
        if (state.busy != null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Muted)
        AnimatedContent(label, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "write") {
            Text(it, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(start = if (state.busy != null) 10.dp else 0.dp))
        }
    }
}

private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
