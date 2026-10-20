package com.wellmosphere.freedsp.cable

/** Read and write sequences over any [Transport]. Stops at the first failed transfer or bad reply; never retries. */
object CableOps {
    interface Transport {
        /** HID SET_REPORT(Output) of a full 62-byte frame; throws unless all 62 bytes went out. */
        fun send(frame: Gen1.Frame)
        /** HID GET_REPORT(Input); throws on a failed transfer. */
        fun receive(): ByteArray
        fun pause(ms: Long)
    }

    data class Snapshot(val mode: Int, val bands: List<Gen1.Metadata>)

    fun read(t: Transport): Snapshot {
        t.send(Gen1.readMode()); t.pause(10)
        val mode = Gen1.decodeMode(t.receive())
        val bands = (1..Gen1.BANDS).map { n ->
            t.send(Gen1.readBand(n)); t.pause(12)
            Gen1.decodeBand(n, t.receive())
        }
        return Snapshot(mode, bands)
    }

    class WriteResult(val readBack: Snapshot, val verified: Boolean, val mismatchedBands: List<Int>)

    /**
     * Upstream's sequence: each frame is followed at once by GET_REPORT (its ack, recorded but not interpreted),
     * then 6 ms; 10 ms after the commit. Success additionally requires the full read-back to match.
     */
    fun write(t: Transport, plan: CableCoding.Plan, onAck: (Gen1.Frame, ByteArray) -> Unit = { _, _ -> }): WriteResult {
        for (frame in plan.frames) {
            t.send(frame)
            onAck(frame, t.receive())
            t.pause(if (frame === plan.frames.last()) 10 else 6)
        }
        val back = read(t)
        val mismatched = plan.expected.indices.filter { plan.expected[it] != back.bands[it] }.map { it + 1 }
        return WriteResult(back, mismatched.isEmpty(), mismatched)
    }
}
