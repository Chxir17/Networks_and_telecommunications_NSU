package onlinesnake.net.dto

import onlinesnake.dto.Direction
import onlinesnake.net.dto.common.GameMessageInfo
import onlinesnake.net.dto.common.SourceHost

data class MsgSteer (
    val sourceHost: SourceHost,
    val gameMessageInfo: GameMessageInfo,
    val newDirection: Direction,
)