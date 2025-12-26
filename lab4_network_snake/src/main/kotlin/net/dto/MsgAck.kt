package onlinesnake.net.dto

import onlinesnake.net.dto.common.GameMessageInfo
import onlinesnake.net.dto.common.SourceHost

data class MsgAck(
    val sourceHost: SourceHost,
    val gameMessageInfo: GameMessageInfo,
)
