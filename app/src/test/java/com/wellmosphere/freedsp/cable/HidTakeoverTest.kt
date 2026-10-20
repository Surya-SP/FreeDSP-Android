package com.wellmosphere.freedsp.cable

import com.wellmosphere.freedsp.cable.NativeHidAccess.Op
import com.wellmosphere.freedsp.cable.NativeHidAccess.Result
import org.junit.Assert.*
import org.junit.Test

/** Fake follows Linux 5.4 devio.c: DISCONNECT_CLAIM, RELEASEINTERFACE (no rebind) and CONNECT (device_attach). */
class HidTakeoverTest {
    private class Kernel : (Op) -> Result {
        var driver: String? = "usbhid"
        var claimFailsAfterUnbind = false
        var connectRebinds = true
        var throwOn: Op? = null
        val ops = mutableListOf<Op>()
        private fun ok(value: Int = 0, driver: String? = null) = Result(value, 0, "NONE", "Success", driver, 1)
        private fun err(name: String, errno: Int) = Result(-1, errno, name, name, null, 1)
        override fun invoke(op: Op): Result {
            ops += op
            if (op == throwOn) error("native failure")
            return when (op) {
                Op.GET_DRIVER -> driver?.let { ok(driver = it) } ?: err("ENODATA", 61)
                Op.DISCONNECT_CLAIM_USBHID -> when {
                    driver != null && driver != "usbhid" -> err("EBUSY", 16)
                    claimFailsAfterUnbind -> { driver = null; err("EBUSY", 16) }
                    else -> { driver = "usbfs"; ok() }
                }
                Op.RELEASE -> if (driver == "usbfs") { driver = null; ok() } else err("EINVAL", 22)
                Op.CONNECT -> when {
                    driver != null -> err("EBUSY", 16)
                    connectRebinds -> { driver = "usbhid"; ok(1) }
                    else -> ok(0)
                }
                Op.CLAIM -> err("EBUSY", 16)
            }
        }
    }
    private val marks = mutableListOf<String>()
    private val marker = object : HidTakeover.Marker {
        override fun set() { marks += "set" }
        override fun clear() { marks += "clear" }
    }
    private fun takeover(k: Kernel) = HidTakeover(k, marker) {}
    private val markerPending get() = marks.lastOrNull() == "set"

    @Test fun fullCycleDetachesOnceRebindsAndVerifies() {
        val k = Kernel(); val t = takeover(k)
        assertTrue(t.claim()); assertEquals("usbfs", k.driver)
        assertTrue(t.release())
        assertEquals(listOf(Op.GET_DRIVER, Op.DISCONNECT_CLAIM_USBHID, Op.GET_DRIVER, Op.RELEASE, Op.CONNECT, Op.GET_DRIVER), k.ops)
        assertEquals("usbhid", k.driver); assertEquals(listOf("set", "clear"), marks); assertFalse(t.restorePending)
    }
    @Test fun markerIsWrittenBeforeDetach() {
        val k = Kernel()
        val t = HidTakeover({ op -> if (op == Op.DISCONNECT_CLAIM_USBHID) assertEquals(listOf("set"), marks); k(op) }, marker) {}
        assertTrue(t.claim())
    }
    @Test fun anotherClientsClaimIsNeverDetached() {
        val k = Kernel().apply { driver = "usbfs" }
        assertFalse(takeover(k).claim()); assertEquals(listOf(Op.GET_DRIVER), k.ops); assertTrue(marks.isEmpty())
    }
    @Test fun claimFailureAfterUnbindReconnectsExactlyOnce() {
        val k = Kernel().apply { claimFailsAfterUnbind = true }; val t = takeover(k)
        assertFalse(t.claim())
        assertEquals(1, k.ops.count { it == Op.CONNECT }); assertEquals("usbhid", k.driver)
        assertFalse(markerPending); assertFalse(t.restorePending)
    }
    @Test fun refusalWithUsbhidStillBoundSendsNoConnect() {
        val k = Kernel()
        val t = HidTakeover({ op -> if (op == Op.DISCONNECT_CLAIM_USBHID) { k.ops += op; Result(-1, 16, "EBUSY", "busy", null, 1) } else k(op) }, marker) {}
        assertFalse(t.claim()); assertFalse(Op.CONNECT in k.ops); assertFalse(markerPending)
    }
    @Test fun releaseFailureStillReconnectsButIsNotSuccess() {
        val k = Kernel(); val t = takeover(k)
        assertTrue(t.claim()); k.driver = null
        assertFalse(t.release()); assertEquals(1, k.ops.count { it == Op.CONNECT }); assertEquals("usbhid", k.driver)
    }
    @Test fun unverifiedRebindKeepsMarker() {
        val k = Kernel().apply { connectRebinds = false }; val t = takeover(k)
        assertTrue(t.claim()); assertFalse(t.release()); assertTrue(markerPending)
    }
    @Test fun nativeExceptionDuringDetachLeavesRestorePending() {
        val k = Kernel().apply { throwOn = Op.DISCONNECT_CLAIM_USBHID }; val t = takeover(k)
        assertThrows(IllegalStateException::class.java) { t.claim() }
        assertTrue(t.restorePending); assertTrue(markerPending)
        k.throwOn = null; k.driver = null
        assertTrue(t.reconnect()); assertFalse(markerPending)
    }
    @Test fun recoverOnlyConnectsWhenNothingIsBound() {
        val unbound = Kernel().apply { driver = null }
        assertEquals(HidTakeover.Restore.RESTORED, takeover(unbound).recover()); assertEquals(listOf(Op.GET_DRIVER, Op.CONNECT, Op.GET_DRIVER), unbound.ops)
        val bound = Kernel()
        assertEquals(HidTakeover.Restore.ALREADY_BOUND, takeover(bound).recover()); assertEquals(listOf(Op.GET_DRIVER), bound.ops)
        val other = Kernel().apply { driver = "usbfs" }
        assertEquals(HidTakeover.Restore.HELD_ELSEWHERE, takeover(other).recover()); assertEquals(listOf(Op.GET_DRIVER), other.ops)
    }
}
