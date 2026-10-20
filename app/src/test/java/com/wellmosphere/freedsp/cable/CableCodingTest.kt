package com.wellmosphere.freedsp.cable

import com.wellmosphere.freedsp.eq.Band
import com.wellmosphere.freedsp.eq.Eq
import com.wellmosphere.freedsp.eq.FilterType
import org.junit.Assert.*
import org.junit.Test

class CableCodingTest {
    private val q = 180 / 256.0
    /** The cable as found: log-spaced flat peaks. */
    private val asFound = Eq(listOf(103, 177, 306, 527, 909, 1565, 2697, 4644, 7999).map { Band(it.toDouble(), 0.0, q) })

    @Test fun roundingMatchesRustHalfAwayFromZero() {
        assertEquals(3, CableCoding.round(2.5)); assertEquals(-3, CableCoding.round(-2.5))
        assertEquals(2, CableCoding.round(2.4999)); assertEquals(0, CableCoding.round(-0.4))
    }

    @Test fun flatBandIsAnExactIdentityAtEveryRate() {
        for ((_, fs) in Gen1.SAMPLE_RATES) for (b in asFound.bands + Band(105.0, 0.0, 0.7, FilterType.LOW_SHELF)) {
            val c = CableCoding.coefficients(b, fs)
            assertEquals(4194304, c[0]); assertEquals(-c[1], c[3]); assertEquals(-c[2], c[4])
        }
    }

    // Golden values computed independently in Python (Q 0.7 is 179/256 on the cable's grid).
    @Test fun coefficientsMatchGoldenVectors() {
        assertArrayEquals(intArrayOf(4082613, -7569841, 3541133, 7569841, -3429442), CableCoding.coefficients(Band(909.0, -3.0, q), 48_000.0))
        assertArrayEquals(intArrayOf(4179097, -8283556, 4105375, 8283556, -4090168), CableCoding.coefficients(Band(909.0, -3.0, q), 384_000.0))
        assertArrayEquals(intArrayOf(4205573, -8301460, 4096874, 8301642, -4107961), CableCoding.coefficients(Band(105.0, 4.0, 179 / 256.0, FilterType.LOW_SHELF), 48_000.0))
        assertArrayEquals(intArrayOf(3616089, -1879061, 508299, 2656284, -707307), CableCoding.coefficients(Band(8000.0, -2.0, 179 / 256.0, FilterType.HIGH_SHELF), 48_000.0))
    }

    @Test fun planIsTheExact56FrameSequence() {
        val plan = CableCoding.plan(asFound)
        assertEquals(56, plan.frames.size)
        assertEquals("select custom bank", plan.frames.first().label)
        assertEquals((1..9).map { "band $it settings" }, plan.frames.subList(1, 10).map { it.label })
        assertEquals("band 1 filter @4", plan.frames[10].label); assertEquals("band 9 filter @8", plan.frames[54].label)
        assertEquals("commit", plan.frames.last().label)
        assertEquals((1..9).map { i -> Gen1.Metadata(asFound.bands[i - 1].freqHz.toInt(), 180, 0, 0) }, plan.expected)
    }

    @Test fun rewritingWhatWasReadIsByteIdentical() {
        val read = asFound.bands.map(CableCoding::metadata)
        assertEquals(asFound, CableCoding.eqFrom(read))
        assertEquals(read, CableCoding.plan(CableCoding.eqFrom(read)).expected)
    }

    @Test fun preampRidesOnTheLastActiveBand() {
        val eq = asFound.withBand(0, Band(105.0, 3.0, 0.7, FilterType.LOW_SHELF)).withPreamp(-4.5)
        val frames = CableCoding.plan(eq).frames
        val band1At48 = frames.single { it.label == "band 1 filter @5" }.bytes
        val c = java.nio.ByteBuffer.wrap(band1At48).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(listOf(2503408, -4943477, 2440624, 8299265, -4105620), (0..4).map { c.getInt(22 + 4 * it) })
        val untouched = frames.single { it.label == "band 9 filter @5" }.bytes
        assertEquals(4194304, java.nio.ByteBuffer.wrap(untouched).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(22))
    }

    @Test fun anythingThatCouldClipIsRefused() {
        val boosted = asFound.withBand(4, Band(909.0, 3.0, q))
        assertThrows(IllegalArgumentException::class.java) { CableCoding.plan(boosted) }
        CableCoding.plan(boosted.withPreamp(-3.0)) // fixed by headroom
    }

    @Test fun valuesOutsideTheLimitsAreRefused() {
        val tooWide = Eq(asFound.bands.toMutableList().also { it[0] = Band(103.0, -2.0, 15.0) })
        assertThrows(IllegalArgumentException::class.java) { CableCoding.plan(tooWide) }
    }

    @Test fun unknownFilterTypesOnTheCableAreNotGuessed() {
        assertThrows(IllegalStateException::class.java) { CableCoding.eqFrom(List(9) { Gen1.Metadata(1000, 180, 7, 0) }) }
    }
}

class SoundsLikeTest {
    private val q = 180 / 256.0
    private val freqs = listOf(103, 177, 306, 527, 909, 1565, 2697, 4644, 7999)
    private val flatPeaks = Eq(freqs.map { Band(it.toDouble(), 0.0, q) })

    @Test fun zeroGainBandsDontChangeTheSound() {
        // Seen on the device: band 1 written as a 0 dB low shelf; a flat profile of peaks sounds the same.
        val records = flatPeaks.withBand(0, Band(103.0, 0.0, q, FilterType.LOW_SHELF)).bands.map(CableCoding::metadata)
        assertFalse(CableCoding.matches(flatPeaks, records))
        assertTrue(CableCoding.soundsLike(flatPeaks, records))
        assertTrue(CableCoding.soundsLike(Eq.FLAT, records)) // different frequencies, still flat
    }
    @Test fun activeBandsMustMatch() {
        val eq = flatPeaks.withBand(4, Band(909.0, -3.0, q))
        val records = eq.bands.map(CableCoding::metadata)
        assertTrue(CableCoding.soundsLike(eq, records))
        assertFalse(CableCoding.soundsLike(flatPeaks, records))
        assertFalse(CableCoding.soundsLike(eq.withBand(4, Band(909.0, -3.0, q, FilterType.HIGH_SHELF)), records))
    }
    @Test fun exactComparisonSeesFractionsAndPreamp() {
        val a = flatPeaks.withBand(2, Band(306.0, 2.3, q)).withSafePreamp()
        assertTrue(a.soundsLike(a.withBand(0, Band(103.0, 0.0, 1.0, FilterType.LOW_SHELF))))
        assertFalse(a.soundsLike(a.withBand(2, Band(306.0, 2.4, q))))
        assertFalse(a.soundsLike(a.withPreamp(-5.0)))
    }
}
