@file:Suppress("UNUSED")

package org.rsmod.plugins.info.player

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.UnpooledUnsafeDirectByteBuf
import net.openhft.chronicle.core.Jvm
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import org.rsmod.plugins.info.buffer.FastBitBuf
import org.rsmod.plugins.info.buffer.IBitBuffer
import org.rsmod.plugins.info.model.coord.HighResCoord
import org.rsmod.plugins.info.player.PlayerInfo.Companion.CACHED_EXT_INFO_BUFFER_SIZE
import java.nio.ByteBuffer
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2)
@Measurement(iterations = 1, time = 5)
@Fork(value = 1, warmups = 2)
abstract class SingleThreadPlayerInfoBenchmark(
    private val bufCapacity: Int,
    private val startInHighRes: Boolean = false
) {

    private lateinit var info: PlayerInfo
    private lateinit var buf: ByteBuffer
    private lateinit var bitBuf: IBitBuffer
    private lateinit var byteBuf: ByteBuf
    private lateinit var staticExtInfo: ByteArray

    private val random = Random()

    @Setup
    fun setup() {
        Jvm.init()
        System.setProperty("io.netty.buffer.checkAccessible", "false")
        System.setProperty("io.netty.buffer.checkBounds", "false")

        info = PlayerInfo()
        buf = ByteBuffer.allocateDirect(bufCapacity)
        byteBuf = UnpooledUnsafeDirectByteBuf(
            ByteBufAllocator.DEFAULT,
            bufCapacity,
            bufCapacity
        )
        bitBuf = FastBitBuf(byteBuf)
        staticExtInfo = ByteArray(CACHED_EXT_INFO_BUFFER_SIZE)
        if (startInHighRes) {
            for (i in info.indices) {
                for (j in info.indices) {
                    info.clients[i].isHighResolution[j] = true
                }
            }
        }
        meta = Array(info.capacity) {
            PlayerInfoMetadata()
        }
    }

    @Benchmark
    fun registerAndUpdateMaxPlayersNoExtInfo(bh: Blackhole) {
        for (i in info.indices) {
            info.register(i)
        }
        for (i in info.indices) {
            bh.consume(info.put(buf, i))
        }
        for (i in info.indices) {
            info.unregister(i)
        }
    }

    @Benchmark
    fun registerAndUpdateMaxPlayersWithMaxByteStaticExtInfo(bh: Blackhole) {
        for (i in info.indices) {
            info.register(i)
            info.cacheStaticExtendedInfo(i, staticExtInfo)
        }
        for (i in info.indices) {
            bh.consume(info.put(buf, i))
        }
        for (i in info.indices) {
            info.unregister(i)
        }
    }

    lateinit var meta: Array<PlayerInfoMetadata>

    @Benchmark
    fun registerAndUpdateMaxHighResPlayersWithMovement(bh: Blackhole) {
        for (i in info.indices) {
            info.register(i)
            val coords = HighResCoord(3200 + random.nextInt(14), 3200 + random.nextInt(14))
            info.updateCoords(i, coords, coords)
        }
        for (i in info.indices) {
            bh.consume(info.put(byteBuf, bitBuf, i, meta[i]))
        }
        for (i in info.indices) {
            info.unregister(i)
        }
    }
}

fun main() {
    val bench = SingleThreadBufLimited()
    bench.setup()
    val blackhole =
        Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")
    while (true) {
        val elapsed = measureNanoTime {
            bench.registerAndUpdateMaxHighResPlayersWithMovement(blackhole)
        }
        println("Total took ${elapsed.toDouble() / 1_000_000}ms")
    }
}
