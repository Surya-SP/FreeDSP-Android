package com.wellmosphere.freedsp.eq

import kotlin.math.ln

/**
 * A frequency-response curve (an IEM measurement or a target), sorted by frequency and shifted to read 0 dB
 * at 1 kHz. Parsing and interpolation follow FreeDSP-Studio fr.rs.
 */
class Curve private constructor(val name: String, private val hz: DoubleArray, private val db: DoubleArray) {
    val points get() = hz.indices.map { hz[it] to db[it] }

    /** Log-frequency linear interpolation; flat beyond the ends. */
    fun at(f: Double): Double {
        if (f <= hz.first()) return db.first()
        if (f >= hz.last()) return db.last()
        var lo = 0; var hi = hz.size - 1
        while (hi - lo > 1) { val mid = (lo + hi) ushr 1; if (hz[mid] < f) lo = mid else hi = mid }
        val t = (ln(f) - ln(hz[lo])) / (ln(hz[hi]) - ln(hz[lo]))
        return db[lo] + (db[hi] - db[lo]) * t
    }

    companion object {
        private val number = Regex("""^-?[\d.]+(?:[eE][-+]?\d+)?""")

        /**
         * "freq dB" per line, as AutoEq and squig.link export them, separated by spaces, tabs, commas or
         * semicolons. Header lines and anything else that isn't a pair of numbers are skipped.
         */
        fun parse(name: String, text: String): Curve {
            val pts = text.lineSequence().mapNotNull { line ->
                val parts = line.trim().split(Regex("""[\s,;]+"""))
                val f = parts.getOrNull(0)?.takeIf { number.matches(it) }?.toDoubleOrNull()
                val d = parts.getOrNull(1)?.takeIf { number.matches(it) }?.toDoubleOrNull()
                if (f != null && d != null && f > 0 && f.isFinite() && d.isFinite()) f to d else null
            }.sortedBy { it.first }.distinctBy { it.first }.toList()
            require(pts.size >= 4) { "No frequency and dB pairs found. Use a measurement or target exported as text or CSV." }
            val raw = Curve(name, DoubleArray(pts.size) { pts[it].first }, DoubleArray(pts.size) { pts[it].second })
            val ref = raw.at(1000.0)
            return Curve(name, raw.hz, DoubleArray(pts.size) { raw.db[it] - ref })
        }
    }
}
