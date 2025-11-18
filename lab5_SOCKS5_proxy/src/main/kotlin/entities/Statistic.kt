package entities

import java.net.InetAddress
import java.time.Instant

data class Statistic(
    val readBytes: Long,
    val wroteBytes: Long,
    val readSinceLastStats: Long,
    val wroteSinceLastStats: Long,
    val startTime: Instant,
    val lastStatsTime: Instant,
    val ip: InetAddress,
    val port: Int
)
