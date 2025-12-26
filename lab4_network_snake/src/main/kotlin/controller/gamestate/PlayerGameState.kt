package onlinesnake.controller.gamestate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import onlinesnake.controller.dto.MessageWithType
import onlinesnake.dto.Direction
import onlinesnake.dto.GameConfig
import onlinesnake.net.RawMessage
import onlinesnake.net.dto.*
import onlinesnake.net.dto.common.GameMessageInfo
import onlinesnake.net.dto.common.NodeRole
import onlinesnake.net.dto.common.PlayerType
import onlinesnake.net.dto.common.SourceHost
import onlinesnake.ui.GameUI
import onlinesnake.ui.dto.UpdateGameDto
import onlinesnake.ui.dto.Player as UIPlayer

class PlayerGameState(
    private var masterSourceHost: SourceHost,
    private val gameName: String,
    private val playerName: String,
    private val gameUI: GameUI,
    private val gameConfig: GameConfig,
    defaultCoroutineScope: CoroutineScope,
    private val deputyUpgrader: DeputyUpgrader,
    private val viewerUpgrader: ViewerUpgrader,
    private val gameExiter: GameExiter,
) : GameState {
    private val outComingConfirmMessageChannel = Channel<MessageWithType>(Channel.UNLIMITED)
    private val outComingNoConfirmMessageChannel = Channel<MessageWithType>(Channel.UNLIMITED)

    override val outComingConfirmMessageReceiveChannel: ReceiveChannel<MessageWithType> = outComingConfirmMessageChannel
    override val outComingNoConfirmMessageReceiveChannel: ReceiveChannel<MessageWithType> =
        outComingNoConfirmMessageChannel

    private val eventChannel = Channel<GameEvent>(Channel.UNLIMITED)

    private var masterID: Int = 0
    private var playerID: Int = 0
    private var knownStateOrder: Int = 0
    private var msgSeq = 0L

    private var deputySourceHost: SourceHost? = null
    private var deputyID = 0

    private var waitingForJoin = true

    override suspend fun newDirection(direction: Direction) {
        eventChannel.send(GameEvent(GameEventType.NEW_DIRECTION, direction))
    }

    override suspend fun exit() {
        eventChannel.send(GameEvent(GameEventType.EXIT, GameEventType.EXIT))
    }

    override suspend fun ping(sourceHost: SourceHost) {
        eventChannel.send(GameEvent(GameEventType.MAKE_PING, sourceHost))
    }

    override suspend fun announce(sourceHost: SourceHost) { }

    override suspend fun connectionLost(sourceHost: SourceHost) {
        eventChannel.send(GameEvent(GameEventType.CONNECTION_LOST, sourceHost))
    }

    override suspend fun networkMessage(rawMessage: RawMessage) {
        eventChannel.send(GameEvent(GameEventType.NETWORK_MESSAGE, rawMessage))
    }

    override suspend fun cancel() {
        eventJob.cancel()
    }

    private val eventJob = defaultCoroutineScope.launch {
        joinToServer()
        while (true) {
            val gameEvent = eventChannel.receive()
            println(gameEvent.eventType)
            when (gameEvent.eventType) {
                GameEventType.EXIT -> {
                    gameExiter.exitGame()
                    return@launch
                }

                GameEventType.NETWORK_MESSAGE -> {
                    val rawMessage = gameEvent.attachment as RawMessage
                    handleNetworkMessage(rawMessage)
                }

                GameEventType.MAKE_PING -> {
                    val remoteSourceHost = gameEvent.attachment as SourceHost
                    handleMakePing(remoteSourceHost)
                }

                GameEventType.CONNECTION_LOST -> {
                    val remoteSourceHost = gameEvent.attachment as SourceHost
                    handleConnectionLost(remoteSourceHost)
                }

                GameEventType.NEW_DIRECTION -> {
                    val direction = gameEvent.attachment as Direction
                    handleNewDirection(direction)
                }

                GameEventType.UPDATE_FIELD -> { }
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
                hasReceiverId = false
            ),
            playerType = PlayerType.HUMAN,
            playerName = playerName,
            gameName = gameName,
            requestedRole = NodeRole.NORMAL,
        )
        outComingConfirmMessageChannel.send(MessageWithType(MessageType.JOIN, masterSourceHost, msgJoin))
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
                "client received message not from master or deputy"
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
                    masterSourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    masterID,
                    playerID,
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck))
            }

            MessageType.STEER -> {
                val msgError = makeMsgError(
                    rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    "I AM NOT A CENTER NODE"
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
            }

            MessageType.ACK -> {
                if (waitingForJoin) {
                    val msgAck = rawMessage.getAsAck()
                    playerID = msgAck.gameMessageInfo.receiverID
                    waitingForJoin = false
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
                                UIPlayer(
                                    name = netPlayer.name,
                                    score = netPlayer.score,
                                    isMe = netPlayer.id == playerID,
                                    isMaster = !netPlayer.hasSourceHost
                                )
                            },
                            gameConfig = gameConfig,
                            myID = playerID,
                            masterName = msgState.playerList.find {
                                !it.hasSourceHost
                            }?.name ?: gameName
                        )
                    )
                }
                val msgAck = makeMsgAck(
                    rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    masterID,
                    playerID
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck))
                val newDeputyPlayer = msgState.playerList.find {
                    it.hasSourceHost && it.nodeRole == NodeRole.DEPUTY
                }
                if (newDeputyPlayer != null) {
                    if (newDeputyPlayer.id == playerID) {
                        deputyUpgrader.upgradeToDeputy(
                            masterSourceHost,
                            gameName,
                            playerName,
                            gameConfig,
                            outComingConfirmMessageChannel,
                            outComingNoConfirmMessageChannel,
                            msgSeq,
                            masterID,
                            playerID,
                            knownStateOrder,
                            eventChannel
                        )
                    } else {
                        deputySourceHost = newDeputyPlayer.sourceHost
                        deputyID = newDeputyPlayer.id
                    }
                }
                println("I KNOW DEPUTY: $deputySourceHost")
            }

            MessageType.ANNOUNCEMENT -> {
                val msgError = makeMsgError(
                    rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    "IN A WRONG IP:PORT"
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
            }

            MessageType.JOIN -> {
                val msgError = makeMsgError(
                    rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    "i am not a center node"
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
            }

            MessageType.ERROR -> {
                gameExiter.exitGame()
            }

            MessageType.ROLE_CHANGE -> {
                val msgRoleChange = rawMessage.getAsRoleChange()
                outComingNoConfirmMessageChannel.send(MessageWithType(
                    MessageType.ACK,
                    masterSourceHost,
                    makeMsgAck(masterSourceHost, msgRoleChange.gameMessageInfo.msgSeq, masterID, playerID)
                ))
                if (msgRoleChange.receiverRole == NodeRole.VIEWER) {
                    viewerUpgrader.upgradeToViewer(
                        masterSourceHost,
                        deputySourceHost,
                        gameConfig,
                        outComingConfirmMessageChannel,
                        outComingNoConfirmMessageChannel,
                        msgSeq,
                        knownStateOrder,
                        masterID,
                        deputyID,
                        playerID,
                        playerName,
                        gameName,
                        eventChannel,
                    )
                } else if (msgRoleChange.receiverRole == NodeRole.DEPUTY) {
                    outComingNoConfirmMessageChannel.send(MessageWithType(
                        MessageType.ACK,
                        masterSourceHost,
                        makeMsgAck(masterSourceHost, msgRoleChange.gameMessageInfo.msgSeq, masterID, playerID)
                    ))
                    deputyUpgrader.upgradeToDeputy(
                        masterSourceHost,
                        gameName,
                        playerName,
                        gameConfig,
                        outComingConfirmMessageChannel,
                        outComingNoConfirmMessageChannel,
                        msgSeq,
                        masterID,
                        playerID,
                        knownStateOrder,
                        eventChannel
                    )
                }
            }

            MessageType.DISCOVER -> {
                val msgError = makeMsgError(
                    rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    "IN A WRONG IP:PORT"
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
            }

            MessageType.UNKNOWN -> {
                val msgError = makeMsgError(
                    rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    "unknown message"
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
            }
        }
    }

    private suspend fun handleMakePing(remoteSourceHost: SourceHost) {
        outComingConfirmMessageChannel.send(MessageWithType(MessageType.PING, remoteSourceHost, makeMsgPing(remoteSourceHost, msgSeq++)))
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

    private suspend fun handleNewDirection(direction: Direction) {
        val msgSteer = makeMsgSteer(
            masterSourceHost,
            direction,
            msgSeq++
        )
        outComingConfirmMessageChannel.send(MessageWithType(MessageType.STEER, masterSourceHost, msgSteer))
    }
}
