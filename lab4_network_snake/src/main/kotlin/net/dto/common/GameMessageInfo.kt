package onlinesnake.net.dto.common

data class GameMessageInfo(
    val msgSeq: Long,
    val senderID: Int,
    val receiverID: Int,
    val hasSenderID: Boolean,
    val hasReceiverId: Boolean,
)
