import parser.Parser
import multicast.Multicast

fun main(args: Array<String>) {
    val parser = Parser()
    val config = if (args.isEmpty()) {
        parser.parseConfigFile()
    } else {
        parser.parseArgs(args)
    }
    val multicast = Multicast(config.ip, config.port, config.reportIntervalSec)
    multicast.start()
}

