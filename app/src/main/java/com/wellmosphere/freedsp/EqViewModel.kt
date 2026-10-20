package com.wellmosphere.freedsp

import android.app.Application
import android.hardware.usb.UsbDevice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wellmosphere.freedsp.cable.CableCoding
import com.wellmosphere.freedsp.cable.CableLink
import com.wellmosphere.freedsp.cable.CableOps
import com.wellmosphere.freedsp.cable.HidTakeover
import com.wellmosphere.freedsp.eq.Band
import com.wellmosphere.freedsp.eq.Eq
import com.wellmosphere.freedsp.eq.Response
import com.wellmosphere.freedsp.eq.SquigText
import com.wellmosphere.freedsp.store.Profile
import com.wellmosphere.freedsp.store.ProfileStore
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

enum class Cable { ABSENT, NEEDS_ACCESS, READY }

data class Notice(val text: String, val problem: Boolean = false, val id: Long = System.nanoTime())

data class UiState(
    val eq: Eq = Eq.FLAT,
    val selected: Int = 0,
    val cable: Cable = Cable.ABSENT,
    /** What the cable holds, as best the app knows; null until read. */
    val onCable: Eq? = null,
    val busy: String? = null,
    val notice: Notice? = null,
    val profiles: List<Profile> = emptyList(),
    val restorePending: Boolean = false,
    val firstWrite: Boolean = true,
    /** Bumped when the whole EQ is replaced (read, profile, import), so the graph can animate the change. */
    val jump: Int = 0,
) {
    /** Loudest point of what you'd hear (bands plus preamp); computed once per state. */
    val loudestDb: Double by lazy { Response.peak(eq) + eq.preampDb }
    /** Same rule CableCoding.plan enforces: never louder than flat. */
    val clips get() = loudestDb > 1e-9
    val unsaved get() = onCable != null && eq != onCable
}

class EqViewModel(app: Application) : AndroidViewModel(app) {
    private val link = CableLink(app)
    private val store = ProfileStore(app.filesDir)
    private val usb = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val _state = MutableStateFlow(UiState(profiles = store.profiles, firstWrite = store.verifiedWrites == 0))
    val state = _state.asStateFlow()

    val device: UsbDevice? get() = link.find()

    /** Re-checks the cable; reads it once when it first becomes available. */
    fun refresh() {
        val d = link.find()
        val cable = when {
            d == null -> Cable.ABSENT
            link.manager.hasPermission(d) -> Cable.READY
            else -> Cable.NEEDS_ACCESS
        }
        _state.update { it.copy(cable = cable, restorePending = link.restorePending, onCable = if (d == null) null else it.onCable) }
        val s = _state.value
        if (cable == Cable.READY && s.onCable == null && s.busy == null && !s.restorePending) read()
    }

    fun detached() { link.detached(); refresh() }

    fun read() = cableJob("Reading your cable…") { d ->
        val snap = link.session(d, CableOps::read)
        val shown = store.lastWritten?.takeIf { CableCoding.matches(it, snap.bands) } ?: CableCoding.eqFrom(snap.bands)
        _state.update { it.copy(onCable = shown, eq = shown, jump = it.jump + 1) }
    }

    fun write() {
        val eq = _state.value.eq
        val plan = runCatching { CableCoding.plan(eq) }.getOrElse { return say(it.message ?: "Can't write this EQ", true) }
        cableJob("Writing to your cable…") { d ->
            val result = link.session(d) { t ->
                CableOps.write(t, plan) { frame, ack -> Log.i(CableLink.TAG, "${frame.label}: sent ${frame.bytes.hex()} ack ${ack.hex()}") }
            }
            if (result.verified) {
                store.written(eq)
                _state.update { it.copy(onCable = eq, firstWrite = false) }
                say("Saved on your cable and checked.")
            } else {
                _state.update { it.copy(onCable = CableCoding.eqFrom(result.readBack.bands)) }
                say("The cable reads back differently on band ${result.mismatchedBands.joinToString()}. Your EQ is still here, try writing again.", true)
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

    fun select(index: Int) = _state.update { it.copy(selected = index) }
    fun updateBand(index: Int, band: Band) = _state.update { it.copy(eq = it.eq.withBand(index, band), selected = index) }
    fun setPreamp(db: Double) = _state.update { it.copy(eq = it.eq.withPreamp(db)) }
    fun autoHeadroom() = _state.update { it.copy(eq = it.eq.withPreamp(Response.headroomFor(it.eq))) }
    fun flatten() = replace(_state.value.eq.flattened())
    fun revert() { _state.value.onCable?.let(::replace) }

    fun saveProfile(name: String) {
        val clean = name.trim().ifEmpty { return }
        store.save(clean, _state.value.eq)
        _state.update { it.copy(profiles = store.profiles) }
        say("Saved \"$clean\".")
    }
    fun loadProfile(p: Profile) { replace(p.eq); say("Loaded \"${p.name}\". Write it to hear it on the cable.") }
    fun deleteProfile(p: Profile) { store.delete(p.name); _state.update { it.copy(profiles = store.profiles) } }

    fun import(text: String) = runCatching { SquigText.parse(text) }.fold(
        onSuccess = { replace(it.eq); say("Imported." + if (it.notes.isEmpty()) "" else " Heads up: ${it.notes.joinToString("; ")}.") },
        onFailure = { say(it.message ?: "That doesn't look like an EQ", true) },
    )
    fun exportText() = SquigText.export(_state.value.eq)

    fun say(text: String, problem: Boolean = false) = _state.update { it.copy(notice = Notice(text, problem)) }
    fun dismissNotice(id: Long) = _state.update { if (it.notice?.id == id) it.copy(notice = null) else it }

    private fun replace(eq: Eq) = _state.update { it.copy(eq = eq, jump = it.jump + 1) }

    private fun cableJob(label: String, work: (UsbDevice) -> Unit) {
        if (_state.value.busy != null) return
        val d = link.find() ?: return say("Plug in your FreeDSP cable first.", true)
        _state.update { it.copy(busy = label) }
        viewModelScope.launch {
            val r = withContext(usb) { runCatching { work(d) } }
            r.exceptionOrNull()?.let { Log.w(CableLink.TAG, "$label failed", it); say(it.message ?: "Something went wrong", true) }
            _state.update { it.copy(busy = null, restorePending = link.restorePending) }
        }
    }

    override fun onCleared() = usb.close()
}

fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 255) }
