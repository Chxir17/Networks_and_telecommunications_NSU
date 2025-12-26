package onlinesnake.net

import onlinesnake.net.dto.common.SourceHost

interface SuspendMessageSender {
    suspend fun send(bytes: ByteArray, targetSourceHost: SourceHost)
    suspend fun send(serializedMessage: SerializedMessage) {
        send(serializedMessage.bytes, serializedMessage.sourceHost)
    }
}