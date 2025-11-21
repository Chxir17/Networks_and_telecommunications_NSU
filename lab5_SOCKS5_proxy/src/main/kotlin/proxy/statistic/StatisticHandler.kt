package proxy.statistic

import entities.Statistic
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import proxy.Socks5Proxy

class StatisticHandler(
    private val proxy: Socks5Proxy
) : Runnable {
    private val logger: Logger = LogManager.getLogger("StatisticHandler")

    private fun collect(): List<Pair<Statistic, Statistic>>? {
        return try {
            val result = proxy.statsClosers.keys.mapNotNull { srv ->
                try {
                    val (c, r) = srv.stats()
                    Pair(c, r)
                } catch (_: Exception) {
                    null
                }
            }
            result.ifEmpty { null }
        } catch (e: Exception) {
            logger.error("Statistic error: ${e.message}")
            null
        }
    }

    override fun run() {
        logger.info("Statistic thread started")
        while (proxy.running) {
            Thread.sleep(1000)
            collect()?.let {
                logger.info(it)
            }
        }
    }
}
