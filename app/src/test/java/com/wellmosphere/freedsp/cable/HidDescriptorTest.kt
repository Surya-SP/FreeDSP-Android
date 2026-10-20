package com.wellmosphere.freedsp.cable

import org.junit.Assert.*
import org.junit.Test

class HidDescriptorTest {
    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()
    // Synthetic layout fixture, NOT a captured cable descriptor.
    private val gen1 = bytes(0x05, 0x0c, 0x09, 1, 0xa1, 1, 0x85, 1, 0x75, 8, 0x95, 61, 0x81, 2, 0x91, 2, 0xc0)
    @Test fun exactReportLayoutPasses() { assertTrue(HidDescriptor.parse(gen1).single().isGen1) }
    @Test fun gen2AndWrongLengthsDoNotPass() {
        assertFalse(HidDescriptor.parse(gen1.clone().also { it[7] = 0x4b }).any { it.isGen1 })
        assertFalse(HidDescriptor.parse(gen1.clone().also { it[11] = 62 }).any { it.isGen1 })
    }
    @Test fun parsesPushPopAndNonByteAlignedReports() {
        val d = bytes(0x85, 1, 0x75, 1, 0x95, 3, 0x81, 2, 0xa4, 0x85, 2, 0x95, 8, 0x91, 2, 0xb4, 0x81, 2)
        val reports = HidDescriptor.parse(d)
        assertEquals(6L, reports[0].inputBits); assertEquals(1L, reports[0].inputBytes)
        assertEquals(1L, reports[1].outputBytes)
    }
    @Test fun malformedDescriptorCannotAuthorizeQueries() {
        for (d in listOf(byteArrayOf(), bytes(0x75), bytes(0xfe, 1, 2, 0), bytes(0xb4), bytes(0xc0), gen1.copyOf(gen1.size - 1), bytes(0x85, 0))) {
            assertThrows(IllegalArgumentException::class.java) { HidDescriptor.parse(d) }
        }
    }
    @Test fun extremeCountCannotOverflowOrAuthorize() {
        val d = bytes(0x85, 1, 0x75, 32, 0x97, 255, 255, 255, 255, 0x81, 2)
        assertThrows(IllegalArgumentException::class.java) { HidDescriptor.parse(d) }
    }
    @Test fun descriptorLengthsUseInterfaceIdNotArrayPosition() {
        val raw = bytes(9, 4, 7, 0, 1, 3, 0, 0, 0, 9, 0x21, 0x11, 1, 0, 1, 0x22, 64, 0)
        assertEquals(mapOf(7 to 64), HidDescriptor.reportLengths(raw))
    }
    @Test fun audioAndAlternateInterfacesAreNotCandidates() {
        val raw = bytes(9, 4, 2, 0, 1, 1, 0, 0, 0, 9, 0x21, 0x11, 1, 0, 1, 0x22, 64, 0)
        assertTrue(HidDescriptor.reportLengths(raw).isEmpty())
        raw[5] = 3; raw[3] = 1
        assertTrue(HidDescriptor.reportLengths(raw).isEmpty())
    }
    @Test fun invalidUsbLengthFailsInsteadOfLooping() {
        assertThrows(IllegalArgumentException::class.java) { HidDescriptor.reportLengths(bytes(0, 4)) }
        assertThrows(IllegalArgumentException::class.java) { HidDescriptor.reportLengths(bytes(9, 4, 1)) }
    }
}
