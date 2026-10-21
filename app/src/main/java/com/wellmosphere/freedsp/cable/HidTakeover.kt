package com.wellmosphere.freedsp.cable

import com.wellmosphere.freedsp.cable.NativeHidAccess.Op
import com.wellmosphere.freedsp.cable.NativeHidAccess.Result

/**
 * Temporary takeover of HID interface 3 from usbhid; audio interfaces are never addressed.
 * Linux 5.4 devio.c: DISCONNECT_CLAIM unbinds only this interface; RELEASEINTERFACE and fd close do NOT rebind,
 * so every path that may have detached usbhid ends in exactly one CONNECT followed by a GETDRIVER check.
 * Process death leaves interface 3 unbound (physically confirmed): the marker survives so the app can restore.
 */
class HidTakeover(private val native: (Op) -> Result, private val marker: Marker, private val log: (String) -> Unit) {
    interface Marker { fun set(); fun clear() }
    enum class Restore { RESTORED, ALREADY_BOUND, HELD_ELSEWHERE, FAILED }

    /** True once usbhid may have been detached and restoration has not yet been attempted. */
    var restorePending = false; private set
    var failureDetail = "Takeover not attempted"; private set

    fun claim(): Boolean {
        val before = native(Op.GET_DRIVER)
        if (before.driver != "usbhid") {
            failureDetail = "The control channel is held by ${before.driver ?: "nothing"}, not Android's HID driver"
            return false
        }
        marker.set()
        restorePending = true
        val taken = native(Op.DISCONNECT_CLAIM_USBHID)
        val after = runCatching { native(Op.GET_DRIVER) }.getOrNull()
        if (taken.ok) {
            log("takeover ok, driver now ${after?.driver}")
            return true
        }
        failureDetail = "Takeover refused: ${taken.errnoName} (${taken.errno})"
        log(failureDetail)
        when {
            // IF_DRIVER guard refused or claim raced before unbind: usbhid never left.
            after?.driver == "usbhid" -> { restorePending = false; marker.clear() }
            after?.driver != null -> { restorePending = false; failureDetail += "; now held by ${after.driver}" }
            else -> reconnect() // Unbound or unknown: usbhid may have been detached before the claim failed.
        }
        return false
    }

    /** RELEASEINTERFACE then a single CONNECT, regardless of whether release succeeded. */
    fun release(): Boolean {
        val released = runCatching { native(Op.RELEASE) }.getOrNull()
        val restored = reconnect()
        return released?.ok == true && restored
    }

    /** One CONNECT attempt, then verify usbhid is bound. No retry. */
    fun reconnect(): Boolean {
        restorePending = false
        val connect = runCatching { native(Op.CONNECT) }
        val driver = runCatching { native(Op.GET_DRIVER) }.getOrNull()
        val restored = driver?.driver == "usbhid"
        if (restored) marker.clear()
        log("reconnect ${connect.getOrNull()?.result ?: connect.exceptionOrNull()?.message}, driver ${driver?.driver ?: driver?.errnoName}")
        return restored
    }

    /** Recovery for an interrupted takeover. Only connects when no driver is bound. */
    fun recover(): Restore {
        val before = native(Op.GET_DRIVER)
        return when {
            before.driver == "usbhid" -> { marker.clear(); Restore.ALREADY_BOUND }
            before.noDriver -> if (reconnect()) Restore.RESTORED else Restore.FAILED
            else -> Restore.HELD_ELSEWHERE
        }
    }
}
