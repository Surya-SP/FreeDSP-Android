package com.wellmosphere.freedsp.cable

/** Fixed USBFS operations on the authorized connection, HID interface 3 only. No device-node opens or resets. */
object NativeHidAccess {
    enum class Op(val code: Int, val ioctl: String) {
        GET_DRIVER(0, "USBDEVFS_GETDRIVER"),
        CLAIM(1, "USBDEVFS_CLAIMINTERFACE"),
        RELEASE(2, "USBDEVFS_RELEASEINTERFACE"),
        /** Detaches and claims in one ioctl, only when the bound driver is exactly usbhid. */
        DISCONNECT_CLAIM_USBHID(3, "USBDEVFS_DISCONNECT_CLAIM(IF_DRIVER usbhid)"),
        /** USBDEVFS_IOCTL(CONNECT): lets kernel drivers re-probe this interface. */
        CONNECT(4, "USBDEVFS_IOCTL(USBDEVFS_CONNECT)"),
    }
    data class Result(val result: Int, val errno: Int, val errnoName: String, val errorMessage: String,
        val driver: String?, val elapsedUs: Long) {
        val ok get() = result >= 0
        /** GETDRIVER reports ENODATA when no kernel driver is bound to the interface. */
        val noDriver get() = !ok && errnoName == "ENODATA"
    }
    private val loaded = runCatching { System.loadLibrary("freedsp_access") }
    fun execute(fd: Int, interfaceId: Int, op: Op): Result {
        check(loaded.isSuccess) { "Native USB access unavailable: ${loaded.exceptionOrNull()?.message}" }
        require(fd >= 0 && interfaceId == 3)
        val v = call(fd, interfaceId, op.code)
        return Result(v[0].toInt(), v[1].toInt(), v[2], v[3], v[4].ifEmpty { null }, v[5].toLong())
    }
    private external fun call(fd: Int, interfaceId: Int, operation: Int): Array<String>
}
