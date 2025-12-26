package onlinesnake.net

import onlinesnake.net.dto.common.SourceHost

interface SuspendMessageDeserializer {
    suspend fun deserialize(bytes: ByteArray, sourceHost: SourceHost): RawMessage
    suspend fun deserialize(serializedMessage: SerializedMessage): RawMessage
}