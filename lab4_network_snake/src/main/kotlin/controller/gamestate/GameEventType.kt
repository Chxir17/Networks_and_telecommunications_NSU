package onlinesnake.controller.gamestate

enum class GameEventType {
    EXIT,
    NETWORK_MESSAGE,
    MAKE_PING,
    CONNECTION_LOST,
    NEW_DIRECTION,
    UPDATE_FIELD
}
