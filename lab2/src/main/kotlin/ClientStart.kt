import client.clientWork.ClientWork
import client.config.ClientConfig
import client.parser.ClientParser

fun main(args: Array<String>) {
    val parser = ClientParser()
    val config = if (args.isEmpty()) {
        parser.parseConfigFile()
    } else {
        parser.parseArgs(args)
    } as ClientConfig
    ClientWork(config).sendFile()
}