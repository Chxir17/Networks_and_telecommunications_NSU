package connection

import entities.ClientMessage
import entities.GreetingResponse
import entities.ServerResponse
import entities.Statistic
import enums.AddressType
import enums.MessageCode
import enums.ResponseCode
import enums.SocksVersion
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.ThreadContext
import proxy.ByteSliceMessageSource
import statistic.ConnectionStatistics
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.ProtocolException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.concurrent.thread

class SingleConnectionServer(private val tcpClientSocket: Socket) : Runnable {

    companion object {
        private val logger = LogManager.getLogger(SingleConnectionServer::class.java)
    }

    private val messageSource: ByteSliceMessageSource
    private val timeoutMs = 10_000
    private var clientStats: ConnectionStatistics? = null
    private var remoteStats: ConnectionStatistics? = null

    init {
        tcpClientSocket.soTimeout = 0
        messageSource = ByteSliceMessageSource(tcpClientSocket.getInputStream(), tcpClientSocket.getOutputStream())
    }

    override fun run() {
        ThreadContext.put("clientIp", tcpClientSocket.remoteSocketAddress.toString())
        logger.info("Starting to serve client")
        try {
            tcpClientSocket.use { client ->
                val greeting = messageSource.readGreetingMessage()
                val greetingResponse = GreetingResponse(SocksVersion.SOCKS5, 0)
                messageSource.writeGreetingResponse(greetingResponse)
                logger.info("Sent greeting response to client")

                val clientMessage = try {
                    messageSource.readClientMessage()
                } catch (e: Exception) {
                    logger.error("Error reading client message - ${e.message}")
                    handleMessageSourceError(e)
                    return
                }

                logger.info("Received client request ${clientMessage.messageCode}")

                when (clientMessage.messageCode) {
                    MessageCode.ESTABLISH_TCP -> {
                        logger.info("Establishing TCP connection to target")
                        establishTCP(clientMessage)
                    }

                    MessageCode.BIND_PORT, MessageCode.ASSOCIATE_UDP_PORT -> {
                        logger.warn("Unsupported command ${clientMessage.messageCode}")
                        sendUnsupported(clientMessage)
                    }

                    else -> {
                        logger.warn("Unknown command ${clientMessage.messageCode}")
                        sendUnsupported(clientMessage)
                    }
                }
            }
            logger.info("Finished serving client")
        } catch (e: Exception) {
            logger.error("Exception in client handling: ${e.message}")
        } finally {
            ThreadContext.clearAll()
        }
    }

    private fun establishTCP(clientMessage: ClientMessage) {
        val targetHost = when (clientMessage.addressType) {
            AddressType.DOMAIN_NAME -> String(clientMessage.addressPayload)
            AddressType.IPV4, AddressType.IPV6 -> clientMessage.addressPayload.joinToString(".") { (it.toInt() and 0xff).toString() }
            else -> throw IOException("Unsupported address type")
        }
        logger.info("Connecting to target $targetHost:${clientMessage.port}")

        val remoteSocket: Socket
        try {
            val addr = InetSocketAddress(targetHost, clientMessage.port)
            val s = Socket()
            s.connect(addr, timeoutMs)
            remoteSocket = s
            logger.info("Connected to remote $targetHost:${clientMessage.port}")
        } catch (e: IOException) {
            logger.error("Failed to connect to remote $targetHost:${clientMessage.port} - ${e.message}")
            handleDialTimeoutError(e, clientMessage)
            return
        }

        remoteSocket.use { remote ->
            val (addrType, serverIP, port) = tcpLocalAddrInfo(remote)
            sendRequestGranted(serverIP, addrType, port)
            logger.info("Request granted, starting data transmission")
            startTransmitting(remote)
        }
    }

    private fun startTransmitting(remote: Socket) {
        logger.info("Starting data transmission client <-> remote")

        val clientIn = tcpClientSocket.getInputStream()
        val clientOut = tcpClientSocket.getOutputStream()
        val remoteIn = remote.getInputStream()
        val remoteOut = remote.getOutputStream()

        remoteStats = ConnectionStatistics(remote.inetAddress, remote.port)
        clientStats = ConnectionStatistics(tcpClientSocket.inetAddress, tcpClientSocket.port)

        val waiter = java.util.concurrent.CountDownLatch(1)

        // remote -> client
        thread {
            try {
                val buf = ByteArray(1400)
                while (true) {
                    val read = remoteIn.read(buf)
                    if (read == -1) break
                    remoteStats!!.addReadBytes(read.toLong())
                    clientOut.write(buf, 0, read)
                    clientStats!!.addWroteBytes(read.toLong())
                }
            } catch (e: Exception) {
                logger.error("Error transmitting data from remote to client - ${e.message}", e)
            } finally {
                waiter.countDown()
            }
        }

        // client -> remote (current thread)
        try {
            val buf = ByteArray(1400)
            while (true) {
                val read = clientIn.read(buf)
                if (read == -1) break
                clientStats!!.addReadBytes(read.toLong())
                remoteOut.write(buf, 0, read)
                remoteStats!!.addWroteBytes(read.toLong())
            }
        } catch (e: Exception) {
            logger.error("Error transmitting data from client to remote - ${e.message}", e)
        }

        waiter.await()
        logger.info("Data transmission finished")
    }

    private fun handleMessageSourceError(err: Exception) {
        logger.error("Message source error - ${err.message}")
        if (err is ProtocolException) {
            try {
                sendProtocolError()
            } catch (e: Exception) {
                logger.error("Failed to send protocol error response - ${e.message}", e)
            }
        }
    }

    private fun handleDialTimeoutError(err: IOException, clientMessage: ClientMessage) {
        logger.error("Dial error: ${err.message}", err)
        try {
            when (err) {
                is ConnectException -> sendConnectionRefused(clientMessage)
                is UnknownHostException -> sendHostUnreachable(clientMessage)
                is SocketTimeoutException -> sendHostUnreachable(clientMessage)
                else -> sendGeneralFailure(clientMessage.addressPayload, clientMessage.addressType, clientMessage.port)
            }
        } catch (e: Exception) {
            logger.error("Failed to send error response - ${e.message}", e)
        }
    }

    private fun sendServerResponse(answer: ServerResponse) {
        try {
            messageSource.writeServerAnswer(answer)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun sendRequestGranted(serverIP: ByteArray, addrType: AddressType, port: Int) {
        sendServerResponse(ServerResponse(SocksVersion.SOCKS5, ResponseCode.REQUEST_GRANTED, addrType, serverIP, port))
    }

    private fun sendGeneralFailure(serverIP: ByteArray, addrType: AddressType, port: Int) {
        sendServerResponse(ServerResponse(SocksVersion.SOCKS5, ResponseCode.GENERAL_FAILURE, addrType, serverIP, port))
    }

    private fun sendHostUnreachable(clientMessage: ClientMessage) {
        sendServerResponse(
            ServerResponse(
                SocksVersion.SOCKS5,
                ResponseCode.HOST_UNREACHABLE,
                clientMessage.addressType,
                clientMessage.addressPayload,
                clientMessage.port
            )
        )
    }

    private fun sendConnectionRefused(clientMessage: ClientMessage) {
        sendServerResponse(
            ServerResponse(
                SocksVersion.SOCKS5,
                ResponseCode.CONNECTION_REFUSED_BY_DEST_HOST,
                clientMessage.addressType,
                clientMessage.addressPayload,
                clientMessage.port
            )
        )
    }

    private fun sendUnsupported(clientMessage: ClientMessage) {
        sendServerResponse(
            ServerResponse(
                SocksVersion.SOCKS5,
                ResponseCode.COMMAND_NOT_SUPPORTED,
                clientMessage.addressType,
                clientMessage.addressPayload,
                clientMessage.port
            )
        )
    }

    private fun sendProtocolError() {
        val (addrType, ip, port) = tcpLocalAddrInfo(tcpClientSocket)
        sendServerResponse(ServerResponse(SocksVersion.SOCKS5, ResponseCode.PROTOCOL_ERROR, addrType, ip, port))
    }

    private fun tcpLocalAddrInfo(socket: Socket): Triple<AddressType, ByteArray, Int> {
        val local = socket.localSocketAddress as InetSocketAddress
        val ip = local.address
        return if (ip.address.size == 16) {
            Triple(AddressType.IPV6, ip.address, local.port)
        } else {
            Triple(AddressType.IPV4, ip.address, local.port)
        }
    }

    fun stats(): Pair<Statistic, Statistic> {
        val c = clientStats ?: throw Exception("stats: no stats available")
        val r = remoteStats ?: throw Exception("stats: no stats available")
        return Pair(c.stats(), r.stats())
    }

    fun close() {
        try {
            tcpClientSocket.close()
        } catch (e: Exception) {
            logger.fatal("CAN'T CLOSE CLIENT CONNECTION $tcpClientSocket")
            throw e
        }
    }
}
