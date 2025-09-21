package org.server.serverWork
import org.server.config.ServerConfig
import org.server.handler.Handler
import java.net.ServerSocket
import java.nio.file.Paths
import java.util.concurrent.Executors

class ServerWork (
    private val config: ServerConfig
) {
    private val pool = Executors.newCachedThreadPool()

    fun start() {
        val serverSocket = ServerSocket(config.port)
        println("Server starts at $config.port, files located at: ${Paths.get("uploads").toAbsolutePath()}")
        while (true) {
            val client = serverSocket.accept()
            println("New client: ${client.remoteSocketAddress}")
            pool.submit(Handler(client, config.updateTime))
        }
    }
}