package onlinesnake.controller.gamestate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import onlinesnake.controller.dto.MessageWithType
import onlinesnake.dto.Direction
import onlinesnake.dto.GameConfig
import onlinesnake.model.IDWithScore
import onlinesnake.net.RawMessage
import onlinesnake.net.dto.*
import onlinesnake.net.dto.common.NodeRole
import onlinesnake.net.dto.common.SourceHost
import onlinesnake.ui.GameUI
import onlinesnake.ui.dto.Player
import onlinesnake.ui.dto.UpdateGameDto

class DeputyGameState(
    private var masterSourceHost: SourceHost,
    private val gameUI: GameUI,
    private val gameConfig: GameConfig,
    defaultCoroutineScope: CoroutineScope,
    private val outComingConfirmMessageChannel: Channel<MessageWithType>,
    private val outComingNoConfirmMessageChannel: Channel<MessageWithType>,
    private var knownStateOrder: Int,
    private var msgSeq: Long,
    private var masterID: Int,
    private var deputyID: Int,
    private val gameName: String,
    private val deputyName: String,
    private val eventChannel: Channel<GameEvent>,
    private val masterUpgrader: MasterUpgrader,
    private val viewerUpgrader: ViewerUpgrader,
    private val gameExiter: GameExiter,
) : GameState {
    override val outComingConfirmMessageReceiveChannel: ReceiveChannel<MessageWithType> =
        outComingConfirmMessageChannel
    override val outComingNoConfirmMessageReceiveChannel: ReceiveChannel<MessageWithType> =
        outComingNoConfirmMessageChannel

    private var lastKnownMessageState: MsgState? = null

    init {
        println("I AM DEPUTY")
    }

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
        while (true) {
            val gameEvent = eventChannel.receive()
            println(gameEvent.eventType)
            println("FROM DEPUTY")
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
                    handleConnectionLost(gameEvent.attachment as SourceHost)
                }

                GameEventType.NEW_DIRECTION -> {
                    val direction = gameEvent.attachment as Direction
                    handleNewDirection(direction)
                }

                GameEventType.UPDATE_FIELD -> { }
            }
        }
    }

    private suspend fun handleNetworkMessage(rawMessage: RawMessage) {
        println(rawMessage.type)
        if (rawMessage.sourceHost != masterSourceHost) {
            handleConnectionLost(masterSourceHost)
        }
        when (rawMessage.type) {
            MessageType.PING -> {
                val msgAck = makeMsgAck(
                    masterSourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    masterID,
                    deputyID,
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

            }

            MessageType.STATE -> {
                println("I AM HANDLING STATE")
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
                                    isMe = netPlayer.id == deputyID,
                                    isMaster = !netPlayer.hasSourceHost
                                )
                            },
                            gameConfig = gameConfig,
                            myID = deputyID,
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
                    deputyID,
                )
                lastKnownMessageState = msgState
                outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck))
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
                when (msgRoleChange.receiverRole) {
                    NodeRole.VIEWER -> {
                        viewerUpgrader.upgradeToViewer(
                            masterSourceHost,
                            null,
                            gameConfig,
                            outComingConfirmMessageChannel,
                            outComingNoConfirmMessageChannel,
                            msgSeq,
                            knownStateOrder,
                            masterID,
                            0,
                            deputyID,
                            deputyName,
                            gameName,
                            eventChannel,
                        )
                    }

                    NodeRole.DEPUTY -> {
                        println("I AM HERE")
                        outComingNoConfirmMessageChannel.send(MessageWithType(
                            MessageType.ACK,
                            masterSourceHost,
                            makeMsgAck(masterSourceHost, msgRoleChange.gameMessageInfo.msgSeq, masterID, deputyID)
                        ))
                    }

                    NodeRole.MASTER -> {
                        outComingNoConfirmMessageChannel.send(MessageWithType(
                            MessageType.ACK,
                            masterSourceHost,
                            makeMsgAck(masterSourceHost, msgRoleChange.gameMessageInfo.msgSeq, masterID, deputyID)
                        ))
                        handleConnectionLost(masterSourceHost)
                    }

                    NodeRole.NORMAL -> { }
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

    private suspend fun handleConnectionLost(sourceHost: SourceHost) {
        if (sourceHost == masterSourceHost) {
            masterUpgrader.upgradeToMaster(
                gameConfig,
                gameName,
                deputyName,
                lastKnownMessageState!!.snakeList,
                lastKnownMessageState!!.foodList,
                lastKnownMessageState!!.playerList.map { player ->
                    IDWithScore(
                        id = player.id,
                        score = player.score,
                    )
                },
                outComingConfirmMessageChannel,
                outComingNoConfirmMessageChannel,
                lastKnownMessageState!!.gameMessageInfo.msgSeq,
                lastKnownMessageState!!.stateOrder+1,
                eventChannel,
                lastKnownMessageState!!.playerList,
                deputyID,
            )
        }
    }

    private suspend fun handleNewDirection(direction: Direction) {
        val msgSteer = makeMsgSteer(
            masterSourceHost,
            direction,
            msgSeq++,
        )
        outComingConfirmMessageChannel.send(MessageWithType(MessageType.STEER, masterSourceHost, msgSteer))
    }
}
