package onlinesnake.net.impl

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import onlinesnake.net.*
import onlinesnake.net.dto.common.SourceHost

class ProtobufKtorUDPSuspendMessageSenderReader : SuspendMessageSender, SuspendMessageReader {
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val socket = aSocket(selectorManager).udp().bind()
    override suspend fun read(): SerializedMessage {
        val datagram = socket.receive()
        return SerializedMessage(
            sourceHost = SourceHost(
                ip = datagram.address.toJavaAddress().address,
                port = datagram.address.toJavaAddress().port,
            ),
            bytes = datagram.packet.readBytes()
        )
    }

    override suspend fun send(bytes: ByteArray, targetSourceHost: SourceHost) {
        val datagram = Datagram(
            ByteReadPacket(
                bytes,
            ),
            InetSocketAddress(
                targetSourceHost.ip,
                targetSourceHost.port,
            )
        )
        socket.send(datagram)
    }
}