package com.wellmosphere.freedsp.cable

import org.junit.Assert.*
import org.junit.Test

class Gen1Test {
    private fun hex(text: String) = text.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
    private fun frame(prefix: String) = hex(prefix).copyOf(Gen1.FRAME_LENGTH)

    @Test fun readFramesMatchTheProvenSelectors() {
        assertArrayEquals(frame("01 00 0D 00 5A 01 00 23 2D B3 5A"), Gen1.readMode().bytes)
        for (n in 1..9) assertArrayEquals(frame("01 00 0D 00 DD 01 00 23 2D B3 %02X".format(n)), Gen1.readBand(n).bytes)
    }

    // Golden vectors computed independently in Python from upstream's formulas.
    @Test fun writeFramesMatchGoldenVectors() {
        assertArrayEquals(frame("01 00 0D 00 5A 00 00 23 2D B3 5A"), Gen1.selectCustomBank().bytes)
        assertArrayEquals(frame("01 00 0D 00 DC 00 00 23 2D B3 00 00 00 00 01 00 00 00 67 00 00 00 B4"),
            Gen1.writeMetadata(1, Gen1.Metadata(103, 180, 0, 0)).bytes)
        assertArrayEquals(frame("01 00 0D 00 DC 00 00 23 2D B3 05 00 00 00 01 00 00 00 03 00 00 00 00 00 40 00 20 3A 81 FF D3 C8 3E 00 E0 C5 7E 00 2D 37 C1 FF"),
            Gen1.writeCoefficients(5, 1, intArrayOf(4194304, -8308192, 4114643, 8308192, -4114643)).bytes)
        assertArrayEquals(frame("01 00 0D 00 DC 00 00 23 2D B3 FF"), Gen1.commit().bytes)
    }

    @Test fun negativeGainIsSigned24Bit() {
        val b = Gen1.writeMetadata(5, Gen1.Metadata(909, 180, 0, -3)).bytes
        assertArrayEquals(hex("FD FF FF 00"), b.copyOfRange(30, 34))
    }

    @Test fun outOfRangeValuesCannotBeFramed() {
        for (m in listOf(Gen1.Metadata(19, 180, 0, 0), Gen1.Metadata(103, 50, 0, 0), Gen1.Metadata(103, 180, 3, 0), Gen1.Metadata(103, 180, 0, 13)))
            assertThrows(IllegalArgumentException::class.java) { Gen1.writeMetadata(1, m) }
        for (band in listOf(0, 10)) assertThrows(IllegalArgumentException::class.java) { Gen1.readBand(band) }
        assertThrows(IllegalArgumentException::class.java) { Gen1.writeCoefficients(3, 1, IntArray(5)) }
        assertThrows(IllegalArgumentException::class.java) { Gen1.writeCoefficients(4, 1, IntArray(4)) }
    }

    // Captured from the physical cable.
    private val capturedMode = frame("01 00 0D 00 5A 81 00 23 2D B3 5A")
    private val capturedBand3 = frame("01 00 06 00 DD 81 00 23 2D B3 05 00 00 00 03 00 00 00 32 01 00 00 B4")

    @Test fun capturedRepliesDecode() {
        assertEquals(0, Gen1.decodeMode(capturedMode))
        assertEquals(Gen1.Metadata(306, 180, 0, 0), Gen1.decodeBand(3, capturedBand3))
    }

    @Test fun badRepliesAreRejectedNotGuessed() {
        assertThrows(IllegalArgumentException::class.java) { Gen1.decodeBand(4, capturedBand3) } // echo mismatch
        assertThrows(IllegalArgumentException::class.java) { Gen1.decodeBand(3, capturedBand3.copyOf(33)) }
        assertThrows(IllegalArgumentException::class.java) { Gen1.decodeBand(3, capturedBand3.copyOf(63)) }
        assertThrows(IllegalArgumentException::class.java) { Gen1.decodeBand(3, capturedBand3.clone().also { it[5] = 1 }) }
        assertThrows(IllegalArgumentException::class.java) { Gen1.decodeBand(3, capturedMode) }
        assertThrows(IllegalArgumentException::class.java) { Gen1.decodeMode(capturedBand3) }
    }
}
