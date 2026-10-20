package com.wellmosphere.freedsp.eq

import org.junit.Assert.*
import org.junit.Test

class EqTest {
    @Test fun headroomBringsTheLoudestPointToZeroWithoutBoosting() {
        val eq = Eq.FLAT.withBand(1, Band(62.0, 5.0, 0.8, FilterType.LOW_SHELF)).withBand(6, Band(2000.0, 2.3, 2.0))
        assertTrue(Response.clips(eq))
        val fixed = eq.withPreamp(Response.headroomFor(eq))
        val loudest = Response.peak(fixed) + fixed.preampDb
        assertFalse(Response.clips(fixed)); assertTrue("$loudest", loudest <= 0.0 && loudest > -0.1)
        assertEquals(0.0, Response.headroomFor(Eq.FLAT.withBand(0, Band(31.0, -6.0, 1.0))), 0.0)
    }
    @Test fun limitsHoldEveryValueInsideTheCablesRange() {
        val b = Limits.clamp(Band(5.0, 20.0, 50.0, FilterType.HIGH_SHELF))
        assertEquals(20.0, b.freqHz, 0.0); assertEquals(6.0, b.gainDb, 0.0); assertEquals(2.0, b.q, 0.0)
        assertEquals(-20.0, Eq.FLAT.withPreamp(-40.0).preampDb, 0.0); assertEquals(0.0, Eq.FLAT.withPreamp(3.0).preampDb, 0.0)
    }
    @Test fun shelvesStayFiniteAcrossTheWholeAllowedRange() {
        for (g in listOf(-12.0, 6.0)) for (q in listOf(0.2, 2.0)) for (t in listOf(FilterType.LOW_SHELF, FilterType.HIGH_SHELF))
            assertTrue(Response.biquad(t, 1000.0, g, q, 384_000.0).all { it.isFinite() })
    }
    @Test fun aPeakMeasuresItsOwnGainAtItsCentre() {
        assertEquals(-3.0, Response.bandDb(Band(909.0, -3.0, 0.7), 909.0), 1e-9)
    }

    @Test fun safePreampNeverLetsAnEditDistort() {
        var eq = Eq.FLAT
        for ((i, g) in listOf(6.0, 6.0, 4.5, -3.0, 2.0, 6.0, 3.5, 6.0, 1.0).withIndex()) {
            eq = eq.withBand(i, eq.bands[i].copy(gainDb = g)).withSafePreamp()
            assertFalse("after band ${i + 1}", Response.clips(eq))
        }
        assertEquals(0.0, Eq.FLAT.withBand(2, Eq.FLAT.bands[2].copy(gainDb = -4.0)).withSafePreamp().preampDb, 0.0)
    }
    @Test fun newProfilesStartAsAFlatOctaveGraphicEq() {
        assertEquals(listOf(63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), Eq.FLAT.bands.map { it.freqHz.toInt() })
        assertTrue(Eq.FLAT.bands.all { it.gainDb == 0.0 && it.type == FilterType.PEAK && it == Limits.clamp(it) })
        assertEquals(0.0, Response.peak(Eq.FLAT), 0.0)
    }
}
