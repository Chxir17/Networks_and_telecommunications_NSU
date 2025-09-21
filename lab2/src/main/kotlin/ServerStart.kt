package org

import org.server.config.ServerConfig
import org.server.parser.ServerParser
import org.server.serverWork.ServerWork

fun main(args: Array<String>) {
        val parser = ServerParser()
        val config = if (args.isEmpty()) {
                parser.parseConfigFile()
        } else {
                parser.parseArgs(args)
        } as ServerConfig
        ServerWork(config).start()
}