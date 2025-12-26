package onlinesnake.controller.gamestate

import kotlinx.coroutines.channels.Channel
import onlinesnake.controller.dto.MessageWithType
import onlinesnake.dto.GameConfig
import onlinesnake.net.dto.common.SourceHost

fun interface ViewerUpgrader {
    suspend fun upgradeToViewer(
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
        eventChannel: Channel<GameEvent>,
    )
}