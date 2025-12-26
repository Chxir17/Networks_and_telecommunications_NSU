package onlinesnake.net.dto

import onlinesnake.net.dto.common.SourceHost
import onlinesnake.net.dto.common.*

data class MsgAnnouncement(
    val sourceHost: SourceHost,
    val gameMessageInfo: GameMessageInfo,
    val gameAnnouncementList: List<GameAnnouncement>
)

