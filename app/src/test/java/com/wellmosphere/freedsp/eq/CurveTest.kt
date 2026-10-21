package com.wellmosphere.freedsp.eq

import org.junit.Assert.*
import org.junit.Test

class CurveTest {
    @Test fun readsAutoEqCsvAndNormalisesAt1k() {
        val c = Curve.parse("t", "frequency,raw\n20,7.5\n500,2\n1000,4\n2000,9\n20000,-3")
        assertEquals(0.0, c.at(1000.0), 1e-9)
        assertEquals(5.0, c.at(2000.0), 1e-9)
        assertEquals(3.5, c.at(5.0), 1e-9) // flat beyond the ends
    }
    @Test fun readsSquigTextWithHeadersAndSpaces() {
        val c = Curve.parse("m", "* squig.link export\nFrequency  dB  phase\n100\t80.0\t0\n1000 75.0 0\n10000 70.5 0\n15000 68 0")
        assertEquals(5.0, c.at(100.0), 1e-9); assertEquals(-4.5, c.at(10_000.0), 1e-9)
    }
    @Test fun interpolatesOnALogScale() {
        val c = Curve.parse("x", "100 0\n1000 0\n10000 10\n20000 10")
        assertEquals(5.0, c.at(Math.sqrt(1000.0 * 10000.0)), 1e-9)
    }
    @Test fun refusesFilesWithoutCurves() {
        assertThrows(IllegalArgumentException::class.java) { Curve.parse("x", "Filter 1: ON PK Fc 100 Hz Gain 3 dB Q 1") }
    }
}
