package onlinesnake.net.dto

enum class MessageType {
    PING,
    STEER,
    ACK,
    STATE,
    ANNOUNCEMENT,
    JOIN,
    ERROR,
    ROLE_CHANGE,
    DISCOVER,
    UNKNOWN
}