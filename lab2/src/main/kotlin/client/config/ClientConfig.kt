package org.client.config

import org.config.IConfig

data class ClientConfig (
    override val port: Int,
    val ip: String,
    val filePath: String,
) : IConfig