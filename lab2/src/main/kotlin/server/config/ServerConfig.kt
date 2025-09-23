package server.config

import config.IConfig

data class ServerConfig(
    override val port: Int,
    val updateTime: Long
) : IConfig