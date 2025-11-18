package proxy

import connection.SingleConnectionServer
import entities.Statistic
import org.apache.logging.log4j.LogManager
import running
import statistic.IStatisticCloser
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class Socks5Proxy {
    private val logger = LogManager.getLogger("ProxySocks5Proxy")
    private var listenAddr: InetSocketAddress? = null
    private var serverSocket: ServerSocket? = null
    private val statsClosers = ConcurrentHashMap<IStatisticCloser, Boolean>()

    constructor(port: Int) {
        logger.info("Setting up proxy...")
        try {
            open(port)
        }catch (e: Exception){
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
        }catch (e: Exception) {
            logger.fatal("Proxy setting error: ${e.message}")
            throw e
        }
    }

    fun serve() {
        val srv = serverSocket ?: throw IOException("server not open")
        try {
            while (running) {
                val clientSocket = try {
                    srv.accept()
                } catch (e: IOException) {
                    logger.error("Error accepting connection: ${e.message}")
                    continue
                }

                thread {
                    val server = SingleConnectionServer(clientSocket)
                    statsClosers[server] = true
                    try {
                        server.serve()
                    } catch (e: IOException) {
                        logger.error("Error serving connection: ${e.message}", e)
                    } finally {
                        statsClosers.remove(server)
                    }
                }
            }
        } catch (e: Exception){
            logger.error("Working error: ${e.message}")
            throw e
        } finally {
            srv.close()
        }
    }

    fun stats(): List<Pair<Statistic, Statistic>>? {
        try {
            val res = ArrayList<Pair<Statistic, Statistic>>()
            for (k in statsClosers.keys) {
                val (c, r) = k.stats()
                res.add(Pair(c, r))
            }
            return res.ifEmpty { null }
        } catch (_: Exception) {
            logger.error("Statistic error")
            return null
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
