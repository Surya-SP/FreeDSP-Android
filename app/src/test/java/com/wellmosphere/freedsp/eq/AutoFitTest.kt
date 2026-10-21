package com.wellmosphere.freedsp.eq

import com.wellmosphere.freedsp.cable.CableCoding
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AutoFitTest {
    // A real bundled target (AutoEq, MIT), read the same way the app reads it.
    private val harman = Curve.parse("Harman in-ear 2019", File("src/main/assets/targets/Harman in-ear 2019.csv").readText())

    /** A pretend IEM: the target with a known EQ taken off, so the right answer is that EQ. */
    private fun iemMissing(eq: Eq): Curve {
        val hz = Response.logSpaced(400)
        val shape = Response.curve(eq, hz)
        return Curve.parse("pretend IEM", hz.indices.joinToString("\n") { "${hz[it]} ${harman.at(hz[it]) - shape[it]}" })
    }

    @Test fun recoversAKnownCorrection() {
        val truth = Eq.FLAT.withBand(1, Band(180.0, 3.0, 0.9)).withBand(4, Band(900.0, -4.0, 1.4)).withBand(7, Band(4200.0, -6.0, 2.5))
        val iem = iemMissing(truth)
        val fitted = AutoFit.fit(iem, harman)
        val before = AutoFit.error(iem, harman, Eq.FLAT)
        val after = AutoFit.error(iem, harman, fitted)
        assertTrue("before $before after $after", before > 2.0 && after < 0.5)
    }

    @Test fun anIemThatAlreadyMatchesNeedsNoBands() {
        val fitted = AutoFit.fit(harman, harman)
        assertTrue(fitted.bands.all { it.gainDb == 0.0 })
        assertEquals(0.0, AutoFit.error(harman, harman, fitted), 1e-9)
    }

    @Test fun resultsStayInsideTheCableAndAreWritable() {
        // Needs more than +3 dB in places, so the gain ceiling must hold.
        val iem = iemMissing(Eq.FLAT.withBand(0, Band(103.0, 3.0, 0.7, FilterType.LOW_SHELF)).withBand(6, Band(2700.0, 3.0, 1.0)).withBand(5, Band(1500.0, 3.0, 1.0)))
        val fitted = AutoFit.fit(iem, harman)
        assertTrue(fitted.bands.all { it == Limits.clamp(it) && it.type == FilterType.PEAK })
        assertFalse(Response.clips(fitted))
        assertEquals(56, CableCoding.plan(fitted).frames.size)
    }

    @Test fun bandsComeBackInFrequencyOrder() {
        val iem = iemMissing(Eq.FLAT.withBand(2, Band(300.0, -3.0, 1.0)).withBand(7, Band(4500.0, 3.0, 2.0)))
        val hz = AutoFit.fit(iem, harman).bands.map { it.freqHz }
        assertEquals(hz.sorted(), hz)
    }

    @Test fun stoppingEarlyLeavesTheTrebleAlone() {
        // The only problem is above 8 kHz, so a fit that stops at 6 kHz has nothing to do.
        val iem = iemMissing(Eq.FLAT.withBand(8, Band(9000.0, -6.0, 3.0)))
        val fitted = AutoFit.fit(iem, harman, upTo = 6_000.0)
        assertTrue(fitted.bands.filter { it.gainDb != 0.0 }.all { it.freqHz <= 6_000.0 })
        assertTrue(AutoFit.error(iem, harman, fitted, 6_000.0) < 0.3)
        assertTrue(AutoFit.fit(iem, harman).bands.any { it.gainDb != 0.0 && it.freqHz > 8_000.0 })
    }
}