package com.wellmosphere.freedsp.eq

import java.util.Locale

/**
 * Equalizer APO parametric text, as AutoEQ and squig.link export it:
 * "Preamp: -3.8 dB" and "Filter 1: ON PK Fc 105 Hz Gain 3.0 dB Q 0.70". After FreeDSP-Studio eq.rs.
 */
object SquigText {
    class Imported(val eq: Eq, val notes: List<String>)

    private val preamp = Regex("""(?i)preamp:\s*(-?[\d.]+)\s*db""")
    private val filter = Regex("""(?i)filter\s*\d*\s*:\s+(ON|OFF)\s+(\w+)\s+Fc\s+([\d.]+)\s*Hz\s+Gain\s+(-?[\d.]+)\s*dB\s+Q\s+([\d.]+)""")

    fun parse(text: String): Imported {
        var pre = 0.0
        val bands = mutableListOf<Band>()
        val notes = linkedSetOf<String>()
        var off = 0
        for (line in text.lineSequence()) {
            preamp.find(line)?.groupValues?.get(1)?.toDoubleOrNull()?.let { pre = it }
            val m = filter.find(line) ?: continue
            if (m.groupValues[1].equals("OFF", ignoreCase = true)) { off++; continue }
            val (kind, fc, gain, q) = m.groupValues.drop(2)
            val type = when (kind.uppercase()) {
                "LSC", "LS", "LSQ" -> FilterType.LOW_SHELF
                "HSC", "HS", "HSQ" -> FilterType.HIGH_SHELF
                "PK", "PEQ" -> FilterType.PEAK
                else -> { notes += "$kind filters aren't supported on the cable, so they became peaks"; FilterType.PEAK }
            }
            val raw = Band(fc.toDoubleOrNull() ?: continue, gain.toDoubleOrNull() ?: continue, q.toDoubleOrNull() ?: continue, type)
            val fitted = Limits.clamp(raw)
            if (fitted.gainDb != raw.gainDb) notes += "some gains were limited to ${fmt(Limits.GAIN.start)}…+${fmt(Limits.GAIN.endInclusive)} dB"
            if (fitted.q != raw.q && kotlin.math.abs(fitted.q - raw.q) > 0.01) notes += "some Q values were limited"
            if (fitted.freqHz != Math.round(raw.freqHz).toDouble()) notes += "some frequencies were moved into the cable's 60 Hz–10 kHz range"
            bands += fitted
        }
        require(bands.isNotEmpty()) { "No EQ filters found. Paste parametric EQ text, like an AutoEQ or squig.link export." }
        if (bands.size > Eq.BANDS) notes += "it had ${bands.size} filters; the cable has ${Eq.BANDS}, so the first ${Eq.BANDS} were kept"
        if (off > 0) notes += "$off switched-off filter${if (off > 1) "s were" else " was"} skipped"
        val kept = bands.take(Eq.BANDS)
        val padded = kept + Eq.FLAT.bands.drop(kept.size)
        return Imported(Eq(padded).withPreamp(pre), notes.toList())
    }

    /** Only bands that do something, numbered from 1. */
    fun export(eq: Eq): String = buildString {
        append("Preamp: ${fmt(eq.preampDb)} dB")
        eq.bands.filter { it.gainDb != 0.0 }.forEachIndexed { i, b ->
            val kind = when (b.type) { FilterType.PEAK -> "PK"; FilterType.LOW_SHELF -> "LSC"; FilterType.HIGH_SHELF -> "HSC" }
            append("\nFilter ${i + 1}: ON $kind Fc ${b.freqHz.toLong()} Hz Gain ${fmt(b.gainDb)} dB Q ${String.format(Locale.US, "%.3f", b.q)}")
        }
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)
}
