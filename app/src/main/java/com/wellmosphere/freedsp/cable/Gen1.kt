package com.wellmosphere.freedsp.cable

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Original FreeDSP (gen-1) wire format, independently implemented from FreeDSP-Studio 1b42f63 dsp.rs (MIT).
 * Reads and writes are verified on the physical cable; writes follow upstream byte for byte.
 * Frames can only be built through the typed functions below: there is no raw command or payload API.
 */
object Gen1 {
    const val VID = 0x35D8
    const val PID = 0x1496
    const val HID_INTERFACE = 3
    const val FRAME_LENGTH = 62
    const val BANDS = 9
    private const val MODE = 0x5A
    private const val BAND_READ = 0xDD
    private const val WRITE = 0xDC
    private const val COMMIT = 0xFF
    private const val COEFFICIENT_MARKER = 3

    /** Sample-rate index and rate; the cable keeps one coefficient set per rate. */
    val SAMPLE_RATES = listOf(4 to 44_100.0, 5 to 48_000.0, 6 to 96_000.0, 7 to 192_000.0, 8 to 384_000.0)

    class Frame internal constructor(val bytes: ByteArray, val label: String)

    /** One band's stored record: what reads return and what the cable shows as its label. */
    data class Metadata(val freqHz: Int, val qRaw: Int, val type: Int, val gainDb: Int) {
        fun requireWritable() {
            require(freqHz in 20..20_000) { "Frequency $freqHz Hz is out of range" }
            require(qRaw in 51..2560) { "Q ${qRaw / 256.0} is out of range" }
            require(type in 0..2) { "Unknown filter type $type" }
            require(gainDb in -12..12) { "Gain $gainDb dB is out of range" }
        }
    }

    private fun build(label: String, cmd: Int, flag: Int, vararg args: Int): Frame {
        val bytes = ByteArray(FRAME_LENGTH)
        intArrayOf(1, 0, 0x0D, 0, cmd, flag, 0, 0x23, 0x2D, 0xB3).forEachIndexed { i, b -> bytes[i] = b.toByte() }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        args.forEachIndexed { i, v -> buffer.putInt(10 + 4 * i, v) }
        return Frame(bytes, label)
    }

    fun readMode() = build("read mode", MODE, 1, MODE)
    fun readBand(band: Int): Frame {
        require(band in 1..BANDS)
        return build("read band $band", BAND_READ, 1, band)
    }

    fun selectCustomBank() = build("select custom bank", MODE, 0, MODE, 0)
    fun writeMetadata(band: Int, m: Metadata): Frame {
        require(band in 1..BANDS)
        m.requireWritable()
        val gain24 = if (m.gainDb < 0) 0x1000000 + m.gainDb else m.gainDb
        return build("band $band settings", WRITE, 0, 0, band, m.freqHz, m.qRaw, m.type, gain24)
    }
    fun writeCoefficients(rateIndex: Int, band: Int, c: IntArray): Frame {
        require(SAMPLE_RATES.any { it.first == rateIndex } && band in 1..BANDS && c.size == 5)
        return build("band $band filter @${rateIndex}", WRITE, 0, rateIndex, band, COEFFICIENT_MARKER, *c)
    }
    fun commit() = build("commit", WRITE, 0, COMMIT)

    private fun header(reply: ByteArray, command: Int, minimum: Int) {
        require(reply.size in minimum..FRAME_LENGTH) { "Reply was ${reply.size} bytes, expected $minimum..$FRAME_LENGTH" }
        require(reply[0].toInt() == 1) { "Unexpected report ID ${reply[0]}" }
        require((reply[4].toInt() and 255) == command) { "Reply is for a different command; stopping" }
    }

    fun decodeMode(reply: ByteArray): Int {
        header(reply, MODE, 18)
        return ByteBuffer.wrap(reply).order(ByteOrder.LITTLE_ENDIAN).getInt(14)
    }

    fun decodeBand(band: Int, reply: ByteArray): Metadata {
        header(reply, BAND_READ, 34)
        require((reply[5].toInt() and 0x80) != 0) { "Band $band reply lacks the success bit" }
        val buffer = ByteBuffer.wrap(reply).order(ByteOrder.LITTLE_ENDIAN)
        // Not checked upstream; observed on the physical cable: offset 14 echoes the requested band.
        require(buffer.getInt(14) == band) { "Reply echoes band ${buffer.getInt(14)}, asked for $band; stopping" }
        var gain = (reply[30].toInt() and 255) or ((reply[31].toInt() and 255) shl 8) or ((reply[32].toInt() and 255) shl 16)
        if (gain and 0x800000 != 0) gain -= 0x1000000
        return Metadata(buffer.getInt(18), buffer.getInt(22), buffer.getInt(26), gain)
    }
}
