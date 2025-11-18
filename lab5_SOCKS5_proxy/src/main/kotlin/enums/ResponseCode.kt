package enums

enum class ResponseCode(val b: Byte) {
    REQUEST_GRANTED(0),
    GENERAL_FAILURE(1),
    NOT_ALLOWED_BY_RULESET(2),
    NETWORK_UNREACHABLE(3),
    HOST_UNREACHABLE(4),
    CONNECTION_REFUSED_BY_DEST_HOST(5),
    COMMAND_NOT_SUPPORTED(6),
    PROTOCOL_ERROR((-1).toByte());

    fun toByte(): Byte = b
}