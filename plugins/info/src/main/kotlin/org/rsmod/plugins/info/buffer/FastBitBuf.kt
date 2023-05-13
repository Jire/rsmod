package org.rsmod.plugins.info.buffer

import io.netty.buffer.ByteBuf
import net.openhft.chronicle.core.Jvm
import net.openhft.chronicle.core.Memory
import net.openhft.chronicle.core.OS
import kotlin.math.min

public class FastBitBuf(public val buf: ByteBuf) : IBitBuffer {

    private var readerIndexField = buf.readerIndex() shl LOG_BITS_PER_BYTE
    private var readerIndex: Int
        get() = readerIndexField
        private set(value) {
            readerIndexField = value
            buf.readerIndex((readerIndex shr LOG_BITS_PER_BYTE))
        }

    private var writerIndexField = buf.writerIndex() shl LOG_BITS_PER_BYTE
    private var writerIndex: Int
        get() = writerIndexField
        private set(value) {
            writerIndexField = value
            buf.writerIndex((writerIndex shr LOG_BITS_PER_BYTE))
        }

    public override fun putBits(len: Int, value: Int) {
        buf.ensureWritable(len)

        setBitsMemory(writerIndex, len, value)
        writerIndex += len
    }

    public companion object {
        public const val LOG_BITS_PER_BYTE: Int = 3
        public const val BITS_PER_BYTE: Int = 1 shl LOG_BITS_PER_BYTE
        public const val MASK_BITS_PER_BYTE: Int = BITS_PER_BYTE - 1
        private const val BITS_PER_INT = 32

        init {
            Jvm.init()
        }

        public val m: Memory = OS.memory()
    }

    public fun setBitsMemory(index: Int, len: Int, value: Int) {
        var remaining = len
        var byteIndex = index shr LOG_BITS_PER_BYTE
        val bitIndex = index and MASK_BITS_PER_BYTE

        val m = m
        val baseAddress = buf.memoryAddress()

        if (remaining > 0) {
            val n = min(BITS_PER_BYTE - bitIndex, remaining)
            val shift = (BITS_PER_BYTE - (bitIndex + n)) and MASK_BITS_PER_BYTE
            val mask = (1 shl n) - 1

            val address = baseAddress + byteIndex

            var v = m.readByte(address).toInt() and 0xFF
            v = v and (mask shl shift).inv()
            v = v or (((value shr (remaining - n)) and mask) shl shift)
            m.writeByte(address, v.toByte())

            remaining -= n
            byteIndex++
        }

        while (remaining > 0) {
            val n = min(BITS_PER_BYTE, remaining)
            val shift = (BITS_PER_BYTE - n) and MASK_BITS_PER_BYTE
            val mask = (1 shl n) - 1

            val address = baseAddress + byteIndex

            var v = m.readByte(address).toInt() and 0xFF
            v = v and (mask shl shift).inv()
            v = v or (((value shr (remaining - n)) and mask) shl shift)
            m.writeByte(address, v.toByte())

            remaining -= n
            byteIndex++
        }
    }

    public fun clear() {
        /*readerIndex = 0
        writerIndex = 0*/
        buf.clear()
    }

    public override fun reset() {
        val buf = this.buf
        readerIndexField = buf.readerIndex() shl LOG_BITS_PER_BYTE
        writerIndexField = buf.writerIndex() shl LOG_BITS_PER_BYTE
    }

    override fun close() {
        val bits = (((writerIndex + MASK_BITS_PER_BYTE) and MASK_BITS_PER_BYTE.inv()) - writerIndex)
        if (bits != 0) {
            putZero(bits)
        }

        readerIndex = (readerIndex + MASK_BITS_PER_BYTE) and MASK_BITS_PER_BYTE.inv()
    }

    public override fun position(): Int {
        return writerIndex
    }

    public override fun capacity(): Long {
        return buf.capacity().toLong() shl LOG_BITS_PER_BYTE
    }
}
