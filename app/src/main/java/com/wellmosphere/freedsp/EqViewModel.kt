package com.wellmosphere.freedsp

import android.app.Application
import android.hardware.usb.UsbDevice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wellmosphere.freedsp.cable.CableCoding
import com.wellmosphere.freedsp.cable.CableLink
import com.wellmosphere.freedsp.cable.CableOps
import com.wellmosphere.freedsp.cable.Gen1
import com.wellmosphere.freedsp.cable.HidTakeover
import com.wellmosphere.freedsp.eq.AutoFit
import com.wellmosphere.freedsp.eq.Band
import com.wellmosphere.freedsp.eq.Curve
import com.wellmosphere.freedsp.eq.Eq
import com.wellmosphere.freedsp.eq.Preset
import com.wellmosphere.freedsp.eq.Presets
import com.wellmosphere.freedsp.eq.Response
import com.wellmosphere.freedsp.eq.SquigText
import com.wellmosphere.freedsp.store.Profile
import com.wellmosphere.freedsp.store.CurveStore
import com.wellmosphere.freedsp.store.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

enum class Cable { ABSENT, NEEDS_ACCESS, READY }
enum class Mode { SIMPLE, PARAMETRIC }

/** [undo] adds an Undo button; the notice then stays up a little longer. */
data class Notice(val text: String, val problem: Boolean = false, val undo: Boolean = false, val id: Long = System.nanoTime())

data class UiState(
    val profiles: List<Profile> = emptyList(),
    /** The saved profile you're on; ignored while a [preset] is applied. */
    val active: String = "",
    /** The applied preset, which wins over [active]. You can edit it; [presetEq] holds what you hear. */
    val preset: String? = null,
    val presetEq: Eq = Eq.FLAT,
    val mode: Mode = Mode.SIMPLE,
    val selected: Int = 0,
    val cable: Cable = Cable.ABSENT,
    /** The cable's band records from the last read or write; null until read. */
    val onCable: List<Gen1.Metadata>? = null,
    val lastWritten: Eq? = null,
    val busy: String? = null,
    val notice: Notice? = null,
    val restorePending: Boolean = false,
    val firstWrite: Boolean = true,
    /** Bumped when the whole EQ is replaced (profile switch, import), so the graph can animate the change. */
    val jump: Int = 0,
    val measurements: List<Curve> = emptyList(),
    val targets: List<Curve> = emptyList(),
    /** Your IEM's measurement and the target to aim for; both are needed to compare and auto-fit. */
    val measurement: Curve? = null,
    val target: Curve? = null,
    val fitting: Boolean = false,
) {
    val eq: Eq get() = if (preset != null) presetEq else profiles.firstOrNull { it.name == active }?.eq ?: Eq.FLAT
    val presetEdited get() = preset != null && Presets.named(preset)?.eq?.soundsLike(presetEq) != true
    /** What you're hearing, by name: the preset (marked when you've changed it) or the profile. */
    val title: String get() = preset?.let { if (presetEdited) "$it · edited" else it } ?: active

    /**
     * What the cable is playing, by name: what you're on first, then your profiles, then the presets. When the
     * cable holds our last write, it must sound exactly like that EQ (records can't tell 2.3 dB from 2.4 dB,
     * or show the preamp); otherwise matching records are our best answer.
     */
    val playing: String? by lazy {
        val records = onCable ?: return@lazy null
        val ours = lastWritten?.takeIf { CableCoding.matches(it, records) }
        (listOf(title to eq) + profiles.map { it.name to it.eq } + Presets.ALL.map { it.name to it.eq })
            .firstOrNull { (_, e) -> if (ours != null) e.soundsLike(ours) else CableCoding.soundsLike(e, records) }?.first
    }
    val activeOnCable get() = playing == title
    /** The cable holds something this app wrote, even if that profile has been edited since. */
    val cableHasOurWrite: Boolean by lazy { onCable != null && lastWritten?.let { CableCoding.matches(it, onCable) } == true }
    /** Only an EQ from elsewhere (e.g. Moondrop's app) is "not saved here"; it can't appear mid-edit. */
    val foreignOnCable get() = onCable != null && playing == null && !cableHasOurWrite
    val clips: Boolean by lazy { Response.clips(eq) }
}

class EqViewModel(app: Application) : AndroidViewModel(app) {
    private val link = CableLink(app)
    private val store = ProfileStore(app.filesDir)
    private val curves = CurveStore(app)
    private val usb = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var saveJob: Job? = null
    /** The cable restarts itself to save a write; set from the USB thread, read on main. */
    @Volatile private var awaitingRestart = false

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        // Earlier versions copied presets into profiles; untouched copies are just the preset again.
        val copies = store.profiles.mapNotNull { p ->
            Presets.ALL.firstOrNull { (p.name == it.name || p.name.matches(Regex("${Regex.escape(it.name)} \\d+"))) && p.eq.soundsLike(it.eq) }?.let { p to it }
        }
        copies.forEach { (p, _) -> store.delete(p.name) }
        val activeCopy = copies.firstOrNull { it.first.name == store.active }?.second
        if (store.preset == null && (activeCopy != null || store.profiles.none { it.name == store.active })) {
            val first = store.profiles.firstOrNull()
            if (activeCopy == null && first != null) store.setActive(first.name) else (activeCopy ?: Presets.FLAT).let { store.setPreset(it.name, it.eq) }
        }
        _state.value = UiState(
            mode = store.mode?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.SIMPLE,
            lastWritten = store.lastWritten,
            firstWrite = store.verifiedWrites == 0,
        )
        sync(jump = false)
        loadCurves()
    }

    /** Takes profiles, the active one and the preset from the store. */
    private fun sync(jump: Boolean = true) = _state.update {
        it.copy(profiles = store.profiles, active = store.active.orEmpty(), preset = store.preset,
            presetEq = store.presetEq ?: Eq.FLAT, jump = if (jump) it.jump + 1 else it.jump)
    }

    val device: UsbDevice? get() = link.find()

    // ---- cable ----

    /** Re-checks the cable; reads it once whenever it (re)appears. */
    fun refresh() {
        val d = link.find()
        val cable = when {
            d == null -> Cable.ABSENT
            link.manager.hasPermission(d) -> Cable.READY
            else -> Cable.NEEDS_ACCESS
        }
        if (d != null && awaitingRestart) awaitingRestart = false
        _state.update {
            it.copy(cable = cable, restorePending = link.restorePending, onCable = if (d == null) null else it.onCable,
                busy = if (it.busy == SAVING && !awaitingRestart) null else it.busy)
        }
        val s = _state.value
        if (cable == Cable.READY && s.onCable == null && s.busy == null && !s.restorePending) read()
    }

    fun detached() { link.detached(); refresh() }

    fun read() = cableJob("Reading your cable…") { d ->
        val snap = link.session(d, CableOps::read)
        _state.update { it.copy(onCable = snap.bands) }
    }

    fun write() {
        val eq = _state.value.eq
        val plan = runCatching { CableCoding.plan(eq) }.getOrElse { return say(it.message ?: "Can't write this EQ", true) }
        cableJob("Writing to your cable…") { d ->
            val result = link.session(d) { t -> CableOps.write(t, plan) }
            if (result.verified) {
                store.written(eq)
                awaitingRestart = true
                _state.update { it.copy(onCable = result.readBack.bands, lastWritten = eq, firstWrite = false) }
                say("Written and checked. The cable restarts for a moment to keep it.")
                viewModelScope.launch {
                    delay(6000) // In case the restart is never seen.
                    if (awaitingRestart) { awaitingRestart = false; refresh() }
                }
            } else {
                _state.update { it.copy(onCable = result.readBack.bands) }
                say("The cable reads back differently on band ${result.mismatchedBands.joinToString()}. Try writing again.", true)
            }
        }
    }

    fun restore() = cableJob("Restoring your cable's buttons…") { d ->
        say(when (link.restore(d)) {
            HidTakeover.Restore.RESTORED, HidTakeover.Restore.ALREADY_BOUND -> "All good, the cable's buttons work again."
            HidTakeover.Restore.HELD_ELSEWHERE -> "Another app is using the cable. Unplug and replug it to reset."
            HidTakeover.Restore.FAILED -> "That didn't work. Unplug and replug the cable to reset it."
        })
    }

    /** Keeps the cable's current EQ as a profile. Gains come back in whole dB and the preamp is recalculated. */
    fun saveCableAsProfile() {
        val records = _state.value.onCable ?: return
        val eq = runCatching { CableCoding.eqFrom(records).withSafePreamp() }.getOrElse { return say(it.message ?: "Can't read that EQ", true) }
        addProfile(unique("From cable"), eq)
        say("Saved what's on your cable. Its gains are rounded to whole dB, which is how the cable stores them.")
    }

    // ---- editing (whatever you're on: the preset or the profile; the preamp follows automatically) ----

    fun setMode(mode: Mode) { _state.update { it.copy(mode = mode) }; store.setMode(mode.name) }
    fun select(index: Int) = _state.update { it.copy(selected = index) }
    fun updateBand(index: Int, band: Band) { _state.update { it.copy(selected = index) }; edit { it.withBand(index, band) } }
    fun setGain(index: Int, gainDb: Double) = edit { it.withBand(index, it.bands[index].copy(gainDb = gainDb)) }
    /** On a preset this is just the Flat preset; on a profile it clears the bands, with Undo. */
    fun flatten() {
        val s = _state.value
        if (s.preset != null) return applyPreset(Presets.FLAT)
        val before = snapshot(s.active)
        replaceActive(s.eq.flattened())
        offerUndo(before, "Reset \"${s.active}\" to flat.")
    }

    private fun edit(change: (Eq) -> Eq) {
        val eq = change(_state.value.eq).withSafePreamp()
        val preset = _state.value.preset ?: return replaceActive(eq, animate = false)
        _state.update { it.copy(presetEq = eq) }
        saveLater { store.setPreset(preset, eq) }
    }

    private fun replaceActive(eq: Eq, animate: Boolean = true) {
        val name = _state.value.active
        _state.update { s -> s.copy(profiles = s.profiles.map { if (it.name == name) it.copy(eq = eq) else it }, jump = if (animate) s.jump + 1 else s.jump) }
        saveLater { store.put(name, eq) }
    }
    private fun saveLater(save: () -> Unit) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(400); save() }
    }
    private fun flushSave() {
        if (saveJob?.isActive != true) return
        saveJob?.cancel()
        val s = _state.value
        if (s.preset != null) store.setPreset(s.preset, s.presetEq) else store.put(s.active, s.eq)
    }

    // ---- presets and profiles: one of them is what you hear ----

    fun applyPreset(p: Preset) { flushSave(); store.setPreset(p.name, p.eq); sync() }
    fun switchTo(name: String) {
        if (name == _state.value.active && _state.value.preset == null) return
        flushSave()
        store.setActive(name)
        sync()
    }
    /** Keeps what you're hearing (a preset, maybe edited, or a profile) as a new profile. */
    fun newProfile(name: String) = addProfile(unique(name.trim().ifEmpty { "My EQ" }), _state.value.eq)
    fun duplicate(name: String) = _state.value.profiles.firstOrNull { it.name == name }?.let { addProfile(unique("${it.name} copy"), it.eq) }
    fun rename(from: String, to: String) {
        val clean = to.trim()
        if (clean.isEmpty() || clean == from) return
        if (_state.value.profiles.any { it.name == clean }) return say("You already have a profile called \"$clean\".", true)
        flushSave()
        store.rename(from, clean)
        sync(jump = false)
    }
    /** Deleting the profile you're on moves you to the next one, or to Flat when none are left. */
    fun delete(name: String) {
        flushSave()
        store.delete(name)
        if (store.preset == null && store.active == name) {
            store.profiles.firstOrNull()?.let { store.setActive(it.name) } ?: store.setPreset(Presets.FLAT.name, Presets.FLAT.eq)
        }
        sync()
    }

    fun import(text: String, suggestedName: String?) = runCatching { SquigText.parse(text) }.fold(
        onSuccess = {
            addProfile(unique(suggestedName?.substringBeforeLast('.')?.trim()?.ifEmpty { null } ?: "Imported"), it.eq.withSafePreamp())
            say("Imported." + if (it.notes.isEmpty()) "" else " Heads up: ${it.notes.joinToString("; ")}.")
        },
        onFailure = { say(it.message ?: "That doesn't look like an EQ", true) },
    )
    fun exportText() = SquigText.export(_state.value.eq)
    val exportName get() = "${_state.value.title}.txt"

    /** Saves [eq] as profile [name] (replacing one with that name) and makes it what you hear. */
    private fun addProfile(name: String, eq: Eq) {
        flushSave()
        store.put(name, eq); store.setActive(name)
        sync()
    }
    private fun unique(base: String): String {
        val names = _state.value.profiles.map { it.name }.toSet()
        return generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base $it" }.first { it !in names }
    }

    // ---- undo, for the steps that replace a profile's sound ----

    /** Where you were, and what profile [profile] held (null if it didn't exist yet). */
    private class Snapshot(val preset: String?, val presetEq: Eq, val active: String, val profile: String, val profileEq: Eq?)
    private var undo: Snapshot? = null
    private fun snapshot(profile: String) = _state.value.let { s ->
        Snapshot(s.preset, s.presetEq, s.active, profile, s.profiles.firstOrNull { it.name == profile }?.eq)
    }
    private fun offerUndo(before: Snapshot, message: String) {
        undo = before
        _state.update { it.copy(notice = Notice(message, undo = true)) }
    }
    fun undo() {
        val u = undo ?: return
        undo = null
        flushSave()
        if (u.profileEq == null) store.delete(u.profile) else store.put(u.profile, u.profileEq)
        if (store.profiles.any { it.name == u.active }) store.setActive(u.active)
        if (u.preset != null) store.setPreset(u.preset, u.presetEq)
        sync()
        _state.update { it.copy(notice = null) }
    }

    // ---- measurements, targets and auto-fit ----

    private fun loadCurves() {
        val ms = curves.list(CurveStore.Kind.MEASUREMENT)
        val ts = curves.list(CurveStore.Kind.TARGET)
        _state.update { s ->
            s.copy(measurements = ms, targets = ts,
                measurement = ms.firstOrNull { it.name == curves.selected(CurveStore.Kind.MEASUREMENT) } ?: ms.firstOrNull(),
                target = ts.firstOrNull { it.name == curves.selected(CurveStore.Kind.TARGET) }
                    ?: ts.firstOrNull { it.name == DEFAULT_TARGET } ?: ts.firstOrNull())
        }
    }
    fun pickCurve(kind: CurveStore.Kind, name: String) { curves.select(kind, name); loadCurves() }
    fun importCurve(kind: CurveStore.Kind, text: String, fileName: String?) {
        // squig.link names channels "Name [L].txt"; keep just the name.
        val name = fileName?.substringBeforeLast('.')?.replace(Regex("""\s*\[[^\]]*]$"""), "") ?: "Imported"
        runCatching { curves.add(kind, name, text) }.fold(
            onSuccess = { c -> curves.select(kind, c.name); loadCurves(); say("Added \"${c.name}\".") },
            onFailure = { say(it.message ?: "That file isn't a frequency response", true) },
        )
    }
    fun deleteCurve(kind: CurveStore.Kind, name: String) {
        curves.delete(kind, name)
        if (curves.selected(kind) == name) curves.select(kind, null)
        loadCurves()
    }
    fun isBuiltInTarget(name: String) = curves.isBuiltIn(name)

    /** Fits the bands that bring your IEM closest to the target and keeps them as "<IEM> fit", with Undo. */
    fun autoFit() {
        val s = _state.value
        val m = s.measurement ?: return say("Add your IEM's measurement first.", true)
        val t = s.target ?: return say("Pick a target first.", true)
        if (s.fitting) return
        _state.update { it.copy(fitting = true) }
        viewModelScope.launch {
            val (eq, before, after) = withContext(Dispatchers.Default) {
                val eq = AutoFit.fit(m, t)
                Triple(eq, AutoFit.error(m, t, Eq.FLAT), AutoFit.error(m, t, eq))
            }
            _state.update { it.copy(fitting = false) }
            val used = eq.bands.count { it.gainDb != 0.0 }
            val name = "${m.name.replace(Regex("""\s*\([^)]*\)"""), "").take(28).trim()} fit"
            val snap = snapshot(name)
            addProfile(name, eq)
            offerUndo(snap, String.format(java.util.Locale.US, "%s \"%s\" with %d band%s: %.1f dB off the target, now %.1f dB.",
                if (snap.profileEq == null) "Saved" else "Updated", name, used, if (used == 1) "" else "s", before, after))
        }
    }

    // ---- messages ----

    fun say(text: String, problem: Boolean = false) = _state.update { it.copy(notice = Notice(text, problem)) }
    fun dismissNotice(id: Long) = _state.update { if (it.notice?.id == id) it.copy(notice = null) else it }

    private fun cableJob(label: String, work: (UsbDevice) -> Unit) {
        if (_state.value.busy != null) return
        val d = link.find() ?: return say("Plug in your FreeDSP cable first.", true)
        _state.update { it.copy(busy = label) }
        viewModelScope.launch {
            val r = withContext(usb) { runCatching { work(d) } }
            r.exceptionOrNull()?.let { Log.w(CableLink.TAG, "$label failed", it); say(it.message ?: "Something went wrong", true) }
            _state.update { it.copy(busy = if (awaitingRestart) SAVING else null, restorePending = link.restorePending) }
        }
    }

    override fun onCleared() { flushSave(); usb.close() }

    private companion object {
        const val DEFAULT_TARGET = "Harman in-ear 2019"
        const val SAVING = "Your cable is saving the EQ…"
    }
}
