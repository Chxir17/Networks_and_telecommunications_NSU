package onlinesnake.net.dto

import onlinesnake.net.dto.common.*

data class MsgJoin(
    val sourceHost: SourceHost,
    val gameMessageInfo: GameMessageInfo,
    val playerType: PlayerType,
    val playerName: String,
    val gameName: String,
    val requestedRole: NodeRole,
)
