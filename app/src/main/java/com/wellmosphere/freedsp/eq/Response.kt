package com.wellmosphere.freedsp.eq

import kotlin.math.*

/** RBJ cookbook biquads and the combined response, as FreeDSP-Studio eq.rs computes them. */
object Response {
    private const val FS = 48_000.0

    /** [b0, b1, b2, a0, a1, a2] for one band at sample rate [fs]. */
    fun biquad(type: FilterType, f: Double, gainDb: Double, q: Double, fs: Double): DoubleArray {
        val a = 10.0.pow(gainDb / 40.0)
        val w = 2 * PI * f / fs
        val cw = cos(w); val sw = sin(w)
        if (type == FilterType.PEAK) {
            val al = sw / (2 * q)
            return doubleArrayOf(1 + al * a, -2 * cw, 1 - al * a, 1 + al / a, -2 * cw, 1 - al / a)
        }
        val sa = (sw / 2) * sqrt((a + 1 / a) * (1 / q - 1) + 2)
        val t = 2 * sqrt(a) * sa
        return if (type == FilterType.LOW_SHELF) doubleArrayOf(
            a * ((a + 1) - (a - 1) * cw + t), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - t),
            (a + 1) + (a - 1) * cw + t, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - t,
        ) else doubleArrayOf(
            a * ((a + 1) + (a - 1) * cw + t), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - t),
            (a + 1) - (a - 1) * cw + t, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - t,
        )
    }

    private fun magnitudeDb(c: DoubleArray, at: Double): Double {
        val w = 2 * PI * at / FS
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = c[0] + c[1] * c1 + c[2] * c2; val ni = -(c[1] * s1 + c[2] * s2)
        val dr = c[3] + c[4] * c1 + c[5] * c2; val di = -(c[4] * s1 + c[5] * s2)
        return 10 * log10((nr * nr + ni * ni) / (dr * dr + di * di))
    }

    fun bandDb(b: Band, at: Double) = if (b.gainDb == 0.0) 0.0 else magnitudeDb(biquad(b.type, b.freqHz, b.gainDb, b.q, FS), at)

    /** The bands' combined response at each frequency (no preamp), drawn at 48 kHz like upstream. */
    fun curve(eq: Eq, freqs: DoubleArray): DoubleArray {
        val active = eq.bands.filter { it.gainDb != 0.0 }.map { biquad(it.type, it.freqHz, it.gainDb, it.q, FS) }
        return DoubleArray(freqs.size) { i -> active.sumOf { magnitudeDb(it, freqs[i]) } }
    }

    /** What you hear at one frequency: all bands plus the preamp. */
    fun bandDbTotal(eq: Eq, at: Double) = curve(eq, doubleArrayOf(at))[0] + eq.preampDb

    /** Log-spaced frequency for t in 0..1 across 20 Hz–20 kHz. */
    fun freqAt(t: Double) = 20.0 * 1000.0.pow(t)
    fun logSpaced(points: Int) = DoubleArray(points) { freqAt(it / (points - 1.0)) }

    /** Fine enough to catch Q 10 peaks between samples. */
    private val PEAK_GRID = logSpaced(961)

    /** Highest point of the bands alone. */
    fun peak(eq: Eq): Double = curve(eq, PEAK_GRID).max()

    fun clips(eq: Eq) = peak(eq) + eq.preampDb > 1e-9

    /** The preamp that brings the loudest point down to 0 dB, never boosting. */
    fun headroomFor(eq: Eq): Double {
        val p = peak(eq)
        return if (p > 0) -ceil(p * 10 - 1e-9) / 10 else 0.0
    }
}
