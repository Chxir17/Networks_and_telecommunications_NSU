package onlinesnake.net.impl

import onlinesnake.net.SerializedMessage
import onlinesnake.net.RawMessage
import onlinesnake.net.SuspendMessageDeserializer
import onlinesnake.net.SuspendMessageSerializer
import onlinesnake.net.dto.*
import onlinesnake.dto.Direction as NetDirection
import onlinesnake.net.dto.common.NodeRole
import onlinesnake.net.dto.common.PlayerType
import onlinesnake.net.dto.common.SourceHost
import onlinesnake.proto.*
import onlinesnake.dto.SnakeState as NetSnakeState

class ProtobufSuspendMessageSerializerDeserializer : SuspendMessageSerializer, SuspendMessageDeserializer {
    override suspend fun deserialize(bytes: ByteArray, sourceHost: SourceHost): RawMessage {
        return ProtobufRawMessage(bytes, sourceHost)
    }

    override suspend fun deserialize(serializedMessage: SerializedMessage): RawMessage {
        return deserialize(serializedMessage.bytes, serializedMessage.sourceHost)
    }

    override suspend fun serializeAnnouncement(msgAnnouncement: MsgAnnouncement): ByteArray {
        val announcementMsg = GameMessageKt.announcementMsg {
            this.games.addAll(msgAnnouncement.gameAnnouncementList.map { announcement ->
                gameAnnouncement {
                    players = gamePlayers { }
                    players.playersList += announcement.playerList.map { p ->
                        gamePlayer {
                            this.name = p.name
                            this.id = p.id
                            this.role = netNodeRoleToProtobufNodeRole(p.nodeRole)
                            this.score = p.score
                            if (p.hasSourceHost) {
                                this.ipAddress = p.sourceHost.ip
                                this.port = p.sourceHost.port
                            }
                        }
                    }
                    config = gameConfig {
                        height = announcement.gameConfig.height
                        width = announcement.gameConfig.width
                        foodStatic = announcement.gameConfig.foodStatic
                        stateDelayMs = announcement.gameConfig.stateDelayMs
                    }
                    canJoin = announcement.canJoin
                    gameName = announcement.gameName
                }
            })
        }
        val gameMessage = gameMessage {
            this.announcement = announcementMsg
            this.msgSeq = msgAnnouncement.gameMessageInfo.msgSeq
            if (msgAnnouncement.gameMessageInfo.hasSenderID) {
                this.senderId = msgAnnouncement.gameMessageInfo.senderID
            }
            if (msgAnnouncement.gameMessageInfo.hasReceiverId) {
                this.receiverId = msgAnnouncement.gameMessageInfo.receiverID
            }
        }
        return gameMessage.toByteArray()
    }

    override suspend fun serializePing(msgPing: MsgPing): ByteArray {
        val pingMsg = GameMessageKt.pingMsg {}
        val gameMessage = gameMessage {
            this.ping = pingMsg
            this.msgSeq = msgPing.gameMessageInfo.msgSeq
            if (msgPing.gameMessageInfo.hasSenderID) {
                this.senderId = msgPing.gameMessageInfo.senderID
            }
            if (msgPing.gameMessageInfo.hasReceiverId) {
                this.receiverId = msgPing.gameMessageInfo.receiverID
            }
        }
        return gameMessage.toByteArray()
    }

    override suspend fun serializeSteer(msgSteer: MsgSteer): ByteArray {
        val steerMsg = GameMessageKt.steerMsg {
            this.direction = netDirectionToProtobufDirection(msgSteer.newDirection)
        }
        val gameMessage = gameMessage {
            this.steer = steerMsg
            this.msgSeq = msgSteer.gameMessageInfo.msgSeq
            if (msgSteer.gameMessageInfo.hasSenderID) {
                this.senderId = msgSteer.gameMessageInfo.senderID
            }
            if (msgSteer.gameMessageInfo.hasReceiverId) {
                this.receiverId = msgSteer.gameMessageInfo.receiverID
            }
        }
        return gameMessage.toByteArray()
    }

    override suspend fun serializeJoin(msgJoin: MsgJoin): ByteArray {
        val joinMsg = GameMessageKt.joinMsg {
            this.gameName = msgJoin.gameName
            this.requestedRole = netNodeRoleToProtobufNodeRole(msgJoin.requestedRole)
            this.playerName = msgJoin.playerName
            this.playerType = netPlayerTypeToProtobufPlayerType(msgJoin.playerType)
        }
        val gameMessage = gameMessage {
            this.join = joinMsg
            this.msgSeq = msgJoin.gameMessageInfo.msgSeq
            if (msgJoin.gameMessageInfo.hasSenderID) {
                this.senderId = msgJoin.gameMessageInfo.senderID
            }
            if (msgJoin.gameMessageInfo.hasReceiverId) {
                this.receiverId = msgJoin.gameMessageInfo.receiverID
            }
        }
        return gameMessage.toByteArray()
    }

    @Throws(IllegalArgumentException::class)
    override suspend fun serializeRoleChange(msgRoleChange: MsgRoleChange): ByteArray {
        if (!msgRoleChange.gameMessageInfo.hasSenderID || !msgRoleChange.gameMessageInfo.hasReceiverId) {
            throw IllegalArgumentException("Expected sender ID and receiver ID")
        }
        val roleChangeMsg = GameMessageKt.roleChangeMsg {
            if (msgRoleChange.hasReceiverRole) {
                this.receiverRole = netNodeRoleToProtobufNodeRole(msgRoleChange.receiverRole)
            }
            if (msgRoleChange.hasSenderRole) {
                this.senderRole = netNodeRoleToProtobufNodeRole(msgRoleChange.senderRole)
            }
        }
        val gameMessage = gameMessage {
            this.roleChange = roleChangeMsg
            this.msgSeq = msgRoleChange.gameMessageInfo.msgSeq
            this.senderId = msgRoleChange.gameMessageInfo.senderID
            this.receiverId = msgRoleChange.gameMessageInfo.receiverID
        }
        return gameMessage.toByteArray()
    }

    override suspend fun serializeError(msgError: MsgError): ByteArray {
        val errorMsg = GameMessageKt.errorMsg {
            this.errorMessage = msgError.errorMessage
        }
        val gameMessage = gameMessage {
            this.error = errorMsg
            this.msgSeq = msgError.gameMessageInfo.msgSeq
            if (msgError.gameMessageInfo.hasReceiverId) {
                this.receiverId = msgError.gameMessageInfo.receiverID
            }
            if (msgError.gameMessageInfo.hasSenderID) {
                this.senderId = msgError.gameMessageInfo.senderID
            }
        }
        return gameMessage.toByteArray()
    }

    @Throws(IllegalArgumentException::class)
    override suspend fun serializeAck(msgAck: MsgAck): ByteArray {
        if (!msgAck.gameMessageInfo.hasSenderID || !msgAck.gameMessageInfo.hasReceiverId) {
            throw IllegalArgumentException("Expected sender ID and receiver ID")
        }
        val ackMsg = GameMessageKt.ackMsg { }
        val gameMessage = gameMessage {
            this.ack = ackMsg
            this.msgSeq = msgAck.gameMessageInfo.msgSeq
            this.receiverId = msgAck.gameMessageInfo.receiverID
            this.senderId = msgAck.gameMessageInfo.senderID
        }
        return gameMessage.toByteArray()
    }

    override suspend fun serializeState(msgState: MsgState): ByteArray {
        val stateMsg = GameMessageKt.stateMsg {
            this.state = gameState {
                this.stateOrder = msgState.stateOrder
                this.snakes += msgState.snakeList.map { snake ->
                    GameStateKt.snake {
                        this.playerId = snake.playerID
                        this.points += snake.pointList.map {
                            GameStateKt.coord {
                                this.x = it.x
                                this.y = it.y
                            }
                        }
                        this.state = netSnakeStateToProtobufSnakeState(snake.snakeState)
                        this.headDirection = netDirectionToProtobufDirection(snake.headDirection)
                    }
                }
                this.foods += msgState.foodList.map {
                    GameStateKt.coord {
                        this.x = it.x
                        this.y = it.y
                    }
                }
                this.players = gamePlayers {
                    this.players += msgState.playerList.map { player ->
                        gamePlayer {
                            this.name = player.name
                            this.id = player.id
                            if (player.hasSourceHost) {
                                this.ipAddress = player.sourceHost.ip
                                this.port = player.sourceHost.port
                            }
                            this.role = netNodeRoleToProtobufNodeRole(player.nodeRole)
                            this.type = netPlayerTypeToProtobufPlayerType(player.playerType)
                            this.score = player.score
                        }
                    }
                }
            }
        }
        val gameMessage = gameMessage {
            this.state = stateMsg
            this.msgSeq = msgState.gameMessageInfo.msgSeq
            if (msgState.gameMessageInfo.hasReceiverId) {
                this.receiverId = msgState.gameMessageInfo.receiverID
            }
            if (msgState.gameMessageInfo.hasSenderID) {
                this.senderId = msgState.gameMessageInfo.senderID
            }
        }
        return gameMessage.toByteArray()
    }

    override suspend fun serializeDiscover(msgDiscover: MsgDiscover): ByteArray {
        val discoverMsg = GameMessageKt.discoverMsg { }
        val gameMessage = gameMessage {
            this.discover = discoverMsg
            this.msgSeq = msgDiscover.gameMessageInfo.msgSeq
            if (msgDiscover.gameMessageInfo.hasReceiverId) {
                this.receiverId = msgDiscover.gameMessageInfo.receiverID
            }
            if (msgDiscover.gameMessageInfo.hasSenderID) {
                this.senderId = msgDiscover.gameMessageInfo.senderID
            }
        }
        return gameMessage.toByteArray()
    }

    private fun netNodeRoleToProtobufNodeRole(netNodeRole: NodeRole): OnlineSnakeProto.NodeRole {
        return when (netNodeRole) {
            NodeRole.VIEWER -> OnlineSnakeProto.NodeRole.VIEWER
            NodeRole.DEPUTY -> OnlineSnakeProto.NodeRole.DEPUTY
            NodeRole.MASTER -> OnlineSnakeProto.NodeRole.MASTER
            NodeRole.NORMAL -> OnlineSnakeProto.NodeRole.NORMAL
        }
    }

    private fun netDirectionToProtobufDirection(netDirection: NetDirection): OnlineSnakeProto.Direction {
        return when (netDirection) {
            NetDirection.DOWN -> OnlineSnakeProto.Direction.DOWN
            NetDirection.LEFT -> OnlineSnakeProto.Direction.LEFT
            NetDirection.RIGHT -> OnlineSnakeProto.Direction.RIGHT
            NetDirection.UP -> OnlineSnakeProto.Direction.UP
        }
    }

    private fun netPlayerTypeToProtobufPlayerType(netPlayerType: PlayerType): OnlineSnakeProto.PlayerType {
        return when (netPlayerType) {
            PlayerType.ROBOT -> OnlineSnakeProto.PlayerType.ROBOT
            PlayerType.HUMAN -> OnlineSnakeProto.PlayerType.HUMAN
        }
    }

    private fun netSnakeStateToProtobufSnakeState(netSnakeState: NetSnakeState): OnlineSnakeProto.GameState.Snake.SnakeState {
        return when (netSnakeState) {
            NetSnakeState.ZOMBIE -> OnlineSnakeProto.GameState.Snake.SnakeState.ZOMBIE
            NetSnakeState.ALIVE -> OnlineSnakeProto.GameState.Snake.SnakeState.ALIVE
        }
    }
}