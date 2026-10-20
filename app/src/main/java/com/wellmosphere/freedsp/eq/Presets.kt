package com.wellmosphere.freedsp.eq

import com.wellmosphere.freedsp.eq.FilterType.HIGH_SHELF
import com.wellmosphere.freedsp.eq.FilterType.LOW_SHELF
import com.wellmosphere.freedsp.eq.FilterType.PEAK

data class Preset(val name: String, val blurb: String, val eq: Eq)

/**
 * Built-in starting points, all inside the cable's range (60 Hz–10 kHz, −12…+3 dB). They sit on the factory
 * frequencies with a low shelf on band 1 and a high shelf on band 9. With at most +3 dB per band, bigger tilts
 * come from cutting the other side; the automatic preamp keeps every one clean.
 * "Hall" and "Small room" only shape tone: the cable has an EQ, not a reverb.
 */
object Presets {
    private val FREQS = listOf(103.0, 177.0, 306.0, 527.0, 909.0, 1565.0, 2697.0, 4644.0, 7999.0)

    private fun preset(name: String, blurb: String, vararg gains: Double) = Preset(name, blurb, Eq(FREQS.mapIndexed { i, f ->
        val type = when (i) { 0 -> LOW_SHELF; FREQS.lastIndex -> HIGH_SHELF; else -> PEAK }
        Limits.clamp(Band(f, gains[i], 0.7, if (gains[i] == 0.0) PEAK else type))
    }).withSafePreamp())

    val ALL = listOf(
        preset("Flat", "The cable as it comes. Honest and untouched.", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        preset("Bass boost", "A little more weight under everything.", 3.0, 2.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        preset("Bass boost+", "Big, warm low end. Mids step back to make room.", 3.0, 3.0, 1.5, 0.0, -1.0, -2.0, -2.0, -2.0, -2.0),
        preset("Rock", "Punchy lows and bright guitars.", 3.0, 1.5, 0.0, -1.5, -2.0, -1.0, 1.0, 2.5, 3.0),
        preset("Pop", "Forward vocals with a lift on top.", 1.0, 1.0, 0.0, 0.5, 1.5, 2.0, 1.5, 1.0, 1.0),
        preset("Electronic", "Deep kick, crisp hats.", 3.0, 2.5, 0.0, -1.5, -1.0, 0.0, 1.0, 2.0, 3.0),
        preset("Jazz", "Smooth and round, with gentle air.", 1.5, 1.0, 0.5, 0.0, -0.5, 0.0, 1.0, 1.5, 1.0),
        preset("Classical", "Open and natural, a touch of shimmer.", 1.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.5, 1.5, 2.0),
        preset("Acoustic", "Woody body and clear strings.", 1.0, 1.0, 0.5, 0.0, 0.5, 1.5, 2.0, 1.5, 1.0),
        preset("Vocal", "Voices front and centre.", -2.0, -1.5, -0.5, 1.0, 2.0, 3.0, 2.5, 1.0, 0.0),
        preset("Podcast", "Clear speech, less rumble.", -4.0, -2.0, 0.0, 1.0, 2.5, 3.0, 2.0, 0.0, -1.0),
        preset("Treble boost", "More sparkle and detail.", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 3.0),
        preset("Warm", "Relaxed and cosy, softer highs.", 2.0, 1.5, 1.0, 0.0, 0.0, -0.5, -1.0, -1.5, -2.0),
        preset("Hall", "A wide, airy tone. Tone only, no echo.", 2.0, 1.5, 0.5, -1.0, -1.5, -0.5, 0.5, 1.5, 2.0),
        preset("Small room", "Close and intimate, mids forward.", 1.0, 1.0, 0.5, 1.0, 0.5, 0.0, -0.5, -1.0, -1.5),
        preset("Cinema", "Rumble for action, clear dialogue.", 3.0, 2.0, 0.0, -1.0, 0.0, 1.5, 1.5, 1.0, 1.5),
        preset("Late night", "Gentle and easy on the ears.", -1.0, 0.0, 0.0, 0.0, 0.0, -1.0, -2.0, -2.0, -3.0),
    )
}
