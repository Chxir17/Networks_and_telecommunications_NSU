package proxy.connectionHandler.responser

import entities.ClientMessage
import entities.ServerResponse
import enums.AddressType
import enums.ResponseCode
import enums.SocksVersion
import proxy.ByteSliceMessageSource

class SocksResponseSender(private val messageSource: ByteSliceMessageSource) {

    fun send(code: ResponseCode, addrType: AddressType, ip: ByteArray, port: Int) {
        val response = ServerResponse(
            SocksVersion.SOCKS5,
            code,
            addrType,
            ip,
            port
        )
        messageSource.writeServerAnswer(response)
    }

    fun granted(ip: ByteArray, type: AddressType, port: Int) =
        send(
            ResponseCode.REQUEST_GRANTED,
            type,
            ip,
            port
        )

    fun generalFailure(ip: ByteArray, type: AddressType, port: Int) =
        send(
            ResponseCode.GENERAL_FAILURE,
            type,
            ip,
            port
        )

    fun hostUnreachable(client: ClientMessage) =
        send(
            ResponseCode.HOST_UNREACHABLE, client.addressType, client.addressPayload, client.port
        )

    fun connectionRefused(client: ClientMessage) =
        send(
            ResponseCode.CONNECTION_REFUSED_BY_DEST_HOST,
            client.addressType,
            client.addressPayload,
            client.port
        )

    fun unsupported(client: ClientMessage) =
        send(
            ResponseCode.COMMAND_NOT_SUPPORTED,
            client.addressType,
            client.addressPayload,
            client.port
        )

    fun protocolError(ip: ByteArray, type: AddressType, port: Int) =
        send(
            ResponseCode.PROTOCOL_ERROR,
            type,
            ip,
            port
        )
}
