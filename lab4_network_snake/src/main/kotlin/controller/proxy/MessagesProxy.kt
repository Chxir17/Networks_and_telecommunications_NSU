package onlinesnake.controller.proxy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import onlinesnake.controller.dto.MessageWithType
import onlinesnake.net.SuspendMessageDeserializer
import onlinesnake.net.SuspendMessageReader
import onlinesnake.net.SuspendMessageSender
import onlinesnake.net.SuspendMessageSerializer
import onlinesnake.net.dto.*
import onlinesnake.net.dto.common.SourceHost

class MessagesProxy(
    private val messageDeserializer: SuspendMessageDeserializer,
    private val messageSerializer: SuspendMessageSerializer,
    private val messageSender: SuspendMessageSender,
    private val messageReader: SuspendMessageReader,
    private val coroutineScope: CoroutineScope,
    private val stateDelayMs: Long,
) {
    private val sourceHostToSingleHostServer = mutableMapOf<SourceHost, SingleHostServer>()
    private val deleteJobChannel = Channel<SourceHost>(Channel.UNLIMITED)

    private val mutex = Mutex()

    private val proxyEventChannel = Channel<ProxyEvent>(Channel.UNLIMITED)

    val proxyEventReceiveChannel: ReceiveChannel<ProxyEvent> = proxyEventChannel

    private val deletingJob = coroutineScope.launch {
        while (true) {
            val sourceHostToDelete = deleteJobChannel.receive()
            mutex.withLock {
                sourceHostToSingleHostServer.remove(sourceHostToDelete)
            }
        }
    }

    private val receivingJob = coroutineScope.launch {
        while (true) {
            val serializedMessage = messageReader.read()
            val sourceHost = serializedMessage.sourceHost
            mutex.withLock {
                val singleHostServer = sourceHostToSingleHostServer[sourceHost]
                if (singleHostServer == null) {
                    val newSingleHostServer = SingleHostServer(
                        coroutineScope = coroutineScope,
                        remoteSourceHost = sourceHost,
                        messageSender = messageSender,
                        messageSerializer = messageSerializer,
                        messageDeserializer = messageDeserializer,
                        deleteJobChannel = deleteJobChannel,
                        localHostProxyEventSendChannel = proxyEventChannel,
                        stateDelayMs = stateDelayMs,
                    )
                    sourceHostToSingleHostServer[sourceHost] = newSingleHostServer
                    newSingleHostServer.remoteHostMessagesSendChannel.send(serializedMessage)
                } else {
                    singleHostServer.remoteHostMessagesSendChannel.send(serializedMessage)
                }
            }
        }
    }

    suspend fun sendConfirmMessage(messageWithType: MessageWithType) {
        mutex.withLock {
            val singleHostServer = sourceHostToSingleHostServer[messageWithType.sourceHost]
            if (singleHostServer == null) {
                val newSingleHostServer = SingleHostServer(
                    coroutineScope = coroutineScope,
                    remoteSourceHost = messageWithType.sourceHost,
                    messageSender = messageSender,
                    messageSerializer = messageSerializer,
                    messageDeserializer = messageDeserializer,
                    deleteJobChannel = deleteJobChannel,
                    localHostProxyEventSendChannel = proxyEventChannel,
                    stateDelayMs = stateDelayMs,
                )
                sourceHostToSingleHostServer[messageWithType.sourceHost] = newSingleHostServer
                newSingleHostServer.localHostConfirmMessageSendChannel.send(messageWithType)
            } else {
                singleHostServer.localHostConfirmMessageSendChannel.send(messageWithType)
            }
        }
    }

    suspend fun sendNoConfirmMessage(messageWithType: MessageWithType) {
        mutex.withLock {
            val singleHostServer = sourceHostToSingleHostServer[messageWithType.sourceHost]
            if (singleHostServer == null) {
                val newSingleHostServer = SingleHostServer(
                    coroutineScope = coroutineScope,
                    remoteSourceHost = messageWithType.sourceHost,
                    messageSender = messageSender,
                    messageSerializer = messageSerializer,
                    messageDeserializer = messageDeserializer,
                    deleteJobChannel = deleteJobChannel,
                    localHostProxyEventSendChannel = proxyEventChannel,
                    stateDelayMs = stateDelayMs,
                )
                sourceHostToSingleHostServer[messageWithType.sourceHost] = newSingleHostServer
                newSingleHostServer.localHostNoConfirmMessageSendChannel.send(messageWithType)
            } else {
                singleHostServer.localHostNoConfirmMessageSendChannel.send(messageWithType)
            }
        }
    }

    suspend fun sendDirect(messageWithType: MessageWithType) {
        when (messageWithType.messageType) {
            MessageType.PING -> { }
            MessageType.STEER -> { }
            MessageType.ACK -> {
                val msgAck = messageWithType.message as MsgAck
                messageSender.send(messageSerializer.serializeAck(msgAck), msgAck.sourceHost)
            }
            MessageType.STATE -> { }
            MessageType.ANNOUNCEMENT -> {
                val msgAnnouncement = messageWithType.message as MsgAnnouncement
                messageSender.send(messageSerializer.serializeAnnouncement(msgAnnouncement), msgAnnouncement.sourceHost)
            }
            MessageType.JOIN -> { }
            MessageType.ERROR -> {
                val msgError = messageWithType.message as MsgError
                messageSender.send(messageSerializer.serializeError(msgError), msgError.sourceHost)
            }
            MessageType.ROLE_CHANGE -> { }
            MessageType.DISCOVER -> {
                val msgDiscover = messageWithType.message as MsgDiscover
                messageSender.send(messageSerializer.serializeDiscover(msgDiscover), msgDiscover.sourceHost)
            }
            MessageType.UNKNOWN -> { }
        }
    }

    suspend fun cancel() {
        deletingJob.cancel()
        receivingJob.cancel()
        val iterator = sourceHostToSingleHostServer.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.cancel()
            iterator.remove()
        }
    }
}