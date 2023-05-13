package org.rsmod.plugins.info.buffer

import java.nio.ByteBuffer
import kotlin.math.min

/* BitBuf from OpenRS2 compatible with java.nio.ByteBuffer */
public class BitBuffer(
    private val buf: ByteBuffer
) : IBitBuffer {
    private var position: Int = buf.position() shl LOG_BITS_PER_BYTE
        private set(value) {
            field = value
            buf.position((position shr LOG_BITS_PER_BYTE))
        }

    private var limit: Int = buf.limit() shl LOG_BITS_PER_BYTE
        private set(value) {
            field = value
            buf.limit((limit shr LOG_BITS_PER_BYTE))
        }

    public fun getBoolean(index: Int): Boolean {
        return getBits(index, 1) != 0
    }

    public fun getBit(index: Int): Int {
        return getBits(index, 1)
    }

    public fun getBits(index: Int, len: Int): Int {
        require(len in 1..BITS_PER_INT)

        if (index < 0 || (index + len) > capacity()) {
            throw IndexOutOfBoundsException()
        }

        var value = 0

        var remaining = len
        var byteIndex = index shr LOG_BITS_PER_BYTE
        var bitIndex = index and MASK_BITS_PER_BYTE

        while (remaining > 0) {
            val n = min(BITS_PER_BYTE - bitIndex, remaining)
            val shift = (BITS_PER_BYTE - (bitIndex + n)) and MASK_BITS_PER_BYTE
            val mask = (1 shl n) - 1

            val v = buf.get(byteIndex).toInt() and 0xFF
            value = value shl n
            value = value or ((v shr shift) and mask)

            remaining -= n
            byteIndex++
            bitIndex = 0
        }

        return value
    }

    public fun getBoolean(): Boolean {
        return getBits(1) != 0
    }

    public fun getBit(): Int {
        return getBits(1)
    }

    public fun getBits(len: Int): Int {
        checkReadableBits(len)
        val value = getBits(position, len)
        position += len
        return value
    }

    public fun skipBits(len: Int): BitBuffer {
        checkReadableBits(len)
        position += len

        return this
    }

    public fun setBoolean(index: Int, value: Boolean): BitBuffer {
        if (value) {
            setBits(index, 1, 1)
        } else {
            setBits(index, 1, 0)
        }

        return this
    }

    public fun setBit(index: Int, value: Int): BitBuffer {
        setBits(index, 1, value)

        return this
    }

    public fun setBits(index: Int, len: Int, value: Int): BitBuffer {
        require(len in 1..BITS_PER_INT)

        if (index < 0 || (index + len) > capacity()) {
            throw IndexOutOfBoundsException("Buffer overflow. (index=$index, len=$len, capacity=${capacity()})")
        }

        var remaining = len
        var byteIndex = index shr LOG_BITS_PER_BYTE
        var bitIndex = index and MASK_BITS_PER_BYTE

        while (remaining > 0) {
            val n = min(BITS_PER_BYTE - bitIndex, remaining)
            val shift = (BITS_PER_BYTE - (bitIndex + n)) and MASK_BITS_PER_BYTE
            val mask = (1 shl n) - 1

            var v = buf.get(byteIndex).toInt() and 0xFF
            v = v and (mask shl shift).inv()
            v = v or (((value shr (remaining - n)) and mask) shl shift)
            buf.put(byteIndex, v.toByte())

            remaining -= n
            byteIndex++
            bitIndex = 0
        }

        return this
    }

    public fun putBit(value: Int): BitBuffer {
        putBits(1, value)

        return this
    }

    public override fun putBits(len: Int, value: Int) {
        setBits(position, len, value)
        position += len
    }

    private fun checkReadableBits(len: Int) {
        require(len >= 0)

        if ((position + len) > limit) {
            throw IndexOutOfBoundsException()
        }
    }

    public fun readableBits(): Int {
        return limit - position
    }

    public fun writableBits(): Long {
        return capacity() - position
    }

    public fun maxWritableBits(): Long {
        return maxCapacity() - position
    }

    public override fun capacity(): Long {
        return buf.limit().toLong() shl LOG_BITS_PER_BYTE
    }

    public fun maxCapacity(): Long {
        return buf.capacity().toLong() shl LOG_BITS_PER_BYTE
    }

    public fun isReadable(): Boolean {
        return position < limit
    }

    public fun isReadable(len: Int): Boolean {
        require(len >= 0)
        return (position + len) <= limit
    }

    public fun isWritable(): Boolean {
        return position < capacity()
    }

    public fun isWritable(len: Int): Boolean {
        require(len >= 0)
        return (position + len) <= capacity()
    }

    public override fun position(): Int {
        return position
    }

    public fun position(index: Int): BitBuffer {
        if (index < 0 || index > maxCapacity()) {
            throw IndexOutOfBoundsException()
        }

        position = index
        return this
    }

    public fun clear(): BitBuffer {
        position = 0
        return this
    }

    public fun flip(): BitBuffer {
        position(0)
        return this
    }

    override fun close() {
        val bits = (((position + MASK_BITS_PER_BYTE) and MASK_BITS_PER_BYTE.inv()) - position)
        if (bits != 0) {
            putZero(bits)
        }

        position = (position + MASK_BITS_PER_BYTE) and MASK_BITS_PER_BYTE.inv()
    }

    public companion object {
        public const val LOG_BITS_PER_BYTE: Int = 3
        public const val BITS_PER_BYTE: Int = 1 shl LOG_BITS_PER_BYTE
        public const val MASK_BITS_PER_BYTE: Int = BITS_PER_BYTE - 1
        private const val BITS_PER_INT = 32
    }
}
