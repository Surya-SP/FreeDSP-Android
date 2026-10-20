package com.wellmosphere.freedsp.eq

import com.wellmosphere.freedsp.cable.CableCoding
import org.junit.Assert.*
import org.junit.Test

class PresetsTest {
    @Test fun everyPresetIsWritableAndCannotDistort() {
        for (p in Presets.ALL) {
            assertTrue(p.name, p.eq.bands.all { it == Limits.clamp(it) })
            assertFalse(p.name, Response.clips(p.eq))
            assertEquals(p.name, 56, CableCoding.plan(p.eq).frames.size)
        }
    }
    @Test fun namesAreUniqueAndFlatIsFlat() {
        assertEquals(Presets.ALL.size, Presets.ALL.map { it.name }.toSet().size)
        assertTrue(Presets.ALL.first { it.name == "Flat" }.eq.soundsLike(Eq.FLAT))
        assertTrue(Presets.ALL.filter { it.name != "Flat" }.none { it.eq.soundsLike(Eq.FLAT) })
    }
    @Test fun bassBoostPlusReallyHasMoreBassThanBassBoost() {
        fun tilt(name: String) = Presets.ALL.first { it.name == name }.eq.let { Response.bandDbTotal(it, 80.0) - Response.bandDbTotal(it, 2000.0) }
        assertTrue(tilt("Bass boost+") > tilt("Bass boost") + 1.5)
    }
}
