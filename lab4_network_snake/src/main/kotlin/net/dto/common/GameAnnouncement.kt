package onlinesnake.net.dto.common

import onlinesnake.dto.GameConfig

data class GameAnnouncement(
    val playerList: List<Player>,
    val gameConfig: GameConfig,
    val canJoin: Boolean,
    val gameName: String,
)