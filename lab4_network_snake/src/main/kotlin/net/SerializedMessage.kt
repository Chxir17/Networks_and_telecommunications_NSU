package onlinesnake.net

import onlinesnake.net.dto.common.SourceHost

data class SerializedMessage(
    val bytes: ByteArray,
    val sourceHost: SourceHost
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SerializedMessage

        if (!bytes.contentEquals(other.bytes)) return false
        if (sourceHost != other.sourceHost) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + sourceHost.hashCode()
        return result
    }
}
