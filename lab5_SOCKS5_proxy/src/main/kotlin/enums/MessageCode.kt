package enums

enum class MessageCode(val b: Byte) {
    _0(0),
    ESTABLISH_TCP(1),
    BIND_PORT(2),
    ASSOCIATE_UDP_PORT(3);

    companion object {
        fun fromByte(v: Byte) = entries.firstOrNull { it.b == v } ?: _0
    }
}