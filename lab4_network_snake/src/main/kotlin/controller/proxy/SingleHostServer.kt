package onlinesnake.controller.proxy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import onlinesnake.controller.dto.MessageWithType
import onlinesnake.net.*
import onlinesnake.net.dto.*
import onlinesnake.net.dto.common.SourceHost

class SingleHostServer(
    coroutineScope: CoroutineScope,
    private val remoteSourceHost: SourceHost,
    private val messageSender: SuspendMessageSender,
    private val messageSerializer: SuspendMessageSerializer,
    private val messageDeserializer: SuspendMessageDeserializer,
    private val deleteJobChannel: SendChannel<SourceHost>,
    private val localHostProxyEventSendChannel: SendChannel<ProxyEvent>,
    stateDelayMs: Long,
) {
    private val oneMessageDelayMs = stateDelayMs / 10
    private val timeoutDelayMs = stateDelayMs * 8 / 10

    private val remoteHostMessageChannel = Channel<SerializedMessage>(Channel.UNLIMITED)
    private val localHostConfirmMessageChannel = Channel<MessageWithType>(Channel.UNLIMITED)
    private val localHostNoConfirmMessageChannel = Channel<MessageWithType>(Channel.UNLIMITED)

    private val answerMessageChannel = Channel<RawMessage>(Channel.UNLIMITED)

    val remoteHostMessagesSendChannel: SendChannel<SerializedMessage> = remoteHostMessageChannel
    val localHostConfirmMessageSendChannel: SendChannel<MessageWithType> = localHostConfirmMessageChannel
    val localHostNoConfirmMessageSendChannel: SendChannel<MessageWithType> = localHostNoConfirmMessageChannel

    private val remoteHostListenJob = coroutineScope.launch {
        while (true) {
            val serializedMessage = withTimeoutOrNull(timeoutDelayMs) {
                remoteHostMessageChannel.receive()
            }
            if (serializedMessage == null) {
                localHostProxyEventSendChannel.send(ProxyEvent(ProxyEventType.CONNECTION_LOST, remoteSourceHost))
                localHostConfirmListenJob.cancel()
                localHostNoConfirmListenJob.cancel()
                deleteJobChannel.send(remoteSourceHost)
                return@launch
            }
            val rawMessage = messageDeserializer.deserialize(serializedMessage)
            when (rawMessage.type) {
                MessageType.ACK -> {
                    answerMessageChannel.send(rawMessage)
                }
                MessageType.ERROR -> {
                    answerMessageChannel.send(rawMessage)
                }
                else -> { }
            }
            localHostProxyEventSendChannel.send(ProxyEvent(ProxyEventType.NETWORK_MESSAGE, rawMessage))
        }
    }

    private val localHostConfirmListenJob = coroutineScope.launch {
        while (true) {
            val sendMessage = withTimeoutOrNull(oneMessageDelayMs) {
                localHostConfirmMessageChannel.receive()
            }
            if (sendMessage == null) {
                localHostProxyEventSendChannel.send(ProxyEvent(ProxyEventType.MAKE_PING, remoteSourceHost))
                continue
            }
            when (sendMessage.messageType) {
                MessageType.PING -> {
                    val msg = sendMessage.message as MsgPing
                    sendWithWaitingAnswer(msg.gameMessageInfo.msgSeq) {
                        messageSender.send(messageSerializer.serializePing(msg), remoteSourceHost)
                    }
                }
                MessageType.STEER -> {
                    val msg = sendMessage.message as MsgSteer
                    sendWithWaitingAnswer(msg.gameMessageInfo.msgSeq) {
                        messageSender.send(messageSerializer.serializeSteer(msg), remoteSourceHost)
                    }
                }
                MessageType.ACK -> { }
                MessageType.STATE -> {
                    val msg = sendMessage.message as MsgState
                    sendWithWaitingAnswer(msg.gameMessageInfo.msgSeq) {
                        messageSender.send(messageSerializer.serializeState(msg), remoteSourceHost)
                    }
                }
                MessageType.ANNOUNCEMENT -> { }
                MessageType.JOIN -> {
                    val msg = sendMessage.message as MsgJoin
                    println(remoteSourceHost)
                    sendWithWaitingAnswer(msg.gameMessageInfo.msgSeq) {
                        messageSender.send(messageSerializer.serializeJoin(msg), remoteSourceHost)
                    }
                }
                MessageType.ERROR -> { }
                MessageType.ROLE_CHANGE -> {
                    val msg = sendMessage.message as MsgRoleChange
                    println(remoteSourceHost)
                    sendWithWaitingAnswer(msg.gameMessageInfo.msgSeq) {
                        messageSender.send(messageSerializer.serializeRoleChange(msg), remoteSourceHost)
                    }
                }
                MessageType.DISCOVER -> { }
                MessageType.UNKNOWN -> { }
            }
        }
    }

    private val localHostNoConfirmListenJob = coroutineScope.launch {
        while (true) {
            val sendMessage = localHostNoConfirmMessageChannel.receive()
            when (sendMessage.messageType) {
                MessageType.ACK -> {
                    val msgAck = sendMessage.message as MsgAck
                    messageSender.send(messageSerializer.serializeAck(msgAck), remoteSourceHost)
                }
                MessageType.ERROR -> {
                    val msgError = sendMessage.message as MsgError
                    messageSender.send(messageSerializer.serializeError(msgError), remoteSourceHost)
                }
                else -> { }
            }
        }
    }

    private suspend fun sendWithWaitingAnswer(targetMsgSeq: Long, action: suspend () -> Unit): RawMessage {
        while (true) {
            action()
            val rawMessage = withTimeoutOrNull(oneMessageDelayMs) {
                var answer: RawMessage
                do {
                    answer = answerMessageChannel.receive()
                } while (answer.gameMessageInfo.msgSeq < targetMsgSeq)
                answer
            }
            if (rawMessage != null) {
                return rawMessage
            }
        }
    }

    suspend fun cancel() {
        remoteHostListenJob.cancel()
        localHostConfirmListenJob.cancel()
        localHostNoConfirmListenJob.cancel()
    }
}