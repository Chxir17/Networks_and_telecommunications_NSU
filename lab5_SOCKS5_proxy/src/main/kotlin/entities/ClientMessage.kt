package entities

import enums.AddressType
import enums.MessageCode
import enums.SocksVersion

data class ClientMessage(
    val socksVersion: SocksVersion,
    val messageCode: MessageCode,
    val addressType: AddressType,
    val addressPayload: ByteArray,
    val port: Int
)
