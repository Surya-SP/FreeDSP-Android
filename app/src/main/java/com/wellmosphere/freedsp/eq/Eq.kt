package com.wellmosphere.freedsp.eq

import kotlin.math.roundToInt

enum class FilterType(val code: Int, val label: String, val short: String) {
    PEAK(0, "Peak", "PK"), LOW_SHELF(1, "Low shelf", "LS"), HIGH_SHELF(2, "High shelf", "HS");
    companion object { fun fromCode(code: Int) = entries.firstOrNull { it.code == code } }
}

data class Band(val freqHz: Double, val gainDb: Double, val q: Double, val type: FilterType = FilterType.PEAK)

/** Nine bands plus a preamp, always within [Limits]. */
data class Eq(val bands: List<Band>, val preampDb: Double = 0.0) {
    init { require(bands.size == BANDS) { "The cable has exactly $BANDS bands" } }

    fun withBand(index: Int, band: Band) = copy(bands = bands.toMutableList().also { it[index] = Limits.clamp(band) })
    fun withPreamp(db: Double) = copy(preampDb = Limits.clampPreamp(db))
    /** Same frequencies, shapes and widths, nothing boosted or cut. */
    fun flattened() = Eq(bands.map { it.copy(gainDb = 0.0) })
    /** Bands at 0 dB do nothing whatever their type, Q or frequency, so only the active ones (and the preamp) count. */
    fun soundsLike(other: Eq) = preampDb == other.preampDb && active() == other.active()
    private fun active() = bands.filter { it.gainDb != 0.0 }.sortedBy { it.freqHz }
    /** The preamp is automatic: just enough to keep the loudest point at 0 dB, so nothing can distort. */
    fun withSafePreamp() = withPreamp(Response.headroomFor(this))

    companion object {
        const val BANDS = 9
        /** The cable's own factory layout (read from it): log-spaced peaks, Q 0.70. New profiles start here. */
        private val FACTORY = listOf(103.0, 177.0, 306.0, 527.0, 909.0, 1565.0, 2697.0, 4644.0, 7999.0)
        val FLAT = Eq(FACTORY.map { Band(it, 0.0, 180 / 256.0) })
    }
}

/**
 * What the official Moondrop app allows on the original FreeDSP. Writes outside it were stored but not heard on
 * the physical cable (2026-10-20), so the app keeps every EQ inside these ranges.
 */
object Limits {
    val FREQ = 60.0..10_000.0
    val GAIN = -12.0..3.0
    val Q = 0.2..10.0
    /** Shelf-Q alpha needs (A + 1/A)(1/Q - 1) + 2 >= 0; Q <= 2 keeps it real for every allowed gain. */
    val SHELF_Q = 0.2..2.0
    val PREAMP = -20.0..0.0

    fun qRange(type: FilterType) = if (type == FilterType.PEAK) Q else SHELF_Q
    fun clamp(b: Band) = Band(
        freqHz = b.freqHz.coerceIn(FREQ).roundToInt().toDouble(),
        gainDb = tenth(b.gainDb.coerceIn(GAIN)),
        q = cableQ(b.q.coerceIn(qRange(b.type))),
        type = b.type,
    )
    fun clampPreamp(db: Double) = tenth(db.coerceIn(PREAMP))
    private fun tenth(v: Double) = (v * 10).roundToInt() / 10.0
    /** The cable stores Q in 1/256 steps; keeping that grid makes a read-then-write an exact rewrite. */
    private fun cableQ(v: Double) = (v * 256).roundToInt() / 256.0
}
