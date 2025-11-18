package enums

enum class AddressType(val b: Byte) {
    _0(0),
    IPV4(1),
    _2(2),
    DOMAIN_NAME(3),
    IPV6(4);

    companion object {
        fun fromByte(v: Byte) = entries.firstOrNull { it.b == v } ?: _0
    }
}