package server.parser

import parser.IParser
import config.IConfig
import server.config.ServerConfig
import java.io.File

class ServerParser : IParser {
    private fun isValidPort(port: Int?): Boolean {
        return port in 1..65534
    }

    override fun parseConfigFile(path: String): IConfig {
        val props = mutableMapOf<String, String>()
        val inputStream = when {
            File(path).exists() -> File(path).inputStream()
            else -> ServerParser::class.java.classLoader.getResourceAsStream(path)
                ?: error("Config file $path not found (checked file system and resources)")
        }
        inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val clean = line.trim()
                if (clean.isEmpty() || clean.startsWith("#")) return@forEach
                val parts = clean.split("=")
                if (parts.size == 2) {
                    props[parts[0].trim()] = parts[1].trim()
                }
            }
        }
        val port = props["port"]?.toIntOrNull() ?: error("Config missing or invalid 'port'")
        require(isValidPort(port)) { "Invalid port in config: $port" }
        val speedReportIntervalSec = props["time"]?.toLongOrNull()
        return ServerConfig(port, speedReportIntervalSec ?: 3L)
    }


    override fun parseArgs(args: Array<String>): IConfig {
        var port: Int? = null
        var speedReportIntervalSec: Long? = null
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--port", "-p" -> {
                    if (i + 1 >= args.size) {
                        error("Usage: ${args[i]} <port>")
                    }
                    port =
                        if (isValidPort(args[i + 1].toIntOrNull())) args[i + 1].toIntOrNull() else error("Invalid value for argument: ${args[i]} - invalid port number")
                    i += 2
                }

                "--time", "-t" -> {
                    if (i + 1 >= args.size) {
                        error("Usage: ${args[i]} <seconds>")
                    }
                    speedReportIntervalSec =
                        args[i + 1].toLongOrNull() ?: error("Invalid value for argument: ${args[i]} <seconds(integer)>")
                    i += 2
                }

                "--help", "-h" -> {
                    println("Usage:\nFor help: --help or -h\nTo indicate port(necessary argument): --port or -p <port number>\nTo indicate second for speed time update(not necessary argument): --time or -t <seconds>")
                }

                else -> error("Unknown argument: ${args[i]} --help or -h for help")
            }
        }
        if (port == null) {
            error("Necessary argument: --port <port>")
        }
        return ServerConfig(port, speedReportIntervalSec ?: 3L)
    }

}
