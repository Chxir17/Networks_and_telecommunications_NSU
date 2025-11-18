import org.apache.logging.log4j.LogManager
import kotlin.ranges.contains

private fun isValidPort(port: Int?): Boolean{
    return port in 1..65534
}

fun main(args: Array<String>) {
    val logger = LogManager.getLogger("ProxyMain")
    try {
        if (args.size != 1) {
            logger.fatal("Invalid arguments size. Expected 1 argument(port), got ${args.size}")
            throw IllegalArgumentException("Expected exactly one argument")
        }
        val port = args[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid port ${args[0]}");
        if (port !in 1..65534){
            logger.fatal("Invalid port $port")
            throw IllegalArgumentException("Invalid port $port")
        }
        logger.info("Starting proxy at port $port...")






    }catch (e: Exception) {
        logger.fatal("Proxy error: ${e.message}")
    }
}