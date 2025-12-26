package onlinesnake.net.dto

import onlinesnake.net.dto.common.SourceHost
import onlinesnake.net.dto.common.GameMessageInfo

data class MsgDiscover(
    val sourceHost: SourceHost,
    val gameMessageInfo: GameMessageInfo,
)
