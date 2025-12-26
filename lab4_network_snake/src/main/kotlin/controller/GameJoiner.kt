package onlinesnake.controller

import onlinesnake.net.dto.common.SourceHost
import onlinesnake.ui.dto.AvailableGameDto

fun interface GameJoiner {
    fun joinGame(availableGameDto: AvailableGameDto, playerName: String, masterSourceHost: SourceHost)
}