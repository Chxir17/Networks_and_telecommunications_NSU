package statistic

import entities.Statistic
import java.net.InetAddress
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

class ConnectionStatistics(private val ip: InetAddress, private val port: Int) {

    private var readBytes: Long = 0
    private var wroteBytes: Long = 0
    private val readSinceLastStats = AtomicLong(0)
    private val wroteSinceLastStats = AtomicLong(0)
    private val startTime: Instant = Instant.now()
    private var lastStatsTime: Instant = Instant.now()

    fun stats(): Statistic {
        val res = Statistic(
            readBytes,
            wroteBytes,
            readSinceLastStats.getAndSet(0),
            wroteSinceLastStats.getAndSet(0),
            startTime,
            lastStatsTime,
            ip,
            port
        )
        lastStatsTime = Instant.now()
        return res
    }

    fun addReadBytes(delta: Long) {
        readBytes += delta
        readSinceLastStats.addAndGet(delta)
    }

    fun addWroteBytes(delta: Long) {
        wroteBytes += delta
        wroteSinceLastStats.addAndGet(delta)
    }
}
