@file:Suppress("UNUSED")

package org.rsmod.plugins.info.player

import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.UnpooledUnsafeDirectByteBuf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import org.rsmod.plugins.info.model.coord.HighResCoord
import org.rsmod.plugins.info.player.PlayerInfo.Companion.CACHED_EXT_INFO_BUFFER_SIZE
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2)
@Measurement(iterations = 1, time = 5)
@Fork(value = 1, warmups = 2)
abstract class MultiThreadPlayerInfoBenchmark(
    private val bufCapacity: Int,
    private val startInHighRes: Boolean = false
) {

    private lateinit var info: PlayerInfo
    private lateinit var bufs: Array<FastBitBuf>
    private lateinit var staticExtInfo: ByteArray
    private lateinit var scope: CoroutineScope

    private val random: ThreadLocalRandom get() = ThreadLocalRandom.current()

    @Setup
    fun setup() {
        Jvm.init()
        System.setProperty("io.netty.buffer.checkAccessible", "false")
        System.setProperty("io.netty.buffer.checkBounds", "false")

        info = PlayerInfo()
        bufs = Array(info.capacity) {
            FastBitBuf(
                UnpooledUnsafeDirectByteBuf(
                    ByteBufAllocator.DEFAULT,
                    bufCapacity,
                    bufCapacity
                )
            )
        }
        staticExtInfo = ByteArray(CACHED_EXT_INFO_BUFFER_SIZE)
        if (startInHighRes) {
            for (i in info.indices) {
                for (j in info.indices) {
                    info.clients[i].isHighResolution[j] = true
                }
            }
        }
        val executor = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors())
        scope = CoroutineScope(executor.asCoroutineDispatcher())
    }

    @Benchmark
    fun registerAndUpdateMaxPlayersNoExtInfo(bh: Blackhole) = runBlocking {
        for (i in info.indices) {
            info.register(i)
        }
        launch(scope.coroutineContext) {
            for (i in info.indices) {
                launch {
                    val buf = bufs[i]
                    bh.consume(info.put(buf.buf, buf, i))
                }
            }
        }.join()
        for (i in info.indices) {
            info.unregister(i)
        }
    }

    @Benchmark
    fun registerAndUpdateMaxPlayersWithMaxByteStaticExtInfo(bh: Blackhole) = runBlocking {
        for (i in info.indices) {
            info.register(i)
            info.cacheStaticExtendedInfo(i, staticExtInfo)
        }
        launch(scope.coroutineContext) {
            for (i in info.indices) {
                launch {
                    val buf = bufs[i]
                    bh.consume(info.put(buf.buf, buf, i))
                }
            }
        }.join()
        for (i in info.indices) {
            info.unregister(i)
        }
    }

    val buf: ByteBuffer = ByteBuffer.allocateDirect(bufCapacity)

    @Benchmark
    fun registerAndUpdateMaxHighResPlayersWithMovement(bh: Blackhole) = runBlocking {
        for (i in info.indices) {
            info.register(i)
            val coords = HighResCoord(random.nextInt(3200, 3213), random.nextInt(3200, 3213))
            info.updateCoords(i, coords, coords)
        }
        launch(scope.coroutineContext) {
            for (i in info.indices) {
                launch {
                    val buf = bufs[i]
                    bh.consume(info.put(buf.buf, buf, i))
                }
            }
        }.join()
        for (i in info.indices) {
            info.unregister(i)
        }
    }
}

fun main() {
    val bench = MultiThreadBufLimited()
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
