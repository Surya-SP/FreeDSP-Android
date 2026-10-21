package com.wellmosphere.freedsp

import com.wellmosphere.freedsp.cable.CableCoding
import com.wellmosphere.freedsp.eq.Eq
import com.wellmosphere.freedsp.eq.Presets
import com.wellmosphere.freedsp.store.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateTest {
    private val rock = Presets.named("Rock")!!.eq
    private val jazz = Presets.named("Jazz")!!.eq
    private val mine = Eq(rock.bands.mapIndexed { i, b -> if (i == 4) b.copy(gainDb = 2.3) else b }).withSafePreamp()
    private val profiles = listOf(Profile("Mine", mine), Profile("Other", jazz))

    private fun written(eq: Eq) = UiState(profiles = profiles, onCable = eq.bands.map(CableCoding::metadata), lastWritten = eq)
    private fun UiState.onPreset(name: String) = copy(preset = name, presetEq = Presets.named(name)!!.eq)
    private fun UiState.onProfile(name: String) = copy(preset = null, active = name)

    @Test fun aWrittenPresetIsRecognisedAfterSwitchingAway() {
        val s = written(rock)
        assertFalse(s.onPreset("Jazz").activeOnCable)
        assertFalse(s.onProfile("Mine").activeOnCable)
        assertTrue(s.onPreset("Rock").activeOnCable)
    }

    @Test fun aWrittenProfileIsRecognisedAfterSwitchingAway() {
        val s = written(mine)
        assertEquals("Mine", s.onPreset("Rock").playing)
        assertFalse(s.onPreset("Rock").activeOnCable)
        assertTrue(s.onProfile("Mine").activeOnCable)
    }

    @Test fun recordsAloneStillFindThePreset() {
        val s = UiState(profiles = profiles, onCable = rock.bands.map(CableCoding::metadata))
        assertTrue(s.onPreset("Rock").activeOnCable)
    }
}
