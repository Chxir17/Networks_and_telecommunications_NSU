package entities

import enums.SocksVersion

data class GreetingMessage(
    val socksVersion: SocksVersion,
    val numOfAuthMethods: Byte,
    val authMethods: ByteArray
)