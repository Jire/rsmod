package org.rsmod.plugins.info.player

import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import org.rsmod.plugins.info.buffer.BitBuffer
import org.rsmod.plugins.info.buffer.FastBitBuf
import org.rsmod.plugins.info.buffer.IBitBuffer
import org.rsmod.plugins.info.buffer.SimpleBuffer
import org.rsmod.plugins.info.buffer.isCapped
import org.rsmod.plugins.info.model.coord.HighResCoord
import org.rsmod.plugins.info.player.bitcode.GPIBitCodes.putHighResUpdate
import org.rsmod.plugins.info.player.bitcode.GPIBitCodes.putHighToLowResChange
import org.rsmod.plugins.info.player.bitcode.GPIBitCodes.putLowResUpdate
import org.rsmod.plugins.info.player.bitcode.GPIBitCodes.putLowToHighResChange
import org.rsmod.plugins.info.player.bitcode.GPIBitCodes.putSkipCount
import org.rsmod.plugins.info.player.client.Avatar
import org.rsmod.plugins.info.player.client.Client
import org.rsmod.plugins.info.player.client.Client.Companion.extendedInfoBlockIndex
import org.rsmod.plugins.info.player.client.Client.Companion.isInitDynamicExtInfoIndex
import org.rsmod.plugins.info.player.client.Client.Companion.isInitStaticExtInfoIndex
import org.rsmod.plugins.info.player.client.Client.Companion.trimExtendedInfoIndex
import org.rsmod.plugins.info.player.client.clean
import org.rsmod.plugins.info.player.client.isInvalid
import org.rsmod.plugins.info.player.client.isValid
import org.rsmod.plugins.info.player.extend.ExtendedInfoBlock
import org.rsmod.plugins.info.player.extend.ExtendedInfoSizes
import java.nio.ByteBuffer
import kotlin.math.max

private typealias AvatarGroup = Array<Avatar>
private typealias ClientGroup = Array<Client>
private typealias ExtInfoBuffers = Array<SimpleBuffer>

public class PlayerInfo(public val playerLimit: Int = DEFAULT_PLAYER_LIMIT) {

    public val capacity: Int = playerLimit + INDEX_PADDING
    public val indices: IntRange = INDEX_PADDING until capacity

    public val avatars: AvatarGroup = AvatarGroup(capacity)
    public val clients: ClientGroup = ClientGroup(capacity)

    public val extendedHighRes: ExtInfoBuffers = ExtInfoBuffers(capacity, EXT_INFO_BUFFER_SIZE)
    public val extendedInitStatic: ExtInfoBuffers = ExtInfoBuffers(capacity, CACHED_EXT_INFO_BUFFER_SIZE)
    public val extendedInitDynamic: ExtInfoBuffers = ExtInfoBuffers(capacity, CACHED_EXT_INFO_BUFFER_SIZE)

    public fun register(playerIndex: Int) {
        check(avatars[playerIndex].isInvalid) { "Player(index=$playerIndex) was not unregistered." }
        val client = clients[playerIndex]
        val avatar = avatars[playerIndex]
        avatar.registered = true
        client.isHighResolution[playerIndex] = true
        client.viewDistance = PREFERRED_VIEW_DISTANCE
    }

    public fun unregister(playerIndex: Int) {
        avatars[playerIndex].clean()
        clients[playerIndex].clean()
    }

    public fun updateCoords(playerIndex: Int, coords: HighResCoord, prevCoords: HighResCoord) {
        assert(avatars[playerIndex].isValid) { "Player(index=$playerIndex) was not registered." }
        val avatar = avatars[playerIndex]
        avatar.coords = coords
        avatar.loCoords = coords.toLowRes()
        avatar.prevCoords = prevCoords
        avatar.loPrevCoords = prevCoords.toLowRes()
    }

    public fun updateExtendedInfo(playerIndex: Int, data: ByteArray, length: Int = data.size) {
        assert(avatars[playerIndex].isValid) { "Player(index=$playerIndex) was not registered." }
        assert(length <= EXT_INFO_BUFFER_SIZE) {
            "Extended info buffer capacity reached. (capacity=$EXT_INFO_BUFFER_SIZE)"
        }
        avatars[playerIndex].extendedInfoLength = length
        extendedHighRes[playerIndex].clear().putBytes(data, length)
    }

    public fun cacheStaticExtendedInfo(playerIndex: Int, data: ByteArray, length: Int = data.size) {
        assert(avatars[playerIndex].isValid) { "Player(index=$playerIndex) was not registered." }
        assert(length <= CACHED_EXT_INFO_BUFFER_SIZE) {
            "Cached extended info buffer capacity reached. (capacity=$CACHED_EXT_INFO_BUFFER_SIZE)"
        }
        extendedInitStatic[playerIndex].clear().putBytes(data, length)
    }

    public fun cacheDynamicExtendedInfo(playerIndex: Int, gameClock: Int, data: ByteArray, length: Int = data.size) {
        assert(avatars[playerIndex].isValid) { "Player(index=$playerIndex) was not registered." }
        assert(length <= CACHED_EXT_INFO_BUFFER_SIZE) {
            "Cached extended info buffer capacity reached. (capacity=$CACHED_EXT_INFO_BUFFER_SIZE)"
        }
        avatars[playerIndex].dynamicExtInfoUpdateClock = gameClock
        extendedInitDynamic[playerIndex].clear().putBytes(data, length)
    }

    public fun put(buf: ByteBuffer, playerIndex: Int, metadata: PlayerInfoMetadata = PlayerInfoMetadata()) {
        if (true) {
            buf.clear()
            val bb = PooledByteBufAllocator.DEFAULT.directBuffer(buf.remaining(), buf.capacity())
            try {
                bb.writeBytes(buf)

                put(bb, FastBitBuf(bb), playerIndex, metadata)

                buf.clear()
                buf.limit(bb.readableBytes())
                bb.readBytes(buf)
                buf.flip()
            } finally {
                bb.release()
            }
            return
        }

        oldPut(buf, playerIndex, metadata)
    }

    public fun oldPut(buf: ByteBuffer, playerIndex: Int, metadata: PlayerInfoMetadata = PlayerInfoMetadata()) {
        val avatar = avatars[playerIndex]
        val client = clients[playerIndex]
        buf.clear()
        BitBuffer(buf).use { it.putHighResolution(true, avatar.coords, client, metadata) }
        BitBuffer(buf).use { it.putHighResolution(false, avatar.coords, client, metadata) }
        BitBuffer(buf).use { it.putLowResolution(false, avatar.coords, client, metadata) }
        BitBuffer(buf).use { it.putLowResolution(true, avatar.coords, client, metadata) }
        buf.putExtendedInfo(metadata.extendedInfoCount, client.extendedInfoIndexes)
        buf.flip()
        shift(client)
        resize(client, metadata.highResolutionCount)
    }

    public fun put(
        buf: ByteBuf,
        bitBuf: IBitBuffer,
        playerIndex: Int,
        metadata: PlayerInfoMetadata = PlayerInfoMetadata()
    ) {
        val avatar = avatars[playerIndex]
        val client = clients[playerIndex]
        buf.clear()
        bitBuf.reset()
        bitBuf.use { it.putHighResolution(true, avatar.coords, client, metadata) }
        bitBuf.use { it.putHighResolution(false, avatar.coords, client, metadata) }
        bitBuf.use { it.putLowResolution(false, avatar.coords, client, metadata) }
        bitBuf.use { it.putLowResolution(true, avatar.coords, client, metadata) }
        buf.putExtendedInfo(metadata.extendedInfoCount, client.extendedInfoIndexes)
        shift(client)
        resize(client, metadata.highResolutionCount)
    }

    public fun shift(client: Client): Unit = with(client) {
        for (i in indices) {
            isHighResolution[i] = isHighResolution[i] xor pendingResolutionChange[i]
            pendingResolutionChange[i] = false
            activityFlags[i] = (activityFlags[i].toInt() shr 1).toByte()
        }
    }

    public fun resize(client: Client, highResCount: Int): Unit = with(client) {
        if (highResCount >= PREFERRED_VIEW_DISTANCE_PLAYER_COUNT) {
            viewDistance = max(0, viewDistance - 1)
            resizeViewDistanceInterval = 0
            return
        }
        if (++resizeViewDistanceInterval >= VIEW_DISTANCE_RESIZE_INTERVALS) {
            if (viewDistance < PREFERRED_VIEW_DISTANCE) {
                viewDistance++
            } else {
                resizeViewDistanceInterval = 0
            }
        }
    }

    public fun IBitBuffer.putHighResolution(
        active: Boolean,
        coords: HighResCoord,
        client: Client,
        metadata: PlayerInfoMetadata
    ): Unit = with(client) {
        var skip = 0
        for (i in indices) {
            if (!isHighResolution[i]) continue
            val inactive = (activityFlags[i].toInt() and INACTIVE_FLAG) != 0
            if (inactive == active) continue
            if (skip > 0) {
                skip--
                activityFlags[i] = (activityFlags[i].toInt() or ACTIVE_TO_INACTIVE_FLAG).toByte()
                continue
            }
            val other = avatars[i]
            if (other.isInvalid || !coords.inViewDistance(other.coords, viewDistance)) {
                pendingResolutionChange[i] = true
                metadata.highResolutionCount++
                putHighToLowResChange(other.loCoords, other.loPrevCoords)
                continue
            }
            if (other.extendedInfoLength != 0) {
                val currLength = metadata.extendedInfoLength + other.extendedInfoLength
                val bufferCapped = isCapped(currLength, CACHED_EXT_INFO_SAFETY_BUFFER)
                if (!bufferCapped) {
                    // TODO: turn on dynamic-extended-info-only mode in metadata
                    // to use later in putExtendedInfo
                    val highResBufferCapped = isCapped(currLength, HIGH_RES_SAFETY_BUFFER)
                    if (!highResBufferCapped) {
                        extendedInfoIndexes[metadata.extendedInfoCount++] = i.toShort()
                        // if we read extended info from high-resolution we should
                        // have the dynamic-extended-info up-to-date as well.
                        extendedInfoClocks[i] = other.dynamicExtInfoUpdateClock
                        metadata.extendedInfoLength += other.extendedInfoLength
                        metadata.highResolutionCount++
                        putHighResUpdate(extended = true, other.coords, other.prevCoords)
                        continue
                    }
                }
            }
            // Previous coords being "0" signifies that the high-res player has
            // registered this tick. The only player this _should_ be possible for
            // is the "local" player. (when they first log in, they are set as high-res)
            if (other.coords != other.prevCoords && other.prevCoords.packed != 0) {
                metadata.highResolutionCount++
                putHighResUpdate(extended = false, other.coords, other.prevCoords)
                continue
            }
            skip = highResolutionSkipCount(
                active = active,
                startIndex = i + 1,
                client = client
            )
            activityFlags[i] = (activityFlags[i].toInt() or ACTIVE_TO_INACTIVE_FLAG).toByte()
            metadata.highResolutionSkip += skip
            metadata.highResolutionCount += skip + 1
            putSkipCount(skip)
        }
    }

    public fun highResolutionSkipCount(
        active: Boolean,
        startIndex: Int,
        client: Client
    ): Int {
        var skip = 0
        var reachedTail = false
        for (i in startIndex until capacity) {
            if (!client.isHighResolution[i]) continue
            val inactive = (client.activityFlags[i].toInt() and INACTIVE_FLAG) != 0
            if (inactive == active) continue
            reachedTail = i == playerLimit
            val other = avatars[i]
            val update = other.isInvalid || other.extendedInfoLength != 0 ||
                other.coords != other.prevCoords
            if (update) break
            skip++
        }
        if (playerLimit != DEFAULT_PLAYER_LIMIT && reachedTail) {
            skip += DEFAULT_PLAYER_LIMIT - playerLimit
        }
        return skip
    }

    public fun IBitBuffer.putLowResolution(
        active: Boolean,
        coords: HighResCoord,
        client: Client,
        metadata: PlayerInfoMetadata
    ): Unit = with(client) {
        var skip = 0
        for (i in indices) {
            if (isHighResolution[i]) continue
            val inactive = (activityFlags[i].toInt() and INACTIVE_FLAG) != 0
            if (inactive == active) continue
            if (skip > 0) {
                skip--
                activityFlags[i] = (activityFlags[i].toInt() or ACTIVE_TO_INACTIVE_FLAG).toByte()
                continue
            }
            val other = avatars[i]
            if (other.isValid) {
                if (coords.inViewDistance(other.coords, viewDistance)) {
                    var updateExtendedInfo = false
                    val extendedBlock = other.getExtendedInfoBlock(extendedInfoClocks[i])
                    // NOTE: we _could_ use the cached extended info buffers to get the
                    // actual length. however - this avoids skipping around in memory.
                    // The above suggestion was benchmarked. It led to an approximate
                    // 25% performance degradation.
                    val extendedLength = if (extendedBlock == ExtendedInfoBlock.None) 0 else CACHED_EXT_INFO_BUFFER_SIZE
                    // The amount of bytes that can be written for the other low-res
                    // players left in the iteration. Note that this still takes
                    // high-res players into account.
                    // A possible workaround is to calculate the amount of low-res
                    // players beforehand and feed it as an argument.
                    val possibleBytesLeft = (indices.last - i) * MAX_BYTES_PER_LOW_RES_PLAYER
                    if (
                        extendedLength != 0 && extendedBlock != ExtendedInfoBlock.None &&
                        !isCapped(
                            metadata.extendedInfoLength + extendedLength + possibleBytesLeft,
                            CACHED_EXT_INFO_SAFETY_BUFFER
                        )
                    ) {
                        val ringIndex = metadata.extendedInfoCount++
                        val offsetIndex = extendedInfoBlockIndex(playerIndex = i, extendedBlock)
                        extendedInfoIndexes[ringIndex] = offsetIndex
                        extendedInfoClocks[i] = other.dynamicExtInfoUpdateClock
                        metadata.extendedInfoLength += extendedLength
                        updateExtendedInfo = true
                    }
                    pendingResolutionChange[i] = true
                    metadata.lowResolutionCount++
                    putLowToHighResChange(other)
                    putBoolean(updateExtendedInfo)
                    activityFlags[i] = (activityFlags[i].toInt() or ACTIVE_TO_INACTIVE_FLAG).toByte()
                    continue
                }
                val currLowResCoords = other.loCoords
                val prevLowResCoords = other.loPrevCoords
                if (currLowResCoords != prevLowResCoords) {
                    metadata.lowResolutionCount++
                    putLowResUpdate(currLowResCoords, prevLowResCoords)
                    continue
                }
            }
            skip = lowResolutionSkipCount(
                active = active,
                startIndex = i + 1,
                coords = coords,
                client = client
            )
            activityFlags[i] = (activityFlags[i].toInt() or ACTIVE_TO_INACTIVE_FLAG).toByte()
            metadata.lowResolutionSkip += skip
            metadata.lowResolutionCount += skip + 1
            putSkipCount(skip)
        }
    }

    public fun lowResolutionSkipCount(
        active: Boolean,
        startIndex: Int,
        coords: HighResCoord,
        client: Client
    ): Int {
        var skip = 0
        var reachedTail = false
        for (i in startIndex until capacity) {
            if (client.isHighResolution[i]) continue
            val inactive = (client.activityFlags[i].toInt() and INACTIVE_FLAG) != 0
            if (inactive == active) continue
            reachedTail = i == playerLimit
            val other = avatars[i]
            if (other.isInvalid) {
                skip++
                continue
            }
            val update = other.loCoords != other.loPrevCoords ||
                coords.inViewDistance(other.coords, client.viewDistance)
            if (update) break
            skip++
        }
        if (playerLimit != DEFAULT_PLAYER_LIMIT && reachedTail) {
            skip += DEFAULT_PLAYER_LIMIT - playerLimit
        }
        return skip
    }

    public fun ByteBuffer.putExtendedInfo(count: Int, indexes: ShortArray) {
        for (i in 0 until count) {
            val offsetIndex = indexes[i].toInt()
            val playerIndex = trimExtendedInfoIndex(offsetIndex)
            val buffer = when {
                isInitDynamicExtInfoIndex(offsetIndex) -> extendedInitDynamic[playerIndex]
                isInitStaticExtInfoIndex(offsetIndex) -> extendedInitStatic[playerIndex]
                else -> extendedHighRes[playerIndex]
            }
            put(buffer.data, 0, buffer.offset)
        }
    }

    public fun ByteBuf.putExtendedInfo(count: Int, indexes: ShortArray) {
        for (i in 0 until count) {
            val offsetIndex = indexes[i].toInt()
            val playerIndex = trimExtendedInfoIndex(offsetIndex)
            val buffer = when {
                isInitDynamicExtInfoIndex(offsetIndex) -> extendedInitDynamic[playerIndex]
                isInitStaticExtInfoIndex(offsetIndex) -> extendedInitStatic[playerIndex]
                else -> extendedHighRes[playerIndex]
            }
            writeBytes(buffer.data, 0, buffer.offset)
        }
    }

    override fun toString(): String {
        return "PlayerInfo(playerLimit=$playerLimit)"
    }

    public companion object {

        public const val INDEX_PADDING: Int = 1
        public const val DEFAULT_PLAYER_LIMIT: Int = 2047

        public const val PREFERRED_VIEW_DISTANCE: Int = 15
        public const val VIEW_DISTANCE_RESIZE_INTERVALS: Int = 10
        public const val PREFERRED_VIEW_DISTANCE_PLAYER_COUNT: Int = 250

        public const val DEFAULT_BUFFER_LIMIT: Int = 40_000
        public const val HIGH_RES_SAFETY_BUFFER: Int = 5000
        public const val CACHED_EXT_INFO_SAFETY_BUFFER: Int = 2500

        public const val EXT_INFO_BUFFER_SIZE: Int = ExtendedInfoSizes.TOTAL_BYTE_SIZE
        public const val CACHED_EXT_INFO_BUFFER_SIZE: Int = 75

        public const val ACTIVE_FLAG: Int = 0
        public const val INACTIVE_FLAG: Int = 0x1
        public const val ACTIVE_TO_INACTIVE_FLAG: Int = 0x2

        private const val MAX_BYTES_PER_LOW_RES_PLAYER = 4

        private fun HighResCoord.inViewDistance(other: HighResCoord, viewDistance: Int): Boolean {
            /*            return other.level == level &&
                            x - other.x in -viewDistance..viewDistance &&
                            z - other.z in -viewDistance..viewDistance*/
            return level == other.level &&
                deltaWithinDistance(x, other.x, viewDistance) &&
                deltaWithinDistance(z, other.z, viewDistance)
        }

        @Suppress("NOTHING_TO_INLINE")
        private inline fun fastAbs(value: Int): Int {
            val mask = value shr 31
            return (value xor mask) - mask
        }

        private fun delta(a: Int, b: Int) = fastAbs(a - b)

        private fun deltaWithinDistance(a: Int, b: Int, distance: Int) = delta(a, b) <= distance

        private fun Avatar.getExtendedInfoBlock(sourceExtInfoClock: Int): ExtendedInfoBlock = when {
            sourceExtInfoClock == 0 -> ExtendedInfoBlock.InitStatic
            sourceExtInfoClock != dynamicExtInfoUpdateClock -> ExtendedInfoBlock.InitDynamic
            else -> ExtendedInfoBlock.None
        }

        @Suppress("FunctionName")
        private fun AvatarGroup(capacity: Int): AvatarGroup {
            return Array(capacity) { Avatar() }
        }

        @Suppress("FunctionName")
        private fun ClientGroup(capacity: Int): ClientGroup {
            return Array(capacity) { Client(capacity) }
        }

        @Suppress("FunctionName")
        private fun ExtInfoBuffers(playerCapacity: Int, bufferSize: Int): ExtInfoBuffers {
            return Array(playerCapacity) { SimpleBuffer(bufferSize) }
        }
    }
}
