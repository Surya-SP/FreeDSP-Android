package com.wellmosphere.freedsp.cable

import com.wellmosphere.freedsp.eq.Band
import com.wellmosphere.freedsp.eq.Eq
import com.wellmosphere.freedsp.eq.FilterType
import com.wellmosphere.freedsp.eq.Limits
import com.wellmosphere.freedsp.eq.Response
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign

/** EQ <-> cable records, matching FreeDSP-Studio write_bank: metadata labels plus 2^22 fixed-point biquads. */
object CableCoding {
    private const val SCALE = 4_194_304.0 // 2^22

    /** Rust's f64::round, which upstream uses: halves go away from zero. */
    fun round(x: Double): Int {
        require(x.isFinite())
        return (sign(x) * floor(abs(x) + 0.5)).toInt()
    }

    fun metadata(b: Band) = Gen1.Metadata(round(b.freqHz), round(b.q * 256), b.type.code, round(b.gainDb))

    /** [b0, b1, b2, -a1, -a2] normalised by a0 and scaled by 2^22. */
    fun coefficients(b: Band, fs: Double): IntArray {
        val c = Response.biquad(b.type, b.freqHz, b.gainDb, b.q, fs)
        return doubleArrayOf(c[0], c[1], c[2], -c[4], -c[5]).map { round(it / c[3] * SCALE) }.toIntArray()
    }

    class Plan(val frames: List<Gen1.Frame>, val expected: List<Gen1.Metadata>)

    /** The exact 56-frame write. Refuses anything out of range, non-finite or able to clip. */
    fun plan(eq: Eq): Plan {
        eq.bands.forEach { require(it == Limits.clamp(it)) { "A band is outside the cable's limits" } }
        require(eq.preampDb == Limits.clampPreamp(eq.preampDb)) { "Preamp is outside the cable's limits" }
        require(!Response.clips(eq)) { "This EQ could clip. Use Auto headroom first." }
        val expected = eq.bands.map(::metadata)
        // Gen-1 has no preamp register: it rides on the last band that does something (upstream's choice).
        val k = 10.0.pow(eq.preampDb / 20)
        val preampBand = if (k == 1.0) -1 else eq.bands.indexOfLast { it.gainDb != 0.0 }.let { if (it < 0) 8 else it }
        val frames = mutableListOf(Gen1.selectCustomBank())
        expected.forEachIndexed { i, m -> frames += Gen1.writeMetadata(i + 1, m) }
        eq.bands.forEachIndexed { i, b ->
            for ((rate, fs) in Gen1.SAMPLE_RATES) {
                val c = coefficients(b, fs)
                if (i == preampBand) for (j in 0..2) c[j] = round(c[j] * k)
                frames += Gen1.writeCoefficients(rate, i + 1, c)
            }
        }
        frames += Gen1.commit()
        return Plan(frames, expected)
    }

    /** What a read shows. Gains are whole dB and the preamp is invisible, so prefer a matching saved EQ. */
    fun eqFrom(records: List<Gen1.Metadata>): Eq = Eq(records.map { m ->
        val type = FilterType.fromCode(m.type) ?: error("The cable reports an unknown filter type (${m.type})")
        Limits.clamp(Band(m.freqHz.toDouble(), m.gainDb.toDouble(), m.qRaw / 256.0, type))
    })

    fun matches(eq: Eq, records: List<Gen1.Metadata>) = eq.bands.map(::metadata) == records
}
