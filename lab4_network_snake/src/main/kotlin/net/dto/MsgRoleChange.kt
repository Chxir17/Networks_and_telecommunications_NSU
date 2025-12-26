package onlinesnake.net.dto

import onlinesnake.net.dto.common.GameMessageInfo
import onlinesnake.net.dto.common.NodeRole
import onlinesnake.net.dto.common.SourceHost

data class MsgRoleChange(
    val sourceHost: SourceHost,
    val gameMessageInfo: GameMessageInfo,
    val senderRole: NodeRole,
    val receiverRole: NodeRole,
    val hasSenderRole: Boolean,
    val hasReceiverRole: Boolean,
)
