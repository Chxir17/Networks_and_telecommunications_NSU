package proxy

import connection.SingleConnectionServer
import entities.Statistic
import org.apache.logging.log4j.LogManager
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class Socks5Proxy : Runnable {

    @Volatile
    var running: Boolean = false

    private val logger = LogManager.getLogger("ProxySocks5Proxy")
    private var listenAddr: InetSocketAddress? = null
    private var serverSocket: ServerSocket? = null
    private val statsClosers = ConcurrentHashMap<SingleConnectionServer, Boolean>()

    constructor(port: Int) {
        logger.info("Setting up proxy...")
        try {
            open(port)
        } catch (e: Exception) {
            throw e
        }
        logger.info("Proxy address: $listenAddr")
    }

    private fun open(port: Int) {
        try {
            listenAddr = InetSocketAddress(port)
            serverSocket = ServerSocket()
            serverSocket!!.reuseAddress = true
            serverSocket!!.bind(listenAddr)
        } catch (e: Exception) {
            logger.fatal("Proxy setting error: ${e.message}")
            throw e
        }
    }

    private fun stats(): List<Pair<Statistic, Statistic>>? {
        try {
            val res = ArrayList<Pair<Statistic, Statistic>>()
            for (k in statsClosers.keys) {
                val (c, r) = k.stats()
                res.add(Pair(c, r))
            }
            return res.ifEmpty { null }
        } catch (e: Exception) {
            logger.error("Statistic error ${e.message}")
            return null
        }
    }

    private fun handleClient(clientSocket: Socket) {
        val client = SingleConnectionServer(clientSocket)
        statsClosers[client] = true
        try {
            client.run()
        } catch (e: IOException) {
            logger.error("Error serving connection: ${e.message}", e)
        } finally {
            statsClosers.remove(client)
        }
    }

    override fun run() {
        logger.info("Starting proxy thread")
        val server = serverSocket ?: throw IOException("Server not opened")
        try {
            while (running) {
                val clientSocket = try {
                    server.accept()
                } catch (e: IOException) {
                    if (running) {
                        logger.error("Error accepting connection: ${e.message}")
                    }
                    continue
                }
                thread { handleClient(clientSocket) }
            }
        } catch (e: Exception) {
            logger.error("Working error: ${e.message}")
            throw e
        } finally {
            server.close()
        }
    }

    fun runStats() {
        logger.info("Starting statistic thread")
        try {
            while (running) {
                Thread.sleep(1000)
                val statistic = stats()
                if (statistic != null) {
                    logger.info(statistic)
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }

    fun close() {
        serverSocket?.close()
        for (k in statsClosers.keys) {
            try {
                k.close()
            } catch (e: Exception) {
                logger.fatal("CAN'T CLOSE SOCKET ${e.message}")
                continue
            }
        }
        statsClosers.clear()
    }
}
