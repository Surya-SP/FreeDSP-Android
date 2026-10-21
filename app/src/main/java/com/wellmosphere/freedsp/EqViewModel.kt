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
    val active: String = "",
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
    val eq: Eq get() = profiles.firstOrNull { it.name == active }?.eq ?: Eq.FLAT

    /**
     * The profile that sounds like what the cable is playing. When the cable holds our last write, it must sound
     * exactly like that EQ (records can't tell 2.3 dB from 2.4 dB, or show the preamp); otherwise matching
     * records are our best answer. The active profile wins ties.
     */
    val playing: String? by lazy {
        val records = onCable ?: return@lazy null
        val ours = lastWritten?.takeIf { CableCoding.matches(it, records) }
        (listOfNotNull(profiles.firstOrNull { it.name == active }) + profiles)
            .firstOrNull { if (ours != null) it.eq.soundsLike(ours) else CableCoding.soundsLike(it.eq, records) }?.name
    }
    val activeOnCable get() = playing == active
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
    /** "My EQ" was created flat before the cable was ever read; the first read may replace it. */
    private var seeded = false

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        if (store.profiles.isEmpty()) { store.put(FIRST, Eq.FLAT); store.setActive(FIRST); seeded = true }
        _state.value = UiState(
            profiles = store.profiles,
            active = store.active?.takeIf { a -> store.profiles.any { it.name == a } } ?: store.profiles.first().name,
            mode = store.mode?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.SIMPLE,
            lastWritten = store.lastWritten,
            firstWrite = store.verifiedWrites == 0,
        )
        loadCurves()
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
        if (seeded) {
            seeded = false
            // First run: start from whatever the cable holds instead of a flat placeholder.
            val untouched = _state.value.let { it.profiles.size == 1 && it.eq == Eq.FLAT }
            if (untouched && _state.value.playing == null) {
                val fromCable = (store.lastWritten?.takeIf { CableCoding.matches(it, snap.bands) } ?: CableCoding.eqFrom(snap.bands)).withSafePreamp()
                replaceActive(fromCable)
            }
        }
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

    // ---- editing (always the active profile; the preamp follows automatically) ----

    fun setMode(mode: Mode) { _state.update { it.copy(mode = mode) }; store.setMode(mode.name) }
    fun select(index: Int) = _state.update { it.copy(selected = index) }
    fun updateBand(index: Int, band: Band) { _state.update { it.copy(selected = index) }; edit { it.withBand(index, band) } }
    fun setGain(index: Int, gainDb: Double) = edit { it.withBand(index, it.bands[index].copy(gainDb = gainDb)) }
    fun flatten() = load(_state.value.eq.flattened(), null, "Reset to flat.")

    private fun edit(change: (Eq) -> Eq) = replaceActive(change(_state.value.eq).withSafePreamp(), animate = false)

    private fun replaceActive(eq: Eq, animate: Boolean = true) {
        val name = _state.value.active
        _state.update { s -> s.copy(profiles = s.profiles.map { if (it.name == name) it.copy(eq = eq) else it }, jump = if (animate) s.jump + 1 else s.jump) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(400); store.put(name, eq) }
    }

    // ---- profiles ----

    fun switchTo(name: String) {
        if (name == _state.value.active) return
        flushSave()
        store.setActive(name)
        _state.update { it.copy(active = name, jump = it.jump + 1) }
    }
    /** Keeps what you're hearing under a new name; Reset to flat is one tap away if you want a blank one. */
    fun newProfile(name: String) = addProfile(unique(name.trim().ifEmpty { "New EQ" }), _state.value.eq)
    /** Trying a preset loads it into the profile you're on (with Undo); "+ New" or Duplicate keep extra ones. */
    fun applyPreset(p: Preset) = load(p.eq, p.name, "Loaded ${p.name}.")

    /**
     * Replaces the active profile's sound in one undoable step. A profile still named after a preset or a fit
     * takes [source] as its new name, so its chip says what it plays; names you chose are kept.
     */
    private fun load(eq: Eq, source: String?, message: String) {
        flushSave()
        val s = _state.value
        val before = Profile(s.active, s.eq)
        val name = source?.takeIf { isAutoName(s.active) && it != s.active }?.let { unique(it) } ?: s.active
        if (name != s.active) { store.rename(s.active, name); store.setActive(name) }
        store.put(name, eq.withSafePreamp())
        _state.update { it.copy(profiles = store.profiles, active = name, jump = it.jump + 1) }
        undo = before to name
        _state.update { it.copy(notice = Notice(message, undo = true)) }
    }
    private fun isAutoName(name: String) =
        " · " in name || Regex(""".+ fit( \d+)?""").matches(name) || Presets.ALL.any { name == it.name || Regex("""${Regex.escape(it.name)} \d+""").matches(name) }

    /** What the last load replaced, and the profile it went into. */
    private var undo: Pair<Profile, String>? = null
    fun undo() {
        val (before, now) = undo ?: return
        undo = null
        flushSave()
        if (_state.value.profiles.none { it.name == now }) return
        if (before.name != now) store.rename(now, before.name)
        store.put(before.name, before.eq)
        if (_state.value.active == now) store.setActive(before.name)
        _state.update { s -> s.copy(profiles = store.profiles, active = if (s.active == now) before.name else s.active, jump = s.jump + 1, notice = null) }
    }
    fun duplicate(name: String) = _state.value.profiles.firstOrNull { it.name == name }?.let { addProfile(unique("${it.name} copy"), it.eq) }
    fun rename(from: String, to: String) {
        val clean = to.trim()
        if (clean.isEmpty() || clean == from) return
        if (_state.value.profiles.any { it.name == clean }) return say("You already have a profile called \"$clean\".", true)
        flushSave()
        store.rename(from, clean)
        _state.update { s -> s.copy(profiles = store.profiles, active = if (s.active == from) clean else s.active) }
    }
    fun delete(name: String) {
        flushSave()
        store.delete(name)
        if (store.profiles.isEmpty()) store.put(FIRST, Eq.FLAT)
        val active = if (_state.value.active == name) store.profiles.first().name else _state.value.active
        store.setActive(active)
        _state.update { it.copy(profiles = store.profiles, active = active, jump = it.jump + 1) }
    }

    fun import(text: String, suggestedName: String?) = runCatching { SquigText.parse(text) }.fold(
        onSuccess = {
            addProfile(unique(suggestedName?.substringBeforeLast('.')?.trim()?.ifEmpty { null } ?: "Imported"), it.eq.withSafePreamp())
            say("Imported." + if (it.notes.isEmpty()) "" else " Heads up: ${it.notes.joinToString("; ")}.")
        },
        onFailure = { say(it.message ?: "That doesn't look like an EQ", true) },
    )
    fun exportText() = SquigText.export(_state.value.eq)
    val exportName get() = "${_state.value.active}.txt"

    private fun addProfile(name: String, eq: Eq) {
        flushSave()
        store.put(name, eq); store.setActive(name)
        _state.update { it.copy(profiles = store.profiles, active = name, jump = it.jump + 1) }
    }
    private fun unique(base: String): String {
        val names = _state.value.profiles.map { it.name }.toSet()
        return generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base $it" }.first { it !in names }
    }
    private fun flushSave() {
        if (saveJob?.isActive == true) { saveJob?.cancel(); store.put(_state.value.active, _state.value.eq) }
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

    /** Fits the bands that bring your IEM closest to the target, into the profile you're on (with Undo). */
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
            load(eq, "${m.name.replace(Regex("""\s*\([^)]*\)"""), "").take(28).trim()} fit", String.format(java.util.Locale.US,
                "Fitted with %d band%s: %.1f dB off the target, now %.1f dB.", used, if (used == 1) "" else "s", before, after))
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
        const val FIRST = "My EQ"
        const val DEFAULT_TARGET = "Harman in-ear 2019"
        const val SAVING = "Your cable is saving the EQ…"
    }
}
