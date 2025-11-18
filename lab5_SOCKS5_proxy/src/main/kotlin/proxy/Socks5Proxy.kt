package proxy

import connection.SingleConnectionServer
import entities.Statistic
import statistic.IStatisticCloser
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class Socks5Proxy {
    private var listenAddr: InetSocketAddress? = null
    private var serverSocket: ServerSocket? = null
    private val statsClosers = ConcurrentHashMap<IStatisticCloser, Boolean>()

    fun open(port: Int) {
        listenAddr = InetSocketAddress(port)
        serverSocket = ServerSocket()
        serverSocket!!.reuseAddress = true
        serverSocket!!.bind(listenAddr)
    }

    fun serve() {
        val srv = serverSocket ?: throw IOException("server not open")
        try {
            while (true) {
                val clientSocket = try {
                    srv.accept()
                } catch (e: SocketException) {
                    if (srv.isClosed) return else throw e
                }
                thread {
                    val server = SingleConnectionServer(clientSocket)
                    statsClosers[server] = true
                    val err = try {
                        server.serve()
                        null
                    } catch (ex: Exception) {
                        ex
                    }
                    statsClosers.remove(server)
                    err?.let { println("serving coroutine: ${it.message}") }
                }
            }
        } finally {
            srv.close()
        }
    }

    fun stats(): List<Pair<Statistic, Statistic>> {
        val res = ArrayList<Pair<Statistic, Statistic>>()
        for (k in statsClosers.keys) {
            try {
                val (c, r) = k.stats()
                res.add(Pair(c, r))
            } catch (_: Exception) { }
        }
        return res
    }

    fun close() {
        serverSocket?.close()
        for (k in statsClosers.keys) {
            try { k.close() } catch (_: Exception) { }
        }
        statsClosers.clear()
    }
}
