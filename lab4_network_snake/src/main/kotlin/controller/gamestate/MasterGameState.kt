package onlinesnake.controller.gamestate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import onlinesnake.controller.dto.MessageWithType
import onlinesnake.dto.Coord
import onlinesnake.dto.Direction
import onlinesnake.dto.GameConfig
import onlinesnake.dto.Snake
import onlinesnake.model.IDWithScore
import onlinesnake.model.NoFreeSpaceException
import onlinesnake.model.SnakeGame
import onlinesnake.ui.dto.UpdateGameDto as UIUpdateGameDto
import onlinesnake.net.dto.common.Player as NetPlayer
import onlinesnake.ui.dto.Player as UIPlayer
import onlinesnake.net.*
import onlinesnake.net.dto.*
import onlinesnake.net.dto.common.*
import onlinesnake.ui.GameUI
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

class MasterGameState(
    private val gameUI: GameUI,
    private val gameConfig: GameConfig,
    defaultCoroutineScope: CoroutineScope,
    ioCoroutineScope: CoroutineScope,
    private val multicastSourceHost: SourceHost,
    private val gameName: String,
    private val masterPlayerName: String,
    snakeList: List<Snake>,
    foodList: List<Coord>,
    scores: List<IDWithScore>,
    private val outComingConfirmMessageChannel: Channel<MessageWithType>,
    private val outComingNoConfirmMessageChannel: Channel<MessageWithType>,
    initMsgSeq: Long,
    private var stateOrder: Int,
    private val eventChannel: Channel<GameEvent>,
    netPlayersList: List<NetPlayer>,
    private val initID: Int?,
    private val viewerUpgrader: ViewerUpgrader,
    private val gameExiter: GameExiter,
) : GameState {

    init {
        println("I AM SERVER")
    }

    override val outComingConfirmMessageReceiveChannel: ReceiveChannel<MessageWithType> = outComingConfirmMessageChannel
    override val outComingNoConfirmMessageReceiveChannel: ReceiveChannel<MessageWithType> = outComingNoConfirmMessageChannel

    private var msgSeq = AtomicLong(initMsgSeq)
    private var viewerMinAvailableID = -1
    private val announcementDelayMs = 1000L
    private val players = mutableMapOf<SourceHost, PlayerInfo>()
    private val viewers = mutableMapOf<SourceHost, ViewerInfo>()
    private val lastAnswers = mutableMapOf<SourceHost, MessageWithType>()

    private val snakeGame = SnakeGame(
        fieldWidth = gameConfig.width,
        fieldHeight = gameConfig.height,
        foodStatic = gameConfig.foodStatic,
        snakeList = snakeList,
        foodList = foodList,
        scores = scores,
    )

    init {
        netPlayersList.forEach { netPlayer ->
            if (netPlayer.hasSourceHost && netPlayer.id != initID) {
                players[netPlayer.sourceHost] = PlayerInfo(
                    id = netPlayer.id,
                    knownMsgSeq = 0,
                    name = netPlayer.name,
                    nodeRole = netPlayer.nodeRole
                )
            }
            if (!netPlayer.hasSourceHost) {
                snakeGame.makeZombie(netPlayer.id)
            }
        }
    }

    private suspend fun joinAllHosts() {
        players.entries.forEach { netPlayerEntry ->
            if (netPlayerEntry.key != deputySourceHost) {
                val msgRoleChange = MsgRoleChange(
                    sourceHost = netPlayerEntry.key,
                    gameMessageInfo = GameMessageInfo(
                        msgSeq = msgSeq.getAndIncrement(),
                        senderID = serverID,
                        receiverID = netPlayerEntry.value.id,
                        hasSenderID = true,
                        hasReceiverId = true,
                    ),
                    senderRole = NodeRole.MASTER,
                    receiverRole = NodeRole.NORMAL,
                    hasSenderRole = true,
                    hasReceiverRole = true,
                )
                outComingConfirmMessageChannel.send(
                    MessageWithType(
                        MessageType.ROLE_CHANGE,
                        netPlayerEntry.key,
                        msgRoleChange,
                    )
                )
            }
        }
    }

    private var serverID = initID ?: snakeGame.createSnake()

    private var deputySourceHost: SourceHost? = null

    private val masterOutComingMessagesMap = mutableMapOf<SourceHost, MutableMap<Long, MessageWithType>>()


    private val announcementJob = ioCoroutineScope.launch {
        while (true) {
            announce(multicastSourceHost)
            delay(announcementDelayMs)
        }
    }

    private val updaterJob = defaultCoroutineScope.launch {
        while (true) {
            delay(gameConfig.stateDelayMs.toLong())
            eventChannel.send(
                GameEvent(
                    eventType = GameEventType.UPDATE_FIELD,
                    attachment = GameEventType.UPDATE_FIELD
                )
            )
        }
    }

    private val eventJob = defaultCoroutineScope.launch {
        if (initID != null) {
            findNewDeputy()
        }
        joinAllHosts()
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
                    announcementJob.cancel()
                    updaterJob.cancel()
                    return@launch
                }
                GameEventType.NETWORK_MESSAGE -> {
                    val rawMessage = gameEvent.attachment as RawMessage
                    handleNetworkMessage(rawMessage)
                }
                GameEventType.UPDATE_FIELD -> {
                    handleUpdateField()
                }
                GameEventType.NEW_DIRECTION -> {
                    val direction = gameEvent.attachment as Direction
                    snakeGame.updateDirection(serverID, direction)
                }
            }
        }
    }

    private suspend fun handleConnectionLost(remoteSourceHost: SourceHost) {
        val playerInfo = players[remoteSourceHost]
        val viewerInfo = viewers[remoteSourceHost]
        if (playerInfo == null && viewerInfo == null) {
            return
        } else if (playerInfo == null && viewerInfo != null) {
            viewers.remove(remoteSourceHost)
        } else if (playerInfo != null && viewerInfo == null) {
            players.remove(remoteSourceHost)
            println("MADE ZOMBIE")
            snakeGame.makeZombie(playerInfo.id)
        }
        masterOutComingMessagesMap.remove(remoteSourceHost)
        lastAnswers.remove(remoteSourceHost)
        if (remoteSourceHost == deputySourceHost) {
            findNewDeputy()
        }
    }

    private suspend fun findNewDeputy() {
        if (players.isEmpty()) {
            deputySourceHost = null
            return
        }
        val newDeputyEntry = players.iterator().next()
        val msgRoleChange = MsgRoleChange(
            sourceHost = newDeputyEntry.key,
            gameMessageInfo = GameMessageInfo(
                msgSeq = msgSeq.getAndAdd(1),
                senderID = serverID,
                receiverID = newDeputyEntry.value.id,
                hasSenderID = true,
                hasReceiverId = true
            ),
            senderRole = NodeRole.MASTER,
            receiverRole = NodeRole.DEPUTY,
            hasSenderRole = true,
            hasReceiverRole = true,
        )
        outComingConfirmMessageChannel.send(MessageWithType(MessageType.ROLE_CHANGE, newDeputyEntry.key, msgRoleChange))
        val mapForSourceHost = masterOutComingMessagesMap[newDeputyEntry.key]
        if (mapForSourceHost == null) {
            masterOutComingMessagesMap[newDeputyEntry.key] = mutableMapOf(
                Pair(
                    msgRoleChange.gameMessageInfo.msgSeq,
                    MessageWithType(MessageType.ROLE_CHANGE, newDeputyEntry.key, msgRoleChange,)
                )
            )
        } else {
            mapForSourceHost[msgRoleChange.gameMessageInfo.msgSeq] = MessageWithType(MessageType.ROLE_CHANGE, newDeputyEntry.key, msgRoleChange)
        }
    }

    private suspend fun handleMakePing(remoteSourceHost: SourceHost) {
        outComingConfirmMessageChannel.send(MessageWithType(
            MessageType.PING,
            remoteSourceHost,
            makeMsgPing(remoteSourceHost, msgSeq.getAndIncrement())
        ))
    }

    private suspend fun handleNetworkMessage(rawMessage: RawMessage) {
        println(rawMessage.type)
        when (rawMessage.type) {
            MessageType.PING -> {
                println("IN PING: ${rawMessage.sourceHost}")
                val playerInfo = players[rawMessage.sourceHost]
                val viewerInfo = viewers[rawMessage.sourceHost]
                if (playerInfo == null && viewerInfo == null) {
                    val msgError = makeMsgError(
                        rawMessage.sourceHost,
                        rawMessage.gameMessageInfo.msgSeq,
                        "unknown host",
                    )
                    outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
                } else if (playerInfo == null) {
                    val msgAck = makeMsgAck(
                        rawMessage.sourceHost,
                        rawMessage.gameMessageInfo.msgSeq,
                        viewerInfo!!.id,
                        serverID
                    )
                    outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck))
                    viewerInfo.knownMsgSeq = max(viewerInfo.knownMsgSeq, rawMessage.gameMessageInfo.msgSeq)
                } else {
                    val msgAck = makeMsgAck(
                        rawMessage.sourceHost,
                        rawMessage.gameMessageInfo.msgSeq,
                        playerInfo.id,
                        serverID
                    )
                    outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck))
                    playerInfo.knownMsgSeq = max(playerInfo.knownMsgSeq, rawMessage.gameMessageInfo.msgSeq)
                }
            }
            MessageType.STEER -> {
                val playerInfo = players[rawMessage.sourceHost]
                if (playerInfo == null) {
                    val msgError = makeMsgError(
                        rawMessage.sourceHost,
                        rawMessage.gameMessageInfo.msgSeq,
                        "no associated player found"
                    )
                    outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
                } else {
                    if (playerInfo.knownMsgSeq < rawMessage.gameMessageInfo.msgSeq) {
                        val msgSteer = rawMessage.getAsSteer()
                        snakeGame.updateDirection(
                            playerInfo.id,
                            msgSteer.newDirection
                        )
                        val msgAck = makeMsgAck(
                            rawMessage.sourceHost,
                            rawMessage.gameMessageInfo.msgSeq,
                            playerInfo.id,
                            serverID,
                        )
                        outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck))
                        playerInfo.knownMsgSeq = rawMessage.gameMessageInfo.msgSeq
                        lastAnswers[rawMessage.sourceHost] = MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck)
                    } else if (playerInfo.knownMsgSeq == rawMessage.gameMessageInfo.msgSeq) {
                        val cachedAnswer = lastAnswers[rawMessage.sourceHost]!!
                        outComingNoConfirmMessageChannel.send(MessageWithType(cachedAnswer.messageType, rawMessage.sourceHost, cachedAnswer.message))
                    } else {
                        val msgError = makeMsgError(
                            rawMessage.sourceHost,
                            rawMessage.gameMessageInfo.msgSeq,
                            "outdated msg seq"
                        )
                        outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
                    }
                }
            }

            MessageType.ACK -> {
                println("from ${rawMessage.sourceHost}")
                val masterRequestMessagesMap = masterOutComingMessagesMap[rawMessage.sourceHost]
                if (masterRequestMessagesMap != null) {
                    val messageWithType = masterRequestMessagesMap[rawMessage.gameMessageInfo.msgSeq]
                    if (messageWithType != null) {
                        when (messageWithType.messageType) {
                            MessageType.ROLE_CHANGE -> {
                                val msgRoleChange = messageWithType.message as MsgRoleChange
                                if (msgRoleChange.senderRole == NodeRole.MASTER && msgRoleChange.receiverRole == NodeRole.DEPUTY) {
                                    deputySourceHost = rawMessage.sourceHost
                                    players[deputySourceHost]?.nodeRole = NodeRole.DEPUTY
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
            MessageType.STATE -> {
                val msgError = makeMsgError(
                    rawMessage.sourceHost,
                    rawMessage.gameMessageInfo.msgSeq,
                    "I AM A CENTER NODE, NOT YOU"
                )
                outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
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
                println("AT JOIN")
                println(rawMessage.gameMessageInfo.msgSeq)
                val possiblePlayerInfo = players[rawMessage.sourceHost]
                val possibleViewerInfo = viewers[rawMessage.sourceHost]
                if (possiblePlayerInfo == null && possibleViewerInfo == null) {
                    val msgJoin = rawMessage.getAsJoin()
                    when (msgJoin.requestedRole) {
                        NodeRole.NORMAL -> {
                            val newID = try {
                                snakeGame.createSnake()
                            } catch (e: NoFreeSpaceException) {
                                val msgError = makeMsgError(
                                    rawMessage.sourceHost,
                                    rawMessage.gameMessageInfo.msgSeq,
                                    "no free space found"
                                )
                                outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
                                return
                            }
                            val playerInfo = PlayerInfo(
                                id = newID,
                                knownMsgSeq = rawMessage.gameMessageInfo.msgSeq,
                                name = msgJoin.playerName,
                                nodeRole = NodeRole.NORMAL
                            )
                            players[rawMessage.sourceHost] = playerInfo
                            val msgAck = makeMsgAck(
                                rawMessage.sourceHost,
                                rawMessage.gameMessageInfo.msgSeq,
                                playerInfo.id,
                                serverID,
                            )
                            outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck))
                            lastAnswers[rawMessage.sourceHost] = MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck)
                            if (players.size == 1) {
                                findNewDeputy()
                            }
                        }
                        NodeRole.VIEWER -> {
                            val viewerInfo = ViewerInfo(
                                id = viewerMinAvailableID--,
                                knownMsgSeq = rawMessage.gameMessageInfo.msgSeq
                            )
                            viewers[rawMessage.sourceHost] = viewerInfo
                            val msgAck = makeMsgAck(
                                rawMessage.sourceHost,
                                rawMessage.gameMessageInfo.msgSeq,
                                viewerInfo.id,
                                serverID
                            )
                            val msg = MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck)
                            outComingNoConfirmMessageChannel.send(msg)
                            lastAnswers[rawMessage.sourceHost] = msg
                        }
                        else -> {
                            val msgError = makeMsgError(
                                rawMessage.sourceHost,
                                rawMessage.gameMessageInfo.msgSeq,
                                "invalid requested role"
                            )
                            outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
                        }
                    }
                } else {
                    val knownMsgSeq = possiblePlayerInfo?.knownMsgSeq ?: possibleViewerInfo!!.knownMsgSeq
                    if (rawMessage.gameMessageInfo.msgSeq > knownMsgSeq) {
                        val msgError = makeMsgError(
                            rawMessage.sourceHost,
                            rawMessage.gameMessageInfo.msgSeq,
                            "player has already joined"
                        )
                        outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
                    } else if (rawMessage.gameMessageInfo.msgSeq == knownMsgSeq) {
                        val cachedAnswer = lastAnswers[rawMessage.sourceHost]!!
                        outComingNoConfirmMessageChannel.send(MessageWithType(cachedAnswer.messageType, rawMessage.sourceHost, cachedAnswer.message))
                    } else {
                        val msgError = makeMsgError(
                            rawMessage.sourceHost,
                            rawMessage.gameMessageInfo.msgSeq,
                            "outdated msg seq"
                        )
                        outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
                    }
                }
            }
            MessageType.ERROR -> {
                println("SERVER: " + rawMessage.getAsError().errorMessage)
                handleConnectionLost(rawMessage.sourceHost)
//                val playerInfo = players[rawMessage.sourceHost]
//                val viewerInfo = viewers[rawMessage.sourceHost]
//                if (playerInfo == null && viewerInfo == null) {
//                    return
//                } else if (playerInfo == null && viewerInfo != null) {
//                    viewers.remove(rawMessage.sourceHost)
//                } else if (playerInfo != null && viewerInfo == null) {
//                    players.remove(rawMessage.sourceHost)
//                    viewers[rawMessage.sourceHost] = ViewerInfo(
//                        playerInfo.id,
//                        knownMsgSeq = playerInfo.knownMsgSeq
//                    )
//                }
            }
            MessageType.ROLE_CHANGE -> {
                if (initID == null) {
                    val msgError = makeMsgError(
                        rawMessage.sourceHost,
                        rawMessage.gameMessageInfo.msgSeq,
                        "I AM THE ROLES CONTROLLER"
                    )
                    outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ERROR, rawMessage.sourceHost, msgError))
                } else {
                    val msgRoleChange = rawMessage.getAsRoleChange()
                    val msgAck = makeMsgAck(
                        rawMessage.sourceHost,
                        rawMessage.gameMessageInfo.msgSeq,
                        msgRoleChange.gameMessageInfo.senderID,
                        serverID,
                    )
                    outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ACK, rawMessage.sourceHost, msgAck))
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

    private suspend fun handleUpdateField() {
        val updateResult = snakeGame.updateField()

        val serverIsDead = updateResult.idForRemoval.contains(serverID)
        if (serverIsDead) {
            gameExiter.exitGame()
        }

        updateResult.idForRemoval.forEach { removalId ->
            val forRemoveEntry = players.entries.find {
                it.value.id == removalId
            }
            if (forRemoveEntry != null) {
                val msgRoleChange = MsgRoleChange(
                    sourceHost = forRemoveEntry.key,
                    gameMessageInfo = GameMessageInfo(
                        msgSeq = msgSeq.getAndAdd(1),
                        senderID = serverID,
                        receiverID = forRemoveEntry.value.id,
                        hasSenderID = true,
                        hasReceiverId = true,
                    ),
                    senderRole = NodeRole.MASTER,
                    receiverRole = NodeRole.VIEWER,
                    hasSenderRole = true,
                    hasReceiverRole = true
                )
                outComingConfirmMessageChannel.send(MessageWithType(MessageType.ROLE_CHANGE, forRemoveEntry.key, msgRoleChange))
                players.remove(forRemoveEntry.key)
                viewers[forRemoveEntry.key] = ViewerInfo(
                    forRemoveEntry.value.id,
                    knownMsgSeq = forRemoveEntry.value.knownMsgSeq
                )
                if (deputySourceHost == forRemoveEntry.key) {
                    findNewDeputy()
                }
            }
        }


        val playersDto = mutableListOf<NetPlayer>()
        playersDto += players.entries.map { playerEntry ->
            NetPlayer(
                sourceHost = playerEntry.key,
                name = playerEntry.value.name,
                id = playerEntry.value.id,
                nodeRole = playerEntry.value.nodeRole,
                score = snakeGame.getScore(playerEntry.value.id),
                hasSourceHost = true,
                playerType = PlayerType.HUMAN,
            )
        }

        if (!serverIsDead) {
            playersDto += NetPlayer(
                sourceHost = SourceHost("", 0),
                name = masterPlayerName,
                id = serverID,
                nodeRole = NodeRole.MASTER,
                score = snakeGame.getScore(serverID),
                hasSourceHost = false,
                playerType = PlayerType.HUMAN,
            )
        }

        println(playersDto)
        println(players)
        gameUI.updateField(
            UIUpdateGameDto(
                stateOrder = stateOrder,
                snakesList = updateResult.snakes,
                foodList = updateResult.foodsPoint,
                players = playersDto.map {
                    UIPlayer(
                        name = it.name,
                        score = it.score,
                        isMe = it.id == serverID,
                        isMaster = it.id == serverID,
                    )
                },
                gameConfig = gameConfig,
                myID = serverID,
                masterName = masterPlayerName,
            )
        )

        players.entries.forEach { playerEntry ->
            val msgState = MsgState(
                sourceHost = playerEntry.key,
                gameMessageInfo = GameMessageInfo(
                    msgSeq = msgSeq.getAndAdd(1),
                    receiverID = 0,
                    senderID = 0,
                    hasReceiverId = false,
                    hasSenderID = false
                ),
                stateOrder = stateOrder,
                snakeList = updateResult.snakes,
                foodList = updateResult.foodsPoint,
                playerList = playersDto
            )
            println("PLAYER SOURCE HOST: ${playerEntry.key}")
            outComingConfirmMessageChannel.send(MessageWithType(MessageType.STATE, playerEntry.key, msgState))
        }
        viewers.entries.forEach { viewerEntry ->
            val msgState = MsgState(
                sourceHost = viewerEntry.key,
                gameMessageInfo = GameMessageInfo(
                    msgSeq = msgSeq.getAndAdd(1),
                    receiverID = 0,
                    senderID = 0,
                    hasReceiverId = false,
                    hasSenderID = false
                ),
                stateOrder = stateOrder,
                snakeList = updateResult.snakes,
                foodList = updateResult.foodsPoint,
                playerList = playersDto,
            )
            outComingConfirmMessageChannel.send(MessageWithType(MessageType.STATE, viewerEntry.key, msgState))
        }
        ++stateOrder


    }

    override suspend fun announce(sourceHost: SourceHost) {
        val msgAnnouncement = MsgAnnouncement(
            sourceHost = sourceHost,
            gameMessageInfo = GameMessageInfo(
                msgSeq = msgSeq.getAndAdd(1),
                senderID = 0,
                receiverID = 0,
                hasSenderID = false,
                hasReceiverId = false,
            ),
            gameAnnouncementList = listOf(
                GameAnnouncement(
                    playerList = listOf(),
                    gameConfig = gameConfig,
                    canJoin = true,
                    gameName = gameName
                )
            )
        )
        println("MULTICAST TO: $multicastSourceHost")
        outComingNoConfirmMessageChannel.send(MessageWithType(MessageType.ANNOUNCEMENT, multicastSourceHost, msgAnnouncement))
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

    override suspend fun connectionLost(sourceHost: SourceHost) {
        eventChannel.send(GameEvent(GameEventType.CONNECTION_LOST, sourceHost))
    }

    override suspend fun networkMessage(rawMessage: RawMessage) {
        eventChannel.send(GameEvent(GameEventType.NETWORK_MESSAGE, rawMessage))
    }

    override suspend fun cancel() {
        announcementJob.cancel()
        updaterJob.cancel()
        eventJob.cancel()
    }
}

private class PlayerInfo(
    val id: Int,
    var knownMsgSeq: Long,
    val name: String,
    var nodeRole: NodeRole,
)

private class ViewerInfo(
    val id: Int,
    var knownMsgSeq: Long,
)
