package onlinesnake.controller.gamestate

data class GameEvent(
    val eventType: GameEventType,
    val attachment: Any
)
