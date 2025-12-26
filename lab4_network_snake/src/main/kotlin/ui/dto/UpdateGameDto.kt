package onlinesnake.ui.dto

import onlinesnake.dto.Coord
import onlinesnake.dto.GameConfig
import onlinesnake.dto.Snake

data class UpdateGameDto(
    val stateOrder: Int,
    val snakesList: List<Snake>,
    val foodList: List<Coord>,
    val players: List<Player>,
    val gameConfig: GameConfig,
    val myID: Int,
    val masterName: String,
)
