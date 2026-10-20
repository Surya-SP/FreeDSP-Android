package com.wellmosphere.freedsp.cable

/** Bounded, fail-closed HID descriptor parser. Counts bits before rounding to bytes. */
object HidDescriptor {
    data class Report(val id: Int, val inputBits: Long, val outputBits: Long, val featureBits: Long) {
        val inputBytes get() = (inputBits + 7) / 8
        val outputBytes get() = (outputBits + 7) / 8
        val featureBytes get() = (featureBits + 7) / 8
        val isGen1 get() = id == 1 && inputBits == 488L && outputBits == 488L
    }
    private data class Globals(var id: Int = 0, var size: Long = 0, var count: Long = 0)

    fun parse(data: ByteArray): List<Report> {
        require(data.isNotEmpty() && data.size <= 4096) { "Invalid descriptor size" }
        val bits = linkedMapOf<Int, LongArray>()
        var global = Globals()
        val stack = ArrayDeque<Globals>()
        var collectionDepth = 0
        var offset = 0
        while (offset < data.size) {
            val prefix = data[offset].toInt() and 255
            require(prefix != 0xFE) { "Long HID items are not supported; query disabled" }
            val length = if (prefix and 3 == 3) 4 else prefix and 3
            require(offset + 1 + length <= data.size) { "Truncated HID item at $offset" }
            var value = 0L
            for (i in 0 until length) value = value or ((data[offset + 1 + i].toLong() and 255) shl (8 * i))
            when (prefix and 0xFC) {
                0x84 -> { require(value in 1..255); global.id = value.toInt() }
                0x74 -> { require(value <= 32768); global.size = value }
                0x94 -> { require(value <= 32768); global.count = value }
                0xA4 -> { require(stack.size < 32); stack.addLast(global.copy()) }
                0xB4 -> { require(stack.isNotEmpty()) { "HID global stack underflow" }; global = stack.removeLast() }
                0xA0 -> { collectionDepth++; require(collectionDepth <= 32) }
                0xC0 -> { collectionDepth--; require(collectionDepth >= 0) }
                0x80, 0x90, 0xB0 -> {
                    require(global.size > 0 && global.count > 0) { "Missing HID report size/count" }
                    val direction = when (prefix and 0xFC) { 0x80 -> 0; 0x90 -> 1; else -> 2 }
                    val record = bits.getOrPut(global.id) { LongArray(3) }
                    record[direction] += global.size * global.count
                    require(record[direction] <= 32768) { "Oversized HID report" }
                }
            }
            offset += 1 + length
        }
        require(collectionDepth == 0 && stack.isEmpty()) { "Unbalanced HID descriptor" }
        return bits.map { (id, b) -> Report(id, b[0], b[1], b[2]) }
    }

    /** Locate a report-descriptor length in raw USB configuration descriptors, without guessing an interface number. */
    fun reportLengths(raw: ByteArray): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        var offset = 0
        var hidInterface: Int? = null
        while (offset < raw.size) {
            require(offset + 2 <= raw.size) { "Truncated USB descriptor header" }
            val len = raw[offset].toInt() and 255
            val type = raw[offset + 1].toInt() and 255
            require(len >= 2 && offset + len <= raw.size) { "Malformed USB descriptor length" }
            if (type == 2) hidInterface = null
            if (type == 4) {
                require(len >= 9)
                hidInterface = if ((raw[offset + 5].toInt() and 255) == 3 && raw[offset + 3].toInt() == 0)
                    raw[offset + 2].toInt() and 255 else null
            }
            val interfaceId = hidInterface
            if (type == 0x21 && interfaceId != null) {
                require(len >= 6)
                val count = raw[offset + 5].toInt() and 255
                require(6 + 3 * count <= len)
                for (i in 0 until count) {
                    val p = offset + 6 + 3 * i
                    if ((raw[p].toInt() and 255) == 0x22) {
                        val size = (raw[p + 1].toInt() and 255) or ((raw[p + 2].toInt() and 255) shl 8)
                        require(size in 1..4096)
                        require(!result.containsKey(interfaceId)) { "Ambiguous HID report descriptor" }
                        result[interfaceId] = size
                    }
                }
            }
            offset += len
        }
        return result
    }
}
