package onlinesnake.net

import onlinesnake.net.dto.*

interface SuspendMessageSerializer {
    suspend fun serializeAnnouncement(msgAnnouncement: MsgAnnouncement): ByteArray
    suspend fun serializePing(msgPing: MsgPing): ByteArray
    suspend fun serializeSteer(msgSteer: MsgSteer): ByteArray
    suspend fun serializeJoin(msgJoin: MsgJoin): ByteArray
    suspend fun serializeRoleChange(msgRoleChange: MsgRoleChange): ByteArray
    suspend fun serializeError(msgError: MsgError): ByteArray
    suspend fun serializeAck(msgAck: MsgAck): ByteArray
    suspend fun serializeState(msgState: MsgState): ByteArray
    suspend fun serializeDiscover(msgDiscover: MsgDiscover): ByteArray
}