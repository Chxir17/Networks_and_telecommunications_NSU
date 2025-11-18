import org.apache.logging.log4j.LogManager
import proxy.Socks5Proxy
import kotlin.concurrent.thread
import kotlin.ranges.contains

private fun isValidPort(port: Int?): Boolean{
    return port in 1..65534
}

fun main(args: Array<String>) {
    val logger = LogManager.getLogger("ProxyMain")
    try {
        if (args.size != 1) {
            logger.fatal("Invalid arguments size. Expected 1 argument(port), got ${args.size}")
            throw IllegalArgumentException("Expected exactly 1 argument")
        }
        val port = args[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid port ${args[0]}");
        if (port !in 1..65534){
            logger.fatal("Invalid port $port")
            throw IllegalArgumentException("Invalid port $port")
        }
        logger.info("Starting proxy at port $port...")


        val proxy = Socks5Proxy()

        try {
            proxy.open(port)
        } catch (e: Exception) {
            error("main: error opening proxy on port $port: ${e.message}")
        }

        thread(start = true, isDaemon = true) {
            try {
                proxy.serve()
            } catch (e: Exception) {
                error("main: error proxy's serve: ${e.message}")
            }
        }

        thread {
            while (true) {
                Thread.sleep(50000)
                println(proxy.stats())
            }
        }


        print("Press Enter to exit...")
        readLine()

        try {
            proxy.close()
        } catch (e: Exception) {
            error("main: error proxy's close: ${e.message}")
        }

        Thread.sleep(15_000)

        println(Thread.activeCount())
    }catch (e: Exception) {
        logger.fatal("Proxy error: ${e.message}")
    }
}