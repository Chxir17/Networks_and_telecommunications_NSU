package onlinesnake.model

import onlinesnake.dto.Coord
import onlinesnake.dto.Direction
import onlinesnake.dto.Snake
import onlinesnake.dto.SnakeState

data class UpdateGameDto(
    val snakes: List<Snake>,
    val foodsPoint: List<Coord>,
    val idForRemoval: List<Int>
)