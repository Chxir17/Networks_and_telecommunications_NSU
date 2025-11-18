package entities

import enums.SocksVersion

data class GreetingResponse(
    val socksVersion: SocksVersion,
    val authMethod: Byte
)
