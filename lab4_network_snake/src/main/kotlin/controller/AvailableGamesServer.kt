package onlinesnake.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import onlinesnake.net.SuspendMessageDeserializer
import onlinesnake.net.SuspendMessageReader
import onlinesnake.net.dto.MessageType
import onlinesnake.net.dto.MsgAnnouncement
import onlinesnake.net.dto.common.SourceHost
import onlinesnake.ui.GameUI
import onlinesnake.ui.dto.AvailableGameDto
import onlinesnake.ui.dto.AvailableGameKey
import java.time.Instant
import java.time.temporal.ChronoUnit

class AvailableGamesServer(
    private val messagesReader: SuspendMessageReader,
    private val messagesDeserializer: SuspendMessageDeserializer,
    private val gameUI: GameUI,
    ioCoroutineScope: CoroutineScope,
    private val gameJoiner: GameJoiner,
    private val gameAnnouncer: GameAnnouncer,
) {
    private val announcedGames = mutableMapOf<SourceHost, AnnouncedGameInfo>()
    private val mutex = Mutex()

    private val announcementListener = ioCoroutineScope.launch {
        while (true) {
            val rawMessage = messagesDeserializer.deserialize(messagesReader.read())
            println("ANNOUNCEMENT LISTENER: ${rawMessage.type}")
            when (rawMessage.type) {
                MessageType.ANNOUNCEMENT -> {
                    handleMessageAnnouncement(rawMessage.getAsAnnouncement())
                }
                MessageType.DISCOVER -> {
                    handleMessageDiscover()
                }
                else -> { }
            }
        }
    }

    private val announcementTimeoutVerifier = ioCoroutineScope.launch {
        val secondsDiff = 3
        while (true) {
            delay(1000)
            val now = Instant.now()
            mutex.withLock {
                val iterator = announcedGames.entries.iterator()
                while (iterator.hasNext()) {
                    val announcement = iterator.next()
                    if (ChronoUnit.SECONDS.between(announcement.value.lastAnnounceTime, now) >= secondsDiff) {
                        iterator.remove()
                        gameUI.removeAvailableGame(announcement.value.key)
                    }
                }
            }
        }
    }

    private suspend fun handleMessageAnnouncement(msgAnnouncement: MsgAnnouncement) {
        msgAnnouncement.gameAnnouncementList.forEach { announcement ->
            val availableGameDto = AvailableGameDto(
                gameName = announcement.gameName,
                numOfPlayers = announcement.playerList.size,
                gameConfig = announcement.gameConfig,
                canJoin = announcement.canJoin,
            )
            mutex.withLock {
                val announced = announcedGames[msgAnnouncement.sourceHost]
                if (announced == null) {
                    val key = gameUI.addAvailableGame(availableGameDto) { playerName ->
                        gameJoiner.joinGame(availableGameDto, playerName, msgAnnouncement.sourceHost)
                    }
                    announcedGames[msgAnnouncement.sourceHost] = AnnouncedGameInfo(
                        key,
                        Instant.now(),
                        availableGameDto
                    )
                } else {
                    gameUI.updateAvailableGame(availableGameDto, announced.key)
                    announced.availableGameDto = availableGameDto
                    announced.lastAnnounceTime = Instant.now()
                }
            }
        }
    }

    private fun handleMessageDiscover() {
        gameAnnouncer.announce()
    }
}

private class AnnouncedGameInfo(
    val key: AvailableGameKey,
    var lastAnnounceTime: Instant,
    var availableGameDto: AvailableGameDto,
)