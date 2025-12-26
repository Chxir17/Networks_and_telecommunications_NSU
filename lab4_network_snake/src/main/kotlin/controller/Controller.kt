package onlinesnake.controller

import arrow.atomic.update
import kotlinx.coroutines.*
import onlinesnake.controller.gamestate.GameExiter
import onlinesnake.controller.gamestate.Session
import onlinesnake.dto.Direction
import onlinesnake.dto.GameConfig
import onlinesnake.net.*
import onlinesnake.net.dto.common.SourceHost
import onlinesnake.net.impl.ProtobufJavaMulticastUDPSuspendMessageSenderReader
import onlinesnake.net.impl.ProtobufKtorUDPSuspendMessageSenderReader
import onlinesnake.net.impl.ProtobufSuspendMessageSerializerDeserializer
import onlinesnake.ui.ExitListener
import onlinesnake.ui.GameUI
import onlinesnake.ui.NewDirectionListener
import onlinesnake.ui.NewGameListener
import onlinesnake.ui.dto.AvailableGameDto
import onlinesnake.ui.impl.GameFrame
import java.util.concurrent.atomic.AtomicReference

class Controller : NewGameListener, ExitListener, NewDirectionListener, GameJoiner, GameAnnouncer, GameExiter {
    private val gameUI: GameUI = GameFrame()
    private val multicastIP = "239.192.0.4"
//    private val multicastIP = "224.0.0.5"
    private val multicastPort = 9192
    private val messageSerializer: SuspendMessageSerializer
    private val messageDeserializer: SuspendMessageDeserializer
    private val multicastMessageReader: SuspendMessageReader
    private val regularMessageReader: SuspendMessageReader
    private val regularMessageSender: SuspendMessageSender

    private val ioCoroutineScope = CoroutineScope(Dispatchers.IO)
    private val defaultCoroutineScope = CoroutineScope(Dispatchers.Default)

    init {
        val senderReader = ProtobufKtorUDPSuspendMessageSenderReader()
        regularMessageReader = senderReader
        regularMessageSender = senderReader
    }

    init {
        val serializerDeserializer = ProtobufSuspendMessageSerializerDeserializer()
        messageSerializer = serializerDeserializer
        messageDeserializer = serializerDeserializer
    }

    init {
        val senderReader = ProtobufJavaMulticastUDPSuspendMessageSenderReader(multicastIP, multicastPort)
        multicastMessageReader = senderReader
    }

    init {
        gameUI.addNewGameListener(this)
        gameUI.addExitListener(this)
    }

    init {
        gameUI.addWidthValidationRule {
            it in 10..100
        }
        gameUI.addHeightValidationRule {
            it in 10..100
        }
        gameUI.addFoodStaticValidationRule {
            it in 0..100
        }
        gameUI.addStateDelayMsValidationRule {
            it in 100..3000
        }
    }

    init {
        gameUI.addNewDirectionListener(this)
    }

    private val availableGamesServer = AvailableGamesServer(
        messagesReader = multicastMessageReader,
        messagesDeserializer = messageDeserializer,
        gameUI = gameUI,
        ioCoroutineScope = ioCoroutineScope,
        gameJoiner = this,
        gameAnnouncer = this,
    )

    private val atomicSession = AtomicReference<Session?>(null)

    override fun newDirection(direction: Direction) {
        defaultCoroutineScope.launch {
            println(atomicSession.get())
            atomicSession.get()?.newDirection(direction)
        }
    }

    override fun exit() {
        defaultCoroutineScope.launch {
            atomicSession.update {
                it?.cancel()
                null
            }
        }
    }

    fun start() {
        gameUI.start()
    }

    override fun newGame(gameConfig: GameConfig, gameName: String, playerName: String) {
        defaultCoroutineScope.launch {
            atomicSession.update {
                it?.cancel()
                Session(
                    messageSerializer,
                    messageDeserializer,
                    regularMessageSender,
                    regularMessageReader,
                    ioCoroutineScope,
                    defaultCoroutineScope,
                    gameUI,
                    gameConfig,
                    playerName,
                    gameName,
                    "",
                    0,
                    multicastIP,
                    multicastPort,
                    startServer = true,
                ) {
                    println("WHEN I AM NULL>............................................")
                    atomicSession.update { session ->
                        session?.cancel()
                        null
                    }
                }
            }
        }
    }

    override fun announce() {
        defaultCoroutineScope.launch {
            atomicSession.get()?.announce(SourceHost(multicastIP, multicastPort))
        }
    }

    override fun joinGame(availableGameDto: AvailableGameDto, playerName: String, masterSourceHost: SourceHost) {
        defaultCoroutineScope.launch {
            atomicSession.update {
                it?.cancel()
                Session(
                    messageSerializer,
                    messageDeserializer,
                    regularMessageSender,
                    regularMessageReader,
                    ioCoroutineScope,
                    defaultCoroutineScope,
                    gameUI,
                    availableGameDto.gameConfig,
                    playerName,
                    availableGameDto.gameName,
                    masterSourceHost.ip,
                    masterSourceHost.port,
                    multicastIP,
                    multicastPort,
                    startServer = false,
                    sessionGameExiter = this@Controller,
                )
            }
        }
    }

    override suspend fun exitGame() {
        println("WHEN I AM NULL>............................................")
        atomicSession.update {
            defaultCoroutineScope.async {
                it?.cancel()
            }
            null
        }
    }
}
