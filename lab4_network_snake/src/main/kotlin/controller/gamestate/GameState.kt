package onlinesnake.controller.gamestate

import kotlinx.coroutines.channels.ReceiveChannel
import onlinesnake.controller.dto.MessageWithType
import onlinesnake.dto.Direction
import onlinesnake.net.RawMessage
import onlinesnake.net.dto.common.SourceHost

interface GameState {
    suspend fun newDirection(direction: Direction)
    suspend fun exit()
    suspend fun ping(sourceHost: SourceHost)
    suspend fun announce(sourceHost: SourceHost)
    suspend fun connectionLost(sourceHost: SourceHost)
    suspend fun networkMessage(rawMessage: RawMessage)
    suspend fun cancel()
    val outComingConfirmMessageReceiveChannel: ReceiveChannel<MessageWithType>
    val outComingNoConfirmMessageReceiveChannel: ReceiveChannel<MessageWithType>
}