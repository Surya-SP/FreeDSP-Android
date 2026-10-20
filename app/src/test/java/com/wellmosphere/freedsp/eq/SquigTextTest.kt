package com.wellmosphere.freedsp.eq

import org.junit.Assert.*
import org.junit.Test

class SquigTextTest {
    private val autoEq = """
        Preamp: -6.4 dB
        Filter 1: ON LSC Fc 105 Hz Gain 6.0 dB Q 0.70
        Filter 2: ON PK Fc 180 Hz Gain -2.6 dB Q 0.82
        Filter 3: OFF PK Fc 300 Hz Gain 1.0 dB Q 1.00
        Filter: ON HSC Fc 10000 Hz Gain -3.1 dB Q 0.70
    """.trimIndent()

    @Test fun readsAutoEqText() {
        val r = SquigText.parse(autoEq)
        assertEquals(-6.4, r.eq.preampDb, 0.0)
        assertEquals(FilterType.LOW_SHELF, r.eq.bands[0].type); assertEquals(105.0, r.eq.bands[0].freqHz, 0.0)
        assertEquals(-2.6, r.eq.bands[1].gainDb, 0.0)
        assertEquals(FilterType.HIGH_SHELF, r.eq.bands[2].type)
        assertEquals(0.0, r.eq.bands[3].gainDb, 0.0) // padded flat
        assertTrue(r.notes.any { "switched-off" in it })
    }
    @Test fun keepsNineAndSaysSo() {
        val text = (1..12).joinToString("\n") { "Filter $it: ON PK Fc ${it * 100} Hz Gain -1.0 dB Q 1.0" }
        val r = SquigText.parse(text)
        assertEquals(900.0, r.eq.bands[8].freqHz, 0.0); assertTrue(r.notes.any { "first 9" in it })
    }
    @Test fun clampsAndConvertsWhatTheCableCantDo() {
        val r = SquigText.parse("Filter 1: ON PK Fc 50 Hz Gain 10.0 dB Q 1.0\nFilter 2: ON HPQ Fc 20 Hz Gain 0.0 dB Q 0.7")
        assertEquals(6.0, r.eq.bands[0].gainDb, 0.0); assertEquals(FilterType.PEAK, r.eq.bands[1].type)
        assertEquals(2, r.notes.size)
    }
    @Test fun refusesTextWithoutFilters() {
        assertThrows(IllegalArgumentException::class.java) { SquigText.parse("hello") }
    }
    @Test fun exportRoundTrips() {
        val eq = SquigText.parse(autoEq).eq
        assertEquals(eq, SquigText.parse(SquigText.export(eq)).eq)
        assertFalse("Filter 4" in SquigText.export(eq)) // only active bands
    }
}
