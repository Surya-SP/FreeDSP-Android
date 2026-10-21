package com.wellmosphere.freedsp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.wellmosphere.freedsp.ui.components.PillSwitch
import com.wellmosphere.freedsp.ui.components.PresetsPage
import com.wellmosphere.freedsp.ui.components.rememberBreath
import com.wellmosphere.freedsp.ui.components.rememberShimmer
import com.wellmosphere.freedsp.ui.components.springClick
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.Cable
import com.wellmosphere.freedsp.EqViewModel
import com.wellmosphere.freedsp.Mode
import com.wellmosphere.freedsp.UiState
import com.wellmosphere.freedsp.store.CurveStore
import com.wellmosphere.freedsp.eq.FilterType
import com.wellmosphere.freedsp.eq.Limits
import com.wellmosphere.freedsp.ui.components.BandPicker
import com.wellmosphere.freedsp.ui.components.GestureHint
import com.wellmosphere.freedsp.ui.components.GraphicEq
import com.wellmosphere.freedsp.ui.components.NameDialog
import com.wellmosphere.freedsp.ui.components.NoticeBar
import com.wellmosphere.freedsp.ui.components.Panel
import com.wellmosphere.freedsp.ui.components.ProfileActions
import com.wellmosphere.freedsp.ui.components.SoundPicker
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
fun EqScreen(state: UiState, vm: EqViewModel, onAllowAccess: () -> Unit, onImport: (CurveStore.Kind?) -> Unit, onExport: () -> Unit) {
    var confirmWrite by rememberSaveable { mutableStateOf(false) }
    var naming by remember { mutableStateOf<Naming?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var presetsOpen by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    // A picked band owns every drag; the page only scrolls again once it's released by a tap elsewhere.
    var picked by remember { mutableStateOf(false) }
    LaunchedEffect(state.mode, state.title) { picked = false }
    val pick = { i: Int -> vm.select(i); picked = true }
    val band = state.eq.bands[state.selected]

    Box(Modifier.fillMaxSize().background(Palette.Ink).pointerInput(Unit) { detectTapGestures { picked = false } }) {
        AmbientGlow(Modifier.fillMaxWidth().height(520.dp))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = !picked).statusBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(state, onAllowAccess, Modifier.padding(horizontal = 20.dp)) { close ->
                DropdownMenuItem(text = { Text("Open EQ file") }, onClick = { close(); onImport(null) })
                DropdownMenuItem(text = { Text("Paste EQ text") }, onClick = { close(); clipboard.getText()?.text?.let { vm.import(it, null) } ?: vm.say("Copy some parametric EQ text first.", true) })
                DropdownMenuItem(text = { Text("Read the cable again") }, enabled = state.cable == Cable.READY && state.busy == null, onClick = { close(); vm.read() })
            }
            SoundPicker(
                state.preset, state.presetEdited, state.profiles, state.active, state.playing, state.activeOnCable,
                onPresets = { presetsOpen = true }, onSelect = vm::switchTo, onSave = { naming = Naming(null) },
                actions = ProfileActions(rename = { naming = Naming(it) }, duplicate = vm::duplicate,
                    export = { vm.switchTo(it); onExport() }, delete = { deleting = it }),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AnimatedVisibility(state.restorePending) { RestoreCard(busy = state.busy != null, onRestore = vm::restore) }
                AnimatedVisibility(state.foreignOnCable && state.busy == null) { ForeignEqCard(vm::saveCableAsProfile) }
                Panel {
                    ResponseGraph(state.eq, state.jump, state.selected, pick, vm::updateBand,
                        Modifier.fillMaxWidth().height(230.dp), interactive = state.mode == Mode.PARAMETRIC,
                        picked = picked && state.mode == Mode.PARAMETRIC, onRelease = { picked = false },
                        overlay = state.measurement?.let { m -> state.target?.let { t -> m to t } })
                    Spacer(Modifier.height(8.dp))
                    if (state.mode == Mode.PARAMETRIC) {
                        GestureHint(
                            if (picked) "Band ${state.selected + 1} picked · drag anywhere on the graph · tap outside to scroll"
                            else "Tap a point to pick it, then drag to move it", active = picked)
                        Spacer(Modifier.height(6.dp))
                    }
                    PreampLine(state)
                }
                PillSwitch(listOf("Simple", "Parametric"), state.mode.ordinal, { vm.setMode(Mode.entries[it]) })
                AnimatedContent(
                    state.mode, label = "mode",
                    transitionSpec = {
                        val dir = if (targetState == Mode.PARAMETRIC) 1 else -1
                        (slideInHorizontally { it / 6 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 * dir } + fadeOut())
                    },
                ) { mode ->
                    when (mode) {
                        Mode.SIMPLE -> Panel {
                            GestureHint(
                                if (picked) "${hz(band.freqHz)} Hz picked · slide up or down · double-tap for 0 · tap outside to scroll"
                                else "Tap a slider to pick it, then slide", active = picked)
                            Spacer(Modifier.height(10.dp))
                            GraphicEq(state.eq, if (picked) state.selected else null, pick, vm::setGain)
                        }
                        Mode.PARAMETRIC -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BandPicker(state.eq, state.selected, { vm.select(it); picked = false })
                            BandEditor(state, vm)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = vm::flatten) { Text("Reset to flat", color = Palette.Muted) }
                }
                FitPanel(state, vm, onImport = { onImport(it) })
            }
            Spacer(Modifier.height(104.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NoticeBar(state.notice, vm::dismissNotice, vm::undo)
            WriteBar(state, onAllowAccess = onAllowAccess, onWrite = { confirmWrite = true })
        }
        // Content scrolls under the transparent status bar; fade it out behind the clock and icons.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(
            Brush.verticalGradient(listOf(Palette.Ink, Palette.Ink.copy(alpha = 0.85f)))))
        AnimatedVisibility(presetsOpen, enter = slideInVertically { it / 4 } + fadeIn(), exit = slideOutVertically { it / 4 } + fadeOut()) {
            PresetsPage(
                current = { it.name == state.preset },
                onPick = { vm.applyPreset(it); presetsOpen = false }, onClose = { presetsOpen = false },
            )
        }
    }

    naming?.let { n ->
        NameDialog(if (n.from == null) "Save as a profile" else "Rename profile", n.from ?: state.preset?.let { "My $it" }.orEmpty(), if (n.from == null) "Save" else "Rename") { name ->
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
        title = { Text("Write \"${state.title}\" to your cable?") },
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
                state.cableHasOurWrite -> Palette.Warm to "Your changes aren't on the cable yet"
                else -> Palette.Warm to "Playing an EQ that isn't saved here"
            }
            val color by animateColorAsState(dot, label = "status")
            // Breathes while the cable is playing what you see; steady otherwise.
            val glow = if (state.activeOnCable) rememberBreath() else 1f
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Box(Modifier.size(8.dp).graphicsLayer { alpha = glow }.clip(CircleShape).background(color))
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

/** A slow, faint drift of the accent colour behind the top of the screen. */
@Composable
private fun AmbientGlow(modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "ambient").animateFloat(0f, 1f, infiniteRepeatable(tween(14_000), RepeatMode.Reverse), label = "drift")
    Canvas(modifier) {
        drawRect(Brush.radialGradient(listOf(Palette.Signal.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width * (0.2f + 0.6f * t), size.height * 0.35f), radius = size.width * 0.9f))
    }
}

@Composable
private fun BandEditor(state: UiState, vm: EqViewModel) {
    val i = state.selected
    val band = state.eq.bands[i]
    Panel {
        Text("Band ${i + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
        PillSwitch(FilterType.entries.map { it.label }, band.type.ordinal, { vm.updateBand(i, band.copy(type = FilterType.entries[it])) }, height = 42.dp)
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
        state.activeOnCable -> "\"${state.title}\" is on your cable  ✓" to null
        else -> "Write \"${state.title}\" to cable" to onWrite
    }
    // A soft pop and a buzz when a write lands, never on launch.
    val haptics = LocalHapticFeedback.current
    val pop = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(state.lastWritten) }
    LaunchedEffect(state.lastWritten) {
        if (state.lastWritten != seen) {
            seen = state.lastWritten
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            pop.animateTo(1.04f, tween(120)); pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f))
        }
    }
    val fill = when {
        state.busy != null -> rememberShimmer(Palette.Raised, Palette.Signal.copy(alpha = 0.28f))
        action != null -> SolidColor(Palette.Signal)
        else -> SolidColor(Palette.Raised)
    }
    val text = if (action != null) Palette.Ink else if (state.activeOnCable) Palette.Signal else Palette.Muted
    Row(
        Modifier.fillMaxWidth().height(58.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(RoundedCornerShape(20.dp)).background(fill)
            .then(if (action != null) Modifier.springClick(onClick = action) else Modifier),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.busy != null) CircularProgressIndicator(Modifier.size(18.dp).padding(end = 0.dp), strokeWidth = 2.dp, color = Palette.Text)
        AnimatedContent(label, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "write") {
            Text(it, style = MaterialTheme.typography.labelLarge, color = text, maxLines = 1, modifier = Modifier.padding(start = if (state.busy != null) 10.dp else 0.dp))
        }
    }
}

private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
