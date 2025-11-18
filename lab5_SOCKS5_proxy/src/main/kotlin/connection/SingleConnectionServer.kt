package connection

import entities.ClientMessage
import entities.GreetingResponse
import entities.ServerResponse
import entities.Statistic
import enums.AddressType
import enums.MessageCode
import enums.ResponseCode
import enums.SocksVersion
import proxy.ByteSliceMessageSource
import statistic.ConnectionStatistics
import statistic.IStatisticCloser
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.ProtocolException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class SingleConnectionServer(private val tcpClientSocket: Socket) : IStatisticCloser {
    private val messageSource: ByteSliceMessageSource
    private val timeoutMs = 10_000
    private var clientStats: ConnectionStatistics? = null
    private var remoteStats: ConnectionStatistics? = null

    init {
        tcpClientSocket.soTimeout = 0 // blocking reads; we handle timeouts on connect only
        messageSource = ByteSliceMessageSource(tcpClientSocket.getInputStream(), tcpClientSocket.getOutputStream())
    }

    @Throws(IOException::class)
    fun serve() {
        tcpClientSocket.use { client ->
            // Greeting
            val greeting = try {
                messageSource.readGreetingMessage()
            } catch (e: Exception) {
                handleMessageSourceError(e)
                return
            }
            val greetingResponse = GreetingResponse(SocksVersion.SOCKS5, 0)
            messageSource.writeGreetingAnswer(greetingResponse)

            val clientMessage = try {
                messageSource.readClientMessage()
            } catch (e: Exception) {
                handleMessageSourceError(e)
                return
            }

            when (clientMessage.messageCode) {
                MessageCode.ESTABLISH_TCP -> {
                    establishTCP(clientMessage)
                }
                MessageCode.BIND_PORT, MessageCode.ASSOCIATE_UDP_PORT -> {
                    sendUnsupported(clientMessage)
                }
                else -> sendUnsupported(clientMessage)
            }
        }
    }

    private fun handleMessageSourceError(err: Exception) {
        if (err is ProtocolException) {
            try {
                sendProtocolError()
            } catch (e: Exception) {
                // log and ignore
                println("handle message source error: on ${err.message} - ${e.message}")
            }
        }
    }

    @Throws(IOException::class)
    private fun establishTCP(clientMessage: ClientMessage) {
        val targetHost = when (clientMessage.addressType) {
            AddressType.DOMAIN_NAME -> String(clientMessage.addressPayload)
            AddressType.IPV4, AddressType.IPV6 -> clientMessage.addressPayload.joinToString(".") { (it.toInt() and 0xff).toString() }
            else -> throw IOException("unsupported address type")
        }
        val remoteSocket: Socket
        try {
            val addr = InetSocketAddress(targetHost, clientMessage.port)
            val s = Socket()
            s.connect(addr, timeoutMs)
            remoteSocket = s
        } catch (e: IOException) {
            handleDialTimeoutError(e, clientMessage)
            return
        }

        remoteSocket.use { remote ->
            val (addrType, serverIP, port) = tcpLocalAddrInfo(remote)
            sendRequestGranted(serverIP, addrType, port)
            startTransmitting(remote)
        }
    }

    // heuristics: map IOException to proper reply. In Go code they matched syscall codes;
    // here we inspect exception type/message minimally.
    private fun handleDialTimeoutError(err: IOException, clientMessage: ClientMessage) {
        try {
            when (err) {
                is ConnectException -> {
                    sendConnectionRefused(clientMessage)
                }
                is UnknownHostException -> {
                    sendHostUnreachable(clientMessage)
                }
                is SocketTimeoutException -> {
                    sendHostUnreachable(clientMessage)
                }
                else -> {
                    sendGeneralFailure(clientMessage.addressPayload, clientMessage.addressType, clientMessage.port)
                }
            }
        } catch (e: Exception) {
            // ignore
            println("handle dial timeout error: on ${err.message} - ${e.message}")
        }
    }

    @Throws(IOException::class)
    private fun startTransmitting(remote: Socket) {
        val clientIn = tcpClientSocket.getInputStream()
        val clientOut = tcpClientSocket.getOutputStream()
        val remoteIn = remote.getInputStream()
        val remoteOut = remote.getOutputStream()

        remoteStats = ConnectionStatistics(remote.inetAddress, remote.port)
        clientStats = ConnectionStatistics(tcpClientSocket.inetAddress, tcpClientSocket.port)

        val clientToRemoteStopped = AtomicLong(0)
        val remoteToClientStopped = AtomicLong(0)
        val waiter = java.util.concurrent.CountDownLatch(1)

        // remote -> client
        thread {
            try {
                val buf = ByteArray(1400)
                while (true) {
                    val read = remoteIn.read(buf)
                    if (read == -1) break
                    remoteStats!!.addReadBytes(read.toLong())
                    var wrote = 0
                    while (wrote < read) {
                        clientOut.write(buf, wrote, read - wrote)
                        wrote = read // write is blocking; assume success or exception
                        clientStats!!.addWroteBytes((read - wrote).toLong()) // careful: here read-wrote becomes 0; better to add actual written
                        // to mimic Go: calculate per write; but Java write blocks until done, so add read
                        clientStats!!.addWroteBytes(read.toLong())
                    }
                }
            } catch (e: Exception) {
                try { tcpClientSocket.close() } catch (_: Exception) {}
                // logging
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
                var wrote = 0
                while (wrote < read) {
                    remoteOut.write(buf, wrote, read - wrote)
                    wrote = read
                    remoteStats!!.addWroteBytes(read.toLong())
                }
            }
        } catch (e: Exception) {
            try { remote.close() } catch (_: Exception) {}
        }

        // wait remote->client thread
        waiter.await()

        // no error propagation details here — keep simple
    }

    // send helpers
    private fun sendServerAnswer(answer: ServerResponse) {
        try {
            messageSource.writeServerAnswer(answer)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun sendRequestGranted(serverIP: ByteArray, addrType: AddressType, port: Int) {
        sendServerAnswer(ServerResponse(SocksVersion.SOCKS5, ResponseCode.REQUEST_GRANTED, addrType, serverIP, port))
    }

    private fun sendGeneralFailure(serverIP: ByteArray, addrType: AddressType, port: Int) {
        sendServerAnswer(ServerResponse(SocksVersion.SOCKS5, ResponseCode.GENERAL_FAILURE, addrType, serverIP, port))
    }

    private fun sendConnNotAllowed(clientMessage: ClientMessage) {
        sendServerAnswer(ServerResponse(
                SocksVersion.SOCKS5, ResponseCode.NOT_ALLOWED_BY_RULESET,
                clientMessage.addressType, clientMessage.addressPayload, clientMessage.port
            )
        )
    }

    private fun sendNetworkUnreachable(clientMessage: ClientMessage) {
        sendServerAnswer(ServerResponse(
                SocksVersion.SOCKS5, ResponseCode.NETWORK_UNREACHABLE,
                clientMessage.addressType, clientMessage.addressPayload, clientMessage.port
            )
        )
    }

    private fun sendHostUnreachable(clientMessage: ClientMessage) {
        sendServerAnswer(ServerResponse(
                SocksVersion.SOCKS5, ResponseCode.HOST_UNREACHABLE,
                clientMessage.addressType, clientMessage.addressPayload, clientMessage.port
            )
        )
    }

    private fun sendConnectionRefused(clientMessage: ClientMessage) {
        sendServerAnswer(
            ServerResponse(
                SocksVersion.SOCKS5, ResponseCode.CONNECTION_REFUSED_BY_DEST_HOST,
                clientMessage.addressType, clientMessage.addressPayload, clientMessage.port
            )
        )
    }

    private fun sendUnsupported(clientMessage: ClientMessage) {
        sendServerAnswer(
            ServerResponse(
                SocksVersion.SOCKS5, ResponseCode.COMMAND_NOT_SUPPORTED,
                clientMessage.addressType, clientMessage.addressPayload, clientMessage.port
            )
        )
    }

    private fun sendProtocolError() {
        val (addrType, ip, port) = tcpLocalAddrInfo(tcpClientSocket)
        sendServerAnswer(ServerResponse(SocksVersion.SOCKS5, ResponseCode.PROTOCOL_ERROR, addrType, ip, port))
    }

    // helper: return address type, ip bytes, port
    private fun tcpLocalAddrInfo(socket: Socket): Triple<AddressType, ByteArray, Int> {
        val local = socket.localSocketAddress as InetSocketAddress
        val ip = local.address
        return if (ip.address.size == 16) {
            Triple(AddressType.IPV6, ip.address, local.port)
        } else {
            Triple(AddressType.IPV4, ip.address, local.port)
        }
    }

    // StatsCloser interface
    override fun stats(): Pair<Statistic, Statistic> {
        val c = clientStats ?: throw Exception("stats: no stats available")
        val r = remoteStats ?: throw Exception("stats: no stats available")
        return Pair(c.stats(), r.stats())
    }

    override fun close() {
        try { tcpClientSocket.close() } catch (_: Exception) {}
    }
}