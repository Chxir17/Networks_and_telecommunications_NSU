package onlinesnake.controller.gamestate

import kotlinx.coroutines.channels.Channel
import onlinesnake.controller.dto.MessageWithType
import onlinesnake.dto.Coord
import onlinesnake.dto.GameConfig
import onlinesnake.dto.Snake
import onlinesnake.model.IDWithScore
import onlinesnake.net.dto.common.Player

fun interface MasterUpgrader {
    suspend fun upgradeToMaster(
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
        id: Int,
    )
}