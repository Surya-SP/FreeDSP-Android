package com.wellmosphere.freedsp.eq

import kotlin.math.*

/** RBJ cookbook biquads and the combined response, as FreeDSP-Studio eq.rs computes them. */
object Response {
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

    fun bandDb(b: Band, at: Double): Double {
        if (b.gainDb == 0.0) return 0.0
        val c = biquad(b.type, b.freqHz, b.gainDb, b.q, 48_000.0)
        val w = 2 * PI * at / 48_000.0
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = c[0] + c[1] * c1 + c[2] * c2; val ni = -(c[1] * s1 + c[2] * s2)
        val dr = c[3] + c[4] * c1 + c[5] * c2; val di = -(c[4] * s1 + c[5] * s2)
        return 10 * log10((nr * nr + ni * ni) / (dr * dr + di * di))
    }

    /** What you hear: every band plus the preamp. */
    fun totalDb(eq: Eq, at: Double) = eq.bands.sumOf { bandDb(it, at) } + eq.preampDb

    /** Log-spaced frequency for t in 0..1 across 20 Hz–20 kHz. */
    fun freqAt(t: Double) = 20.0 * 1000.0.pow(t)

    /** Highest point of the bands alone, sampled finely enough for Q 10 peaks. */
    fun peak(eq: Eq): Double = (0..960).maxOf { i -> eq.bands.sumOf { bandDb(it, freqAt(i / 960.0)) } }

    fun clips(eq: Eq) = peak(eq) + eq.preampDb > 1e-9

    /** The preamp that brings the loudest point down to 0 dB, never boosting. */
    fun headroomFor(eq: Eq): Double {
        val p = peak(eq)
        return if (p > 0) -ceil(p * 10 - 1e-9) / 10 else 0.0
    }
}
