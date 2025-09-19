package parser
import config.Config
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException

class Parser {

    private fun isValidIpAddress(ip: String) : Boolean{
        return try {
            InetAddress.getByName(ip).isMulticastAddress
        } catch (e: UnknownHostException) {
            false
        }
    }

    private fun isValidPort(port: Int?): Boolean{
        return port in 1..65534
    }

    fun parseConfigFile(path: String = "config.cfg"): Config {
        val props = mutableMapOf<String, String>()
        val inputStream = when {
            File(path).exists() -> File(path).inputStream()
            else -> Parser::class.java.classLoader.getResourceAsStream(path)
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
        val ip = props["ip"] ?: error("Config missing 'ip'")
        require(isValidIpAddress(ip)) { "Invalid IP in config: $ip" }
        val port = props["port"]?.toIntOrNull() ?: error("Config missing or invalid 'port'")
        require(isValidPort(port)) { "Invalid port in config: $port" }
        val reportIntervalSec = props["time"]?.toLongOrNull() ?: 10L
        return Config(ip, port, reportIntervalSec)
    }


    fun parseArgs(args: Array<String>): Config {
        var ip: String? = null
        var port: Int? = null
        var reportIntervalSec: Long? = null
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--ip", "-i" -> {
                    if (i + 1 >= args.size){
                        error("Usage: ${args[i]} <ip>")
                    }
                    ip = if(isValidIpAddress(args[i + 1])) args[i + 1] else error("Invalid value for argument: ${args[i]} - invalid address")
                    i += 2
                }
                "--port", "-p" -> {
                    if (i + 1 >= args.size){
                        error("Usage: ${args[i]} <port>")
                    }
                    port = if(isValidPort(args[i + 1].toIntOrNull())) args[i+1].toIntOrNull() else error("Invalid value for argument: ${args[i]} - invalid port number")
                    i += 2
                }
                "--time", "-t" -> {
                    if (i + 1 >= args.size){
                        error("Usage: ${args[i]} <seconds>")
                    }
                    reportIntervalSec = args[i + 1].toLongOrNull() ?: error("Invalid value for argument: ${args[i]} <seconds(integer)>")
                    i += 2
                }
                "--help", "-h" ->{
                    println("Usage:\nFor help: --help or -h\nTo indicate ip(necessary argument): --ip or -i <ip>\nTo indicate port(necessary argument): --port or -p <port number>\nTo indicate second for table update(not necessary argument): --time or -t <seconds>")
                }
                else -> error("Unknown argument: ${args[i]} --help or -h for help")
            }
        }
        if (ip == null || port == null) {
            error("Necessary arguments: --ip <ip> --port <port>")
        }
        return Config(ip, port, reportIntervalSec ?: 10L)
    }
}