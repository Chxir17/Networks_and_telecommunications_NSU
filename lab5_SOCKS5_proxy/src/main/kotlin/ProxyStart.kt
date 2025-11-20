import org.apache.logging.log4j.LogManager
import proxy.Socks5Proxy
import kotlin.concurrent.thread


fun isValidPort(port: Int): Boolean {
    return port in 1..65534
}

fun getPort(args: Array<String>): Int {
    if (args.size != 1) {
        throw IllegalArgumentException("Expected exactly 1 argument <port>")
    }
    val port: Int = args[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid port ${args[0]}")
    if (!isValidPort(port)) {
        throw IllegalArgumentException("Invalid port number $port")
    }
    return port
}

fun main(args: Array<String>) {
    val logger = LogManager.getLogger("ProxyMain")
    try {
        val port = getPort(args)
        logger.info("Starting proxy...")
        val proxy = Socks5Proxy(port)

        val shutdownHook = Thread {
            logger.info("Disabling proxy...")
            proxy.running = false
            proxy.close()
        }

        Runtime.getRuntime().addShutdownHook(shutdownHook)

        proxy.running = true
        thread { proxy.run() }
        thread { proxy.runStats() }

        println("Press Enter to exit...")
        readLine()
        proxy.running = false
        logger.info("Disabling proxy...")
        proxy.close()

        Runtime.getRuntime().removeShutdownHook(shutdownHook)

    } catch (e: Exception) {
        logger.fatal("Proxy error: ${e.message}")
    }
}