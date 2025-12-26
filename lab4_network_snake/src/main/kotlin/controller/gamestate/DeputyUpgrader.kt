package onlinesnake.controller.gamestate

import kotlinx.coroutines.channels.Channel
import onlinesnake.controller.dto.MessageWithType
import onlinesnake.dto.GameConfig
import onlinesnake.net.dto.common.SourceHost

fun interface DeputyUpgrader {
    suspend fun upgradeToDeputy(
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
        eventChannel: Channel<GameEvent>,
    )
}