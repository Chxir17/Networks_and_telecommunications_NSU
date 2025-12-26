package onlinesnake.controller.gamestate

import onlinesnake.dto.Direction
import onlinesnake.net.dto.MsgAck
import onlinesnake.net.dto.MsgError
import onlinesnake.net.dto.MsgPing
import onlinesnake.net.dto.MsgSteer
import onlinesnake.net.dto.common.GameMessageInfo
import onlinesnake.net.dto.common.SourceHost

fun makeMsgPing(targetSourceHost: SourceHost, msgSeq: Long) =
    MsgPing(
        sourceHost = targetSourceHost,
        gameMessageInfo = GameMessageInfo(
            msgSeq = msgSeq,
            senderID = 0,
            receiverID = 0,
            hasReceiverId = false,
            hasSenderID = false,
        )
    )

fun makeMsgError(targetSourceHost: SourceHost, requestMsgSeq: Long, message: String) =
    MsgError(
        sourceHost = targetSourceHost,
        gameMessageInfo = GameMessageInfo(
            msgSeq = requestMsgSeq,
            senderID = 0,
            receiverID = 0,
            hasSenderID = false,
            hasReceiverId = false,
        ),
        errorMessage = message
    )

fun makeMsgAck(targetSourceHost: SourceHost, requestMsgSeq: Long, receiverId: Int, senderId: Int) =
    MsgAck(
        sourceHost = targetSourceHost,
        gameMessageInfo = GameMessageInfo(
            msgSeq = requestMsgSeq,
            senderID = senderId,
            receiverID = receiverId,
            hasSenderID = true,
            hasReceiverId = true,
        )
    )

fun makeMsgSteer(targetSourceHost: SourceHost, direction: Direction, msgSeq: Long) =
    MsgSteer(
        sourceHost = targetSourceHost,
        gameMessageInfo = GameMessageInfo(
            msgSeq = msgSeq,
            senderID = 0,
            receiverID = 0,
            hasSenderID = false,
            hasReceiverId = false,
        ),
        direction
    )