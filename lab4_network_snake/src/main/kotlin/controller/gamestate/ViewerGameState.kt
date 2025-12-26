package onlinesnake.controller.gamestate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import onlinesnake.controller.dto.MessageWithType
import onlinesnake.controller.dto.MessageWithTypeAndCreationTime
import onlinesnake.dto.Direction
import onlinesnake.dto.GameConfig
import onlinesnake.net.RawMessage
import onlinesnake.net.dto.MessageType
import onlinesnake.net.dto.MsgJoin
import onlinesnake.net.dto.MsgPing
import onlinesnake.net.dto.common.GameMessageInfo
import onlinesnake.net.dto.common.NodeRole
import onlinesnake.net.dto.common.PlayerType
import onlinesnake.net.dto.common.SourceHost
import onlinesnake.ui.GameUI
import onlinesnake.ui.dto.Player
import onlinesnake.ui.dto.UpdateGameDto
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class ViewerGameState(
    private var masterSourceHost: SourceHost,
    private var deputySourceHost: SourceHost?,
    private val gameUI: GameUI,
    private val gameConfig: GameConfig,
    defaultCoroutineScope: CoroutineScope,
    private val outComingConfirmMessageChannel: Channel<MessageWithType>,
    private val outComingNoConfirmMessageChannel: Channel<MessageWithType>,
    private var knownStateOrder: Int,
    private var msgSeq: Long,
    private var masterID: Int,
    private var deputyID: Int,
    private val initViewerID: Int?,
    private val viewerName: String,
    private val gameName: String,
    private val eventChannel: Channel<GameEvent>,
    private val gameExiter: GameExiter
) : GameState {

    private var viewerID = 0

    override val outComingConfirmMessageReceiveChannel: ReceiveChannel<MessageWithType> = outComingConfirmMessageChannel
    override val outComingNoConfirmMessageReceiveChannel: ReceiveChannel<MessageWithType> = outComingNoConfirmMessageChannel

    private var waitingForJoin = false

//    private val messagesMap = ConcurrentHashMap<SourceHost, ConcurrentHashMap<Long, MessageWithTypeAndCreationTime>>()

    override suspend fun newDirection(direction: Direction) {
    }

    override suspend fun exit() {
        eventChannel.send(GameEvent(GameEventType.EXIT, GameEventType.EXIT))
    }

    override suspend fun ping(sourceHost: SourceHost) {
        eventChannel.send(GameEvent(GameEventType.MAKE_PING, sourceHost))
    }

    override suspend fun announce(sourceHost: SourceHost) {
    }

    override suspend fun connectionLost(sourceHost: SourceHost) {
        eventChannel.send(GameEvent(GameEventType.CONNECTION_LOST, sourceHost))
    }

    override suspend fun networkMessage(rawMessage: RawMessage) {
        eventChannel.send(GameEvent(GameEventType.NETWORK_MESSAGE, rawMessage))
    }

    override suspend fun cancel() {
        eventJob.cancel()
//        cleaningJob.cancel()
    }

//    private val cleaningJob = defaultCoroutineScope.launch {
//        while (true) {
//            delay(gameConfig.stateDelayMs.toLong() * 2)
//            val nowInstant = Instant.now()
//            val sourceHostsIter = messagesMap.entries.iterator()
//            while (sourceHostsIter.hasNext()) {
//                val nextSourceHostEntry = sourceHostsIter.next()
//                val msgSeqIter = nextSourceHostEntry.value.entries.iterator()
//                while (msgSeqIter.hasNext()) {
//                    val nextMsgSeqEntry = msgSeqIter.next()
//                    if (ChronoUnit.MILLIS.between(nextMsgSeqEntry.value.creationInstant, nowInstant) > gameConfig.stateDelayMs) {
//                        msgSeqIter.remove()
//                    }
//                }
//                if (nextSourceHostEntry.value.size == 0) {
//                    sourceHostsIter.remove()
//                }
//            }
//        }
//    }

    private val eventJob = defaultCoroutineScope.launch {
        waitingForJoin = if (initViewerID == null) {
            joinToServer()
            true
        } else {
            false
        }
        while (true) {
            val gameEvent = eventChannel.receive()
            println(gameEvent.eventType)
            when (gameEvent.eventType) {
                GameEventType.CONNECTION_LOST -> {
                    val remoteSourceHost = gameEvent.attachment as SourceHost
                    handleConnectionLost(remoteSourceHost)
                }
                GameEventType.MAKE_PING -> {
                    val remoteSourceHost = gameEvent.attachment as SourceHost
                    handleMakePing(remoteSourceHost)
                }
                GameEventType.EXIT -> {
                    gameExiter.exitGame()
                    return@launch
                }
                GameEventType.NETWORK_MESSAGE -> {
                    val rawMessage = gameEvent.attachment as RawMessage
                    handleNetworkMessage(rawMessage)
                }
                GameEventType.UPDATE_FIELD -> { }
                GameEventType.NEW_DIRECTION -> { }
            }
        }
    }

    private suspend fun joinToServer() {
        val msgJoin = MsgJoin(
            sourceHost = masterSourceHost,
            gameMessageInfo = GameMessageInfo(
                msgSeq = msgSeq++,
                senderID = 0,
                receiverID = 0,
                hasSenderID = false,
                hasReceiverId = false,
            ),
            playerType = PlayerType.HUMAN,
            playerName = viewerName,
            gameName = gameName,
            requestedRole = NodeRole.VIEWER,
        )
        outComingConfirmMessageChannel.send(MessageWithType(
            MessageType.JOIN,
            masterSourceHost,
            msgJoin,
        ))
    }

    private suspend fun handleConnectionLost(remoteSourceHost: SourceHost) {
        if (remoteSourceHost == masterSourceHost) {
            if (deputySourceHost != null) {
                masterSourceHost = deputySourceHost!!
                deputySourceHost = null
                handleMakePing(masterSourceHost)
            } else {
                gameExiter.exitGame()
            }
        }
    }

    private suspend fun handleMakePing(remoteSourceHost: SourceHost) {
        outComingConfirmMessageChannel.send(MessageWithType(
            MessageType.PING,
            remoteSourceHost,
            makeMsgPing(remoteSourceHost, msgSeq++)
        ))
    }

    private suspend fun handleNetworkMessage(rawMessage: RawMessage) {
        println(rawMessage.type)
        if (rawMessage.sourceHost == deputySourceHost) {
            masterSourceHost = deputySourceHost!!
            masterID = deputyID
            deputySourceHost = null
        }
        if (rawMessage.sourceHost != masterSourceHost) {
            val msgError = makeMsgError(
                rawMessage.sourceHost,
                rawMessage.gameMessageInfo.msgSeq,
                "viewer received message not from master or deputy"
            )
            outComingNoConfirmMessageChannel.send(MessageWithType(
                MessageType.ERROR,
                rawMessage.sourceHost,
                msgError
            ))
        }
        when (rawMessage.type) {
            MessageType.PING -> {
                val msgAck = makeMsgAck(
                    rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    masterID,
                    viewerID,
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck))
            }
            MessageType.STEER -> {
                val msgError = makeMsgError(
                    rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    "viewer cannot handle steer"
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(
                    MessageType.ERROR,
                    rawMessage.sourceHost,
                    msgError
                ))
            }
            MessageType.ACK -> {
                if (waitingForJoin) {
                    val msgAck = rawMessage.getAsAck()
                    viewerID = msgAck.gameMessageInfo.receiverID
                    waitingForJoin = true
                }
            }
            MessageType.STATE -> {
                val msgState = rawMessage.getAsState()
                if (msgState.stateOrder > knownStateOrder) {
                    knownStateOrder = msgState.stateOrder
                    gameUI.updateField(
                        UpdateGameDto(
                            stateOrder = msgState.stateOrder,
                            snakesList = msgState.snakeList,
                            foodList = msgState.foodList,
                            players = msgState.playerList.map { netPlayer ->
                                Player(
                                    name = netPlayer.name,
                                    score = netPlayer.score,
                                    isMe = false,
                                    isMaster = !netPlayer.hasSourceHost
                                )
                            },
                            gameConfig = gameConfig,
                            myID = viewerID,
                            masterName = msgState.playerList.find {
                                !it.hasSourceHost
                            }?.name ?: gameName
                        )
                    )
                }
                val msgAck = makeMsgAck(rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    masterID,
                    viewerID
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck))
                val newDeputyPlayer = msgState.playerList.find {
                    it.hasSourceHost && it.nodeRole == NodeRole.DEPUTY
                }
                if (newDeputyPlayer != null) {
                    deputySourceHost = newDeputyPlayer.sourceHost
                    deputyID = newDeputyPlayer.id
                }
            }
            MessageType.ANNOUNCEMENT -> {
                val msgError = makeMsgError(
                    rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    "viewer cannot handle announcement"
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(
                    MessageType.ERROR,
                    rawMessage.sourceHost,
                    msgError
                ))
            }
            MessageType.JOIN -> {
                val msgError = makeMsgError(
                    rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    "viewer cannot handle join"
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(
                    MessageType.ERROR,
                    rawMessage.sourceHost,
                    msgError
                ))
            }
            MessageType.ERROR -> {
                gameExiter.exitGame()
            }
            MessageType.ROLE_CHANGE -> {
                val msgAck = makeMsgAck(rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    masterID,
                    viewerID
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck))
            }
            MessageType.DISCOVER -> {
                val msgError = makeMsgError(
                    rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    "viewer cannot handle discover"
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(
                    MessageType.ERROR,
                    rawMessage.sourceHost,
                    msgError
                ))
            }
            MessageType.UNKNOWN -> {
                val msgError = makeMsgError(
                    rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    "viewer cannot handle discover"
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(
                    MessageType.ERROR,
                    rawMessage.sourceHost,
                    msgError
                ))
            }
        }
    }
}