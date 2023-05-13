package org.rsmod.plugins.info.player.bitcode

import org.rsmod.plugins.info.buffer.IBitBuffer
import org.rsmod.plugins.info.model.coord.HighResCoord
import org.rsmod.plugins.info.model.coord.LowResCoord
import org.rsmod.plugins.info.player.client.Avatar

internal object GPIBitCodes {

    fun IBitBuffer.putHighResUpdate(
        extended: Boolean,
        currCoords: HighResCoord,
        prevCoords: HighResCoord
    ): IBitBuffer {
        putBoolean(true)
        putBoolean(extended)
        val diff = currCoords - prevCoords
        // If no movement, or if player just logged in, that means this
        // update is strictly for extended-info.
        if (extended && (diff.packed == 0 || prevCoords.packed == 0)) {
            putBits(len = 2, value = 0)
            return this
        }
        if (diff.level == 0) {
            if (diff.x in -1..1 && diff.z in -1..1) {
                putBits(len = 2, value = 1)
                putBits(len = 3, value = get3BitDirection(diff.x, diff.z))
                return this
            } else if (diff.x in -2..2 && diff.z in -2..2) {
                putBits(len = 2, value = 2)
                putBits(len = 4, value = get4BitDirection(diff.x, diff.z))
                return this
            }
        }
        if (diff.x in -15..15 && diff.z in -15..15) {
            putBits(len = 2, value = 3)
            putBoolean(false)
            putBits(len = 2, value = diff.level)
            putBits(len = 5, value = diff.x and 0x1F)
            putBits(len = 5, value = diff.z and 0x1F)
        } else {
            putBits(len = 2, value = 3)
            putBoolean(true)
            putBits(len = 2, value = diff.level)
            putBits(len = 14, value = diff.x)
            putBits(len = 14, value = diff.z)
        }
        return this
    }

    fun IBitBuffer.putHighToLowResChange(currCoords: LowResCoord, prevCoords: LowResCoord): IBitBuffer {
        val updateLowResCoords = currCoords != prevCoords
        putBoolean(true)
        putBoolean(false)
        putBits(len = 2, value = 0)
        putBoolean(updateLowResCoords)
        if (updateLowResCoords) {
            val diff = currCoords - prevCoords
            if (diff.packed == 0) { /* all coords are 0 */
                putBits(len = 2, value = 0)
            } else if (diff.x == 0 && diff.z == 0) {
                putBits(len = 2, value = 1)
                putBits(len = 2, value = diff.level)
            } else if (diff.x in -1..1 && diff.z in -1..1) {
                putBits(len = 2, value = 2)
                putBits(len = 2, value = diff.level)
                putBits(len = 3, value = get3BitDirection(diff.x, diff.z))
            } else {
                putBits(len = 2, value = 3)
                putBits(len = 2, value = diff.level)
                putBits(len = 8, value = diff.x)
                putBits(len = 8, value = diff.z)
            }
        }
        return this
    }

    fun IBitBuffer.putLowResUpdate(currCoords: LowResCoord, prevCoords: LowResCoord): IBitBuffer {
        putBoolean(true)
        putLowResCoordsChange(currCoords, prevCoords)
        return this
    }

    fun IBitBuffer.putLowToHighResChange(other: Avatar): IBitBuffer {
        val lowResCurrCoords = other.loCoords
        val lowResPrevCoords = other.loPrevCoords
        val updateLowResCoords = lowResCurrCoords != lowResPrevCoords
        putBoolean(true)
        putBits(len = 2, value = 0)
        putBoolean(updateLowResCoords)
        if (updateLowResCoords) {
            putLowResCoordsChange(lowResCurrCoords, lowResPrevCoords)
        }
        val coords = other.coords
        putBits(len = 13, value = coords.x)
        putBits(len = 13, value = coords.z)
        return this
    }

    fun IBitBuffer.putLowResCoordsChange(currCoords: LowResCoord, prevCoords: LowResCoord) {
        val diff = currCoords - prevCoords
        if (diff.x == 0 && diff.z == 0 && diff.level == 0) {
            putBits(len = 2, value = 0)
        } else if (diff.x == 0 && diff.z == 0) {
            putBits(len = 2, value = 1)
            putBits(len = 2, value = diff.level)
        } else if (diff.x in -1..1 && diff.z in -1..1) {
            putBits(len = 2, value = 2)
            putBits(len = 2, value = diff.level)
            putBits(len = 3, value = get3BitDirection(diff.x, diff.z))
        } else {
            putBits(len = 2, value = 3)
            putBits(len = 2, value = diff.level)
            putBits(len = 8, value = diff.x and 0xFF)
            putBits(len = 8, value = diff.z and 0xFF)
        }
    }

    fun IBitBuffer.putSkipCount(count: Int) {
        putBoolean(false)
        when {
            count == 0 -> putBits(len = 2, value = 0)
            count <= 0x1F -> {
                putBits(len = 2, value = 1)
                putBits(len = 5, value = count)
            }

            count <= 0xFF -> {
                putBits(len = 2, value = 2)
                putBits(len = 8, value = count)
            }

            else -> {
                putBits(len = 2, value = 3)
                putBits(len = 11, value = count)
            }
        }
    }

    private fun get3BitDirection(dx: Int, dy: Int): Int {
        require(dx != 0 || dy != 0)
        if (dx == -1 && dy == -1) return 0
        if (dx == 0 && dy == -1) return 1
        if (dx == 1 && dy == -1) return 2
        if (dx == -1 && dy == 0) return 3
        if (dx == 1 && dy == 0) return 4
        if (dx == -1 && dy == 1) return 5
        if (dx == 0 && dy == 1) return 6
        return if (dx == 1 && dy == 1) 7 else 0
    }

    private fun get4BitDirection(dx: Int, dy: Int): Int {
        require(dx != 0 || dy != 0)
        if (dx == -2 && dy == -2) return 0
        if (dx == -1 && dy == -2) return 1
        if (dx == 0 && dy == -2) return 2
        if (dx == 1 && dy == -2) return 3
        if (dx == 2 && dy == -2) return 4
        if (dx == -2 && dy == -1) return 5
        if (dx == 2 && dy == -1) return 6
        if (dx == -2 && dy == 0) return 7
        if (dx == 2 && dy == 0) return 8
        if (dx == -2 && dy == 1) return 9
        if (dx == 2 && dy == 1) return 10
        if (dx == -2 && dy == 2) return 11
        if (dx == -1 && dy == 2) return 12
        if (dx == 0 && dy == 2) return 13
        if (dx == 1 && dy == 2) return 14
        return if (dx == 2 && dy == 2) 15 else 0
    }
}
