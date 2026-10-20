package com.wellmosphere.freedsp.cable

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/**
 * One guarded session with the original FreeDSP: identity and layout checks, temporary takeover of HID
 * interface 3, the caller's exchanges, then release and a verified usbhid rebind. Interfaces 0–2 (audio)
 * are never touched. Call from one background thread at a time.
 */
class CableLink(context: Context) {
    val manager: UsbManager = context.getSystemService(UsbManager::class.java)
    /** Written (fsynced) before usbhid may be detached; removed once it is verified bound again. */
    private val markerFile = File(context.noBackupFilesDir, "hid-takeover-pending.json")
    val restorePending get() = markerFile.exists()

    private val marker = object : HidTakeover.Marker {
        override fun set() = FileOutputStream(markerFile).use {
            it.write("""{"startedAt":"${Instant.now()}"}""".toByteArray()); it.fd.sync()
        }
        override fun clear() { markerFile.delete() }
    }

    fun find(): UsbDevice? = manager.deviceList.values.firstOrNull { it.vendorId == Gen1.VID && it.productId == Gen1.PID }

    /** A replug re-enumerates the cable with usbhid bound, so an interrupted takeover needs no restore. */
    fun detached() { markerFile.delete() }

    fun <T> session(device: UsbDevice, block: (CableOps.Transport) -> T): T = open(device) { connection ->
        val takeover = HidTakeover({ native(connection, it) }, marker) { Log.i(TAG, it) }
        var claimed = false
        val result = runCatching {
            claimed = takeover.claim()
            check(claimed) { takeover.failureDetail }
            val length = HidDescriptor.reportLengths(connection.rawDescriptors ?: byteArrayOf())[Gen1.HID_INTERFACE]
                ?: error("The cable doesn't describe its control channel")
            val descriptor = ByteArray(length)
            val n = connection.controlTransfer(0x81, 6, 0x2200, Gen1.HID_INTERFACE, descriptor, length, TIMEOUT)
            check(n == length) { "The cable's control channel didn't answer ($n/$length)" }
            check(HidDescriptor.parse(descriptor).any { it.isGen1 }) { "This cable's control channel isn't the one FreeDSP uses" }
            block(Transport(connection))
        }
        // Exactly one rebind on every path that may have detached usbhid; no retries.
        val restored = when {
            claimed -> runCatching { takeover.release() }.getOrDefault(false)
            takeover.restorePending -> runCatching { takeover.reconnect() }.getOrDefault(false)
            else -> true
        }
        if (!restored) throw RestoreFailed(result.exceptionOrNull())
        result.getOrThrow()
    }

    class RestoreFailed(cause: Throwable?) : IllegalStateException(
        "The cable's buttons may not work until you unplug and replug it" + (cause?.message?.let { ". Also: $it" } ?: ""), cause)

    /** For a takeover interrupted by the app being killed: rebinds usbhid only if nothing holds interface 3. */
    fun restore(device: UsbDevice): HidTakeover.Restore = open(device) { connection ->
        HidTakeover({ native(connection, it) }, marker) { Log.i(TAG, it) }.recover()
    }

    private fun <T> open(device: UsbDevice, use: (UsbDeviceConnection) -> T): T {
        check(device.vendorId == Gen1.VID && device.productId == Gen1.PID) { "That isn't an original FreeDSP cable" }
        check(manager.hasPermission(device)) { "USB access hasn't been allowed yet" }
        val hid = (0 until device.interfaceCount).map { device.getInterface(it) }
            .filter { it.interfaceClass == UsbConstants.USB_CLASS_HID && it.alternateSetting == 0 }
        check(device.configurationCount == 1 && hid.singleOrNull()?.id == Gen1.HID_INTERFACE) { "Unexpected USB layout; nothing was sent" }
        val connection = manager.openDevice(device) ?: error("Android couldn't open the cable")
        try {
            check(connection.fileDescriptor >= 0) { "Android couldn't open the cable" }
            val id = ByteArray(18)
            val n = connection.controlTransfer(0x80, 6, 0x0100, 0, id, 18, TIMEOUT)
            fun word(i: Int) = (id[i].toInt() and 255) or ((id[i + 1].toInt() and 255) shl 8)
            check(n == 18 && word(8) == Gen1.VID && word(10) == Gen1.PID) { "The cable didn't identify itself; nothing was sent" }
            return use(connection)
        } finally {
            connection.close()
        }
    }

    private fun native(connection: UsbDeviceConnection, op: NativeHidAccess.Op) =
        NativeHidAccess.execute(connection.fileDescriptor, Gen1.HID_INTERFACE, op)

    private class Transport(private val connection: UsbDeviceConnection) : CableOps.Transport {
        override fun send(frame: Gen1.Frame) {
            val n = connection.controlTransfer(0x21, 0x09, 0x0201, Gen1.HID_INTERFACE, frame.bytes, Gen1.FRAME_LENGTH, TIMEOUT)
            check(n == Gen1.FRAME_LENGTH) { "Sending ${frame.label} failed ($n/${Gen1.FRAME_LENGTH})" }
        }
        override fun receive(): ByteArray {
            val buffer = ByteArray(Gen1.FRAME_LENGTH)
            val n = connection.controlTransfer(0xA1, 0x01, 0x0101, Gen1.HID_INTERFACE, buffer, Gen1.FRAME_LENGTH, TIMEOUT)
            check(n >= 0) { "The cable stopped answering" }
            return buffer.copyOf(n)
        }
        override fun pause(ms: Long) = Thread.sleep(ms)
    }

    companion object {
        const val TAG = "FreeDSP"
        private const val TIMEOUT = 1000
    }
}
