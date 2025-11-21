package proxy

import proxy.connectionHandler.SingleConnection
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
    internal val statsClosers = ConcurrentHashMap<SingleConnection, Boolean>()

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

    private fun handleClient(clientSocket: Socket) {
        val client = SingleConnection(clientSocket)
        statsClosers[client] = true
        try {
            client.run()
        } catch (e: IOException) {
            logger.error("Error serving connection: ${e.message}")
        } finally {
            statsClosers.remove(client)
        }
    }

    override fun run() {
        logger.info("Proxy thread started")
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
