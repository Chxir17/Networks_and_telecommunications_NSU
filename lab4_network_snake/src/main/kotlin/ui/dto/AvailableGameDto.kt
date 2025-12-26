package onlinesnake.ui.dto

import onlinesnake.dto.GameConfig

data class AvailableGameDto(
    val gameName: String,
    val numOfPlayers: Int,
    val gameConfig: GameConfig,
    val canJoin: Boolean,
)