package onlinesnake.net.dto

import onlinesnake.dto.Coord
import onlinesnake.dto.Snake
import onlinesnake.net.dto.common.GameMessageInfo
import onlinesnake.net.dto.common.Player
import onlinesnake.net.dto.common.SourceHost

data class MsgState(
    val sourceHost: SourceHost,
    val gameMessageInfo: GameMessageInfo,
    val stateOrder: Int,
    val snakeList: List<Snake>,
    val foodList: List<Coord>,
    val playerList: List<Player>,
)

