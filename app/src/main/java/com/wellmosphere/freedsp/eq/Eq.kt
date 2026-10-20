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

    companion object {
        const val BANDS = 9
        private val OCTAVES = listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0)
        val FLAT = Eq(OCTAVES.map { Band(it, 0.0, 1.0) })
    }
}

object Limits {
    val FREQ = 20.0..20_000.0
    val GAIN = -12.0..6.0
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
