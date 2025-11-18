import org.apache.logging.log4j.LogManager
import proxy.Socks5Proxy
import kotlin.concurrent.thread
import kotlin.ranges.contains

private fun isValidPort(port: Int?): Boolean{
    return port in 1..65534
}
@Volatile
var running = true

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
        logger.info("Starting proxy...")

        val proxy = Socks5Proxy(port)
        thread(start = true, isDaemon = true) { proxy.serve() }

        thread {
            while (running) {
                Thread.sleep(1000)
                val statistic = proxy.stats()
                if(statistic != null){
                    println(statistic)
                }
            }
        }


        print("Press Enter to exit...")
        readLine()
        running = false
        try {
            proxy.close()
        } catch (e: Exception) {
            error("main: error proxy's close: ${e.message}")
        }

        Thread.sleep(15)

        println(Thread.activeCount())
    }catch (e: Exception) {
        logger.fatal("Proxy error: ${e.message}")
    }
}