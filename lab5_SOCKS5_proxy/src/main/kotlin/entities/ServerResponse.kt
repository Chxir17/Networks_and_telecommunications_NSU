package entities

import enums.AddressType
import enums.ResponseCode
import enums.SocksVersion

data class ServerResponse(
    val socksVersion: SocksVersion,
    val answerCode: ResponseCode,
    val addressType: AddressType,
    val addressPayload: ByteArray,
    val port: Int
)