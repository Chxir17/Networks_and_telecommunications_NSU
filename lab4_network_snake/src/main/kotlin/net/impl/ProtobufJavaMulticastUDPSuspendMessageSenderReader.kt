package onlinesnake.net.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import onlinesnake.net.SerializedMessage
import onlinesnake.net.SuspendMessageReader
import onlinesnake.net.SuspendMessageSender
import onlinesnake.net.dto.common.SourceHost
import java.io.IOException
import java.net.*

class ProtobufJavaMulticastUDPSuspendMessageSenderReader(
    ip: String,
    port: Int,
) : SuspendMessageSender, SuspendMessageReader {
    private companion object {
        const val BYTES_SIZE = 10_000
    }
    private val multicastSocket = MulticastSocket(port)
    private val addr = InetAddress.getByName(ip)
    private val group = InetSocketAddress(addr, port)

    private val niface: NetworkInterface

    init {
        val allNifaces = NetworkInterface.getNetworkInterfaces()!!
        niface = allNifaces.toList().find { networkInterface ->
            if (networkInterface.isLoopback) {
                return@find false
            }

            if (!networkInterface.isUp) {
                return@find false
            }

            if (!networkInterface.supportsMulticast()) {
                return@find false
            }

            if (!networkInterface.inetAddresses.asSequence().toList().any { it is Inet4Address }) {
                return@find false
            }

            return@find true
        } ?: throw IllegalStateException("no suitable interfaces found")

        println("Selected interface ${niface.name}")
    }

    init {
        multicastSocket.joinGroup(group, niface)
    }

    private val bytes = ByteArray(BYTES_SIZE)
    private val datagramPacket = DatagramPacket(bytes, BYTES_SIZE)

    @Throws(IOException::class)
    override suspend fun read(): SerializedMessage {
        withContext(Dispatchers.IO) {
            multicastSocket.receive(datagramPacket)
        }
        return SerializedMessage(
            bytes = datagramPacket.data.copyOf(datagramPacket.length),
            sourceHost = SourceHost(
                ip = datagramPacket.address.hostAddress,
                port = datagramPacket.port
            )
        )
    }

    @Throws(IOException::class)
    override suspend fun send(bytes: ByteArray, targetSourceHost: SourceHost) {
        val sendDatagramPacket = DatagramPacket(
            bytes,
            bytes.size,
            InetSocketAddress(targetSourceHost.ip, targetSourceHost.port)
        )
        withContext(Dispatchers.IO) {
            multicastSocket.send(sendDatagramPacket)
        }
    }
}