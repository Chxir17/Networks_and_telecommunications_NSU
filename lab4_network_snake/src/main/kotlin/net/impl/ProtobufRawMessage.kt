package onlinesnake.net.impl

import onlinesnake.dto.GameConfig
import onlinesnake.dto.Snake
import onlinesnake.net.dto.common.SourceHost
import onlinesnake.net.RawMessage
import onlinesnake.net.dto.*
import onlinesnake.net.dto.common.*
import onlinesnake.net.dto.common.Player
import onlinesnake.proto.OnlineSnakeProto
import onlinesnake.proto.OnlineSnakeProto.Direction as ProtobufDirection
import onlinesnake.dto.Direction as NetDirection
import onlinesnake.proto.OnlineSnakeProto.GameState.Snake.SnakeState as ProtobufSnakeState
import onlinesnake.dto.SnakeState as NetSnakeState
import onlinesnake.proto.OnlineSnakeProto.NodeRole as ProtobufNodeRole
import onlinesnake.net.dto.common.NodeRole as NetNodeRole
import onlinesnake.proto.OnlineSnakeProto.GamePlayer as ProtobufPlayer
import onlinesnake.net.dto.common.Player as NetPlayer
import onlinesnake.proto.OnlineSnakeProto.PlayerType as ProtobufPlayerType
import onlinesnake.net.dto.common.PlayerType as NetPlayerType
import onlinesnake.dto.Coord as NetCoord

class ProtobufRawMessage(
    bytes: ByteArray,
    override val sourceHost: SourceHost
) : RawMessage {
    private val gameMessage = OnlineSnakeProto.GameMessage.parseFrom(bytes)

    override val gameMessageInfo = GameMessageInfo(
        gameMessage.msgSeq,
        gameMessage.senderId,
        gameMessage.receiverId,
        gameMessage.hasSenderId(),
        gameMessage.hasReceiverId()
    )

    override val type: MessageType
        get() = if (gameMessage.hasPing()) {
            MessageType.PING
        } else if (gameMessage.hasSteer()) {
            MessageType.STEER
        } else if (gameMessage.hasAck() && gameMessage.hasSenderId() && gameMessage.hasReceiverId()) {
            MessageType.ACK
        } else if (gameMessage.hasState()) {
            MessageType.STATE
        } else if (gameMessage.hasAnnouncement()) {
            MessageType.ANNOUNCEMENT
        } else if (gameMessage.hasJoin()) {
            MessageType.JOIN
        } else if (gameMessage.hasError()) {
            MessageType.ERROR
        } else if (gameMessage.hasRoleChange() && gameMessage.hasSenderId() && gameMessage.hasReceiverId()) {
            MessageType.ROLE_CHANGE
        } else if (gameMessage.hasDiscover()) {
            MessageType.DISCOVER
        } else {
            MessageType.UNKNOWN
        }

    override fun getAsPing(): MsgPing {
        return MsgPing(
            sourceHost,
            gameMessageInfo
        )
    }

    override fun getAsSteer(): MsgSteer {
        val steer = gameMessage.steer
        return MsgSteer(
            sourceHost,
            gameMessageInfo,
            protobufDirectionToNetDirection(steer.direction)
        )
    }

    override fun getAsAck(): MsgAck {
        return MsgAck(
            sourceHost,
            gameMessageInfo,
        )
    }

    override fun getAsState(): MsgState {
        val state: OnlineSnakeProto.GameState = gameMessage.state.state
        val snakeList = state.snakesList.map {
            Snake(
                playerID = it.playerId,
                pointList = it.pointsList.map { protoCoord ->
                    NetCoord(
                        x = if (protoCoord.hasX()) protoCoord.x else 0,
                        y = if (protoCoord.hasY()) protoCoord.y else 0,
                    )
                },
                snakeState = protobufSnakeStateToNetSnakeState(it.state),
                headDirection = protobufDirectionToNetDirection(it.headDirection)
            )
        }
        val foodList = state.foodsList.map {
            NetCoord(it.x, it.y)
        }
        return MsgState(
            sourceHost,
            gameMessageInfo,
            state.stateOrder,
            snakeList,
            foodList,
            mapProtobufPlayersListToNetPlayersList(state.players.playersList),
        )
    }

    override fun getAsAnnouncement(): MsgAnnouncement {
        val announcement = gameMessage.announcement
        val gameAnnouncementList = announcement.gamesList.map {
            GameAnnouncement(
                playerList = mapProtobufPlayersListToNetPlayersList(it.players.playersList),
                gameConfig = GameConfig(
                    width = if (it.config.hasWidth()) it.config.width else 40,
                    height = if (it.config.hasHeight()) it.config.height else 30,
                    foodStatic = if (it.config.hasFoodStatic()) it.config.foodStatic else 1,
                    stateDelayMs = if (it.config.hasStateDelayMs()) it.config.stateDelayMs else 1000,
                ),
                canJoin = if (it.hasCanJoin()) it.canJoin else true,
                gameName = it.gameName,
            )
        }
        return MsgAnnouncement(
            sourceHost = sourceHost,
            gameMessageInfo = gameMessageInfo,
            gameAnnouncementList = gameAnnouncementList,
        )
    }

    override fun getAsJoin(): MsgJoin {
        val join = gameMessage.join
        return MsgJoin(
            sourceHost = sourceHost,
            gameMessageInfo = gameMessageInfo,
            playerType = protobufPlayerTypeToNetPlayerType(join.playerType),
            playerName = join.playerName,
            gameName = join.gameName,
            requestedRole = protobufNodeRoleToNetNodeRole(join.requestedRole),
        )
    }

    override fun getAsError(): MsgError {
        val error = gameMessage.error
        return MsgError(
            sourceHost = sourceHost,
            gameMessageInfo = gameMessageInfo,
            errorMessage = error.errorMessage,
        )
    }

    override fun getAsRoleChange(): MsgRoleChange {
        val roleChange = gameMessage.roleChange
        return MsgRoleChange(
            sourceHost = sourceHost,
            gameMessageInfo = gameMessageInfo,
            senderRole = if (roleChange.hasSenderRole()) protobufNodeRoleToNetNodeRole(roleChange.senderRole) else NetNodeRole.VIEWER,
            receiverRole = if (roleChange.hasReceiverRole()) protobufNodeRoleToNetNodeRole(roleChange.receiverRole) else NetNodeRole.VIEWER,
            hasSenderRole = roleChange.hasSenderRole(),
            hasReceiverRole = roleChange.hasReceiverRole(),
        )
    }

    override fun getAsDiscover(): MsgDiscover {
        return MsgDiscover(
            sourceHost = sourceHost,
            gameMessageInfo = gameMessageInfo,
        )
    }

    private fun protobufDirectionToNetDirection(protobufDirection: ProtobufDirection): NetDirection {
        return when (protobufDirection) {
            ProtobufDirection.UP -> NetDirection.UP
            ProtobufDirection.LEFT -> NetDirection.LEFT
            ProtobufDirection.DOWN -> NetDirection.DOWN
            ProtobufDirection.RIGHT -> NetDirection.RIGHT
        }
    }

    private fun protobufSnakeStateToNetSnakeState(protobufSnakeState: ProtobufSnakeState): NetSnakeState {
        return when (protobufSnakeState) {
            ProtobufSnakeState.ALIVE -> NetSnakeState.ALIVE
            ProtobufSnakeState.ZOMBIE -> NetSnakeState.ZOMBIE
        }
    }

    private fun protobufNodeRoleToNetNodeRole(protobufNodeRole: ProtobufNodeRole): NetNodeRole {
        return when (protobufNodeRole) {
            ProtobufNodeRole.VIEWER -> NetNodeRole.VIEWER
            ProtobufNodeRole.NORMAL -> NetNodeRole.NORMAL
            ProtobufNodeRole.DEPUTY -> NetNodeRole.DEPUTY
            ProtobufNodeRole.MASTER -> NetNodeRole.MASTER
        }
    }

    private fun mapProtobufPlayersListToNetPlayersList(protobufPlayerList: List<ProtobufPlayer>): List<NetPlayer> {
        return protobufPlayerList.map {
            Player(
                sourceHost = if (it.hasIpAddress()) {
                    SourceHost(it.ipAddress, it.port)
                } else {
                    SourceHost("", 0)
                },
                hasSourceHost = it.hasIpAddress(),
                id = it.id,
                nodeRole = protobufNodeRoleToNetNodeRole(it.role),
                score = it.score,
                name = it.name,
                playerType = protobufPlayerTypeToNetPlayerType(it.type)
            )
        }
    }
    private fun protobufPlayerTypeToNetPlayerType(protobufPlayerType: ProtobufPlayerType): NetPlayerType {
        return when (protobufPlayerType) {
            ProtobufPlayerType.HUMAN -> NetPlayerType.HUMAN
            ProtobufPlayerType.ROBOT -> NetPlayerType.ROBOT
        }
    }
}