import server.config.ServerConfig
import server.parser.ServerParser
import server.serverWork.ServerWork

fun main(args: Array<String>) {
    val parser = ServerParser()
    val config = if (args.isEmpty()) {
        parser.parseConfigFile()
    } else {
        parser.parseArgs(args)
    } as ServerConfig
    ServerWork(config).start()
}