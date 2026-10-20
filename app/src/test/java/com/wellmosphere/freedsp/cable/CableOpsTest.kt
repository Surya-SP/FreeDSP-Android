package com.wellmosphere.freedsp.cable

import com.wellmosphere.freedsp.eq.Band
import com.wellmosphere.freedsp.eq.Eq
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A fake cable that stores band records from metadata writes and answers reads like the real one. */
class CableOpsTest {
    private inner class FakeCable : CableOps.Transport {
        val stored = MutableList(9) { Gen1.Metadata(1000, 180, 0, 0) }
        val sent = mutableListOf<String>()
        var failAt = -1
        var ignoreBand = -1
        private var last: ByteArray? = null
        override fun send(frame: Gen1.Frame) {
            if (sent.size == failAt) error("Sending ${frame.label} failed")
            sent += frame.label; last = frame.bytes
            val b = ByteBuffer.wrap(frame.bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (frame.bytes[4] == 0xDC.toByte() && b.getInt(10) == 0) {
                val band = b.getInt(14)
                var gain = b.getInt(30) and 0xFFFFFF; if (gain and 0x800000 != 0) gain -= 0x1000000
                if (band != ignoreBand) stored[band - 1] = Gen1.Metadata(b.getInt(18), b.getInt(22), b.getInt(26), gain)
            }
        }
        override fun receive(): ByteArray {
            val req = last!!; val reply = ByteArray(62); reply[0] = 1; reply[4] = req[4]; reply[5] = (req[5].toInt() or 0x80).toByte()
            val r = ByteBuffer.wrap(reply).order(ByteOrder.LITTLE_ENDIAN)
            if (req[4] == 0xDD.toByte()) {
                val n = ByteBuffer.wrap(req).order(ByteOrder.LITTLE_ENDIAN).getInt(10); val m = stored[n - 1]
                r.putInt(14, n).putInt(18, m.freqHz).putInt(22, m.qRaw).putInt(26, m.type)
                val g = if (m.gainDb < 0) 0x1000000 + m.gainDb else m.gainDb
                reply[30] = g.toByte(); reply[31] = (g shr 8).toByte(); reply[32] = (g shr 16).toByte()
            }
            return reply
        }
        override fun pause(ms: Long) {}
    }

    private val eq = Eq.FLAT.withBand(4, Band(500.0, -3.0, 1.0))

    @Test fun writeSendsThePlanThenVerifiesByReadingBack() {
        val cable = FakeCable(); val acks = mutableListOf<String>()
        val result = CableOps.write(cable, CableCoding.plan(eq)) { f, _ -> acks += f.label }
        assertTrue(result.verified)
        assertEquals(56, acks.size)
        assertEquals(56 + 10, cable.sent.size) // plan, then mode + nine band reads
        assertEquals(-3, cable.stored[4].gainDb)
    }

    @Test fun aBandThatDidNotLandIsReported() {
        val cable = FakeCable().apply { ignoreBand = 5 }
        val result = CableOps.write(cable, CableCoding.plan(eq))
        assertFalse(result.verified); assertEquals(listOf(5), result.mismatchedBands)
    }

    @Test fun firstFailureStopsEverythingIncludingTheCommit() {
        val cable = FakeCable().apply { failAt = 12 }
        assertThrows(IllegalStateException::class.java) { CableOps.write(cable, CableCoding.plan(eq)) }
        assertEquals(12, cable.sent.size); assertFalse("commit" in cable.sent)
    }

    @Test fun readStopsAtTheFirstBadReply() {
        val cable = object : CableOps.Transport by FakeCable() {
            override fun receive() = ByteArray(10)
        }
        assertThrows(IllegalArgumentException::class.java) { CableOps.read(cable) }
    }
}
