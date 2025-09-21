package org

import org.client.clientWork.ClientWork
import org.client.config.ClientConfig
import org.server.parser.ClientParser

fun main(args: Array<String>) {
    val parser = ClientParser()
    val config = if (args.isEmpty()) {
        parser.parseConfigFile()
    } else {
        parser.parseArgs(args)
    } as ClientConfig
    ClientWork(config).sendFile()
}