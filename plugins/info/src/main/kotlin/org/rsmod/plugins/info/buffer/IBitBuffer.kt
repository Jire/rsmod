package org.rsmod.plugins.info.buffer

public interface IBitBuffer : AutoCloseable {

    public fun position(): Int

    public fun capacity(): Long

    public fun putBits(len: Int, value: Int)

    public fun putZero(len: Int) {
        putBits(len, 0)
    }

    public fun putBoolean(value: Boolean) {
        putBits(1, if (value) 1 else 0)
    }

    public fun reset() {
    }
}
