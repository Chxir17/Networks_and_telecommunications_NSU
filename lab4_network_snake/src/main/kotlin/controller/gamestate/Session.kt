package onlinesnake.controller.gamestate

import arrow.atomic.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import onlinesnake.controller.dto.MessageWithType
import onlinesnake.controller.proxy.MessagesProxy
import onlinesnake.controller.proxy.ProxyEventType
import onlinesnake.dto.Coord
import onlinesnake.dto.Direction
import onlinesnake.dto.GameConfig
import onlinesnake.dto.Snake
import onlinesnake.model.IDWithScore
import onlinesnake.net.*
import onlinesnake.net.dto.common.Player
import onlinesnake.net.dto.common.SourceHost
import onlinesnake.ui.GameUI
import java.util.concurrent.atomic.AtomicReference

class Session(
    messageSerializer: SuspendMessageSerializer,
    messageDeserializer: SuspendMessageDeserializer,
    messageSender: SuspendMessageSender,
    messageReader: SuspendMessageReader,
    private val ioCoroutineScope: CoroutineScope,
    private val defaultCoroutineScope: CoroutineScope,
    private val gameUI: GameUI,
    gameConfig: GameConfig,
    playerName: String,
    gameName: String,
    masterIP: String,
    masterPort: Int,
    multicastIP: String,
    multicastPort: Int,
    startServer: Boolean,
    private val sessionGameExiter: GameExiter,
) : GameExiter, DeputyUpgrader, MasterUpgrader, ViewerUpgrader {
    private val messagesProxy = MessagesProxy(
        messageDeserializer = messageDeserializer,
        messageSerializer = messageSerializer,
        messageSender = messageSender,
        messageReader = messageReader,
        coroutineScope = ioCoroutineScope,
        stateDelayMs = gameConfig.stateDelayMs.toLong(),
    )

    private val multicastSourceHost = SourceHost(multicastIP, multicastPort)

    private val gameState = AtomicReference(if (startServer) {
        MasterGameState(
            gameConfig = gameConfig,
            gameUI = gameUI,
            ioCoroutineScope = ioCoroutineScope,
            defaultCoroutineScope = defaultCoroutineScope,
            multicastSourceHost = multicastSourceHost,
            gameName = gameName,
            masterPlayerName = playerName,
            snakeList = listOf(),
            foodList = listOf(),
            scores = listOf(),
            outComingConfirmMessageChannel = Channel(Channel.UNLIMITED),
            outComingNoConfirmMessageChannel = Channel(Channel.UNLIMITED),
            initMsgSeq = 0,
            stateOrder = 0,
            eventChannel = Channel(Channel.UNLIMITED),
            netPlayersList = listOf(),
            initID = null,
            gameExiter = this,
            viewerUpgrader = this,
        )
    } else {
        PlayerGameState(
            masterSourceHost = SourceHost(masterIP, masterPort),
            gameName = gameName,
            playerName = playerName,
            gameUI = gameUI,
            defaultCoroutineScope = defaultCoroutineScope,
            gameConfig = gameConfig,
            gameExiter = this,
            deputyUpgrader = this,
            viewerUpgrader = this
        )
    })

    private val gameStateListenerJob = defaultCoroutineScope.launch {
        while (true) {
            select {
                gameState.get().outComingConfirmMessageReceiveChannel.onReceive {
                    messagesProxy.sendConfirmMessage(it)
                }
                gameState.get().outComingNoConfirmMessageReceiveChannel.onReceive {
                    messagesProxy.sendDirect(it)
                }
            }
        }
    }

    private val proxyListenerJob = defaultCoroutineScope.launch {
        while (true) {
            val proxyEvent = messagesProxy.proxyEventReceiveChannel.receive()
            when (proxyEvent.type) {
                ProxyEventType.NETWORK_MESSAGE -> {
                    gameState.get().networkMessage(proxyEvent.attachment as RawMessage)
                }
                ProxyEventType.MAKE_PING -> {
                    gameState.get().ping(proxyEvent.attachment as SourceHost)
                }
                ProxyEventType.CONNECTION_LOST -> {
                    gameState.get().connectionLost(proxyEvent.attachment as SourceHost)
                }
            }
        }
    }

    suspend fun announce(sourceHost: SourceHost) {
        gameState.get().announce(sourceHost)
    }

    suspend fun newDirection(direction: Direction) {
        println("SESSION DIRECTION ${direction.name}")
        gameState.get().newDirection(direction)
    }

    suspend fun cancel() {
        gameStateListenerJob.cancel()
        proxyListenerJob.cancel()
        gameState.get().cancel()
        messagesProxy.cancel()
    }

    override suspend fun exitGame() {
        sessionGameExiter.exitGame()
    }

    override suspend fun upgradeToDeputy(
        masterSourceHost: SourceHost,
        gameName: String,
        playerName: String,
        gameConfig: GameConfig,
        outComingConfirmMessageChannel: Channel<MessageWithType>,
        outComingNoConfirmMessageChannel: Channel<MessageWithType>,
        msgSeq: Long,
        masterID: Int,
        nodeID: Int,
        knownStateOrder: Int,
        eventChannel: Channel<GameEvent>
    ) {
        gameState.update {
            it.cancel()
            DeputyGameState(
                masterSourceHost,
                gameUI,
                gameConfig,
                defaultCoroutineScope,
                outComingConfirmMessageChannel,
                outComingNoConfirmMessageChannel,
                knownStateOrder,
                msgSeq,
                masterID,
                nodeID,
                gameName,
                playerName,
                eventChannel,
                this,
                this,
                this,
            )
        }
    }

    override suspend fun upgradeToMaster(
        gameConfig: GameConfig,
        gameName: String,
        playerName: String,
        snakeList: List<Snake>,
        foodList: List<Coord>,
        scores: List<IDWithScore>,
        outComingConfirmMessageChannel: Channel<MessageWithType>,
        outComingNoConfirmMessageChannel: Channel<MessageWithType>,
        initMsgSeq: Long,
        stateOrder: Int,
        eventChannel: Channel<GameEvent>,
        netPlayersList: List<Player>,
        id: Int
    ) {

        gameState.update {
            it.cancel()
            MasterGameState(
                gameUI,
                gameConfig,
                ioCoroutineScope,
                defaultCoroutineScope,
                multicastSourceHost,
                gameName,
                playerName,
                snakeList,
                foodList,
                scores,
                outComingConfirmMessageChannel,
                outComingNoConfirmMessageChannel,
                initMsgSeq,
                stateOrder,
                eventChannel,
                netPlayersList,
                id,
                this,
                this,
            )
        }
    }

    override suspend fun upgradeToViewer(
        masterSourceHost: SourceHost,
        deputySourceHost: SourceHost?,
        gameConfig: GameConfig,
        outComingConfirmMessageChannel: Channel<MessageWithType>,
        outComingNoConfirmMessageChannel: Channel<MessageWithType>,
        initMsgSeq: Long,
        knownStateOrder: Int,
        masterID: Int,
        deputyID: Int,
        initViewerID: Int,
        viewerName: String,
        gameName: String,
        eventChannel: Channel<GameEvent>
    ) {
        gameState.update {
            it.cancel()
            ViewerGameState(
                masterSourceHost,
                deputySourceHost,
                gameUI,
                gameConfig,
                defaultCoroutineScope,
                outComingConfirmMessageChannel,
                outComingNoConfirmMessageChannel,
                knownStateOrder,
                initMsgSeq,
                masterID,
                deputyID,
                initViewerID,
                viewerName,
                gameName,
                eventChannel,
                this,
            )
        }
    }
}