package onlinesnake.controller.dto

import onlinesnake.net.dto.MessageType
import onlinesnake.net.dto.common.SourceHost

data class MessageWithType(
    val messageType: MessageType,
    val sourceHost: SourceHost,
    val message: Any,
)
