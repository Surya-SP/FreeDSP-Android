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
}
