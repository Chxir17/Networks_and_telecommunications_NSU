package org.server.config
import org.config.IConfig

data class ServerConfig(
    override val port: Int,
    val updateTime: Long
) : IConfig