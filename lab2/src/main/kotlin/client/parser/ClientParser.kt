package org.server.parser

import org.argumentsParser.IParser
import org.client.config.ClientConfig
import org.config.IConfig
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Paths

class ClientParser : IParser {

    private fun isValidIpAddress(ip: String) : Boolean{
        return try {
            InetAddress.getByName(ip)
            true
        } catch (e: UnknownHostException) {
            false
        }
    }

    private fun isValidPort(port: Int?): Boolean{
        return port in 1..65534
    }

    private fun isValidFile(pathStr: String?): Boolean {
        if (pathStr == null) {
            return false
        }
        val path = Paths.get(pathStr).toAbsolutePath().normalize()
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            error("File doesn't exist or invalid file: $pathStr")
        }
        val fileNameBytes = path.fileName.toString().toByteArray(Charsets.UTF_8)
        if (fileNameBytes.size > 4096) {
            error("File name is to long (>4096 байт)")
        }
        val fileSize = Files.size(path)
        if (fileSize > 1_000_000_000_000L) {
            error("File is to large (>1 TB)")
        }
        return true
    }


    override fun parseConfigFile(path: String): IConfig {
        val props = mutableMapOf<String, String>()
        val file = File(path)
        val inputStream = when {
            file.exists() && file.isFile -> file.inputStream()
            else -> ClientParser::class.java.classLoader.getResourceAsStream(path)
                ?: error("Config file '$path' not found (checked file system and resources)")
        }
        inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val clean = line.trim()
                if (clean.isEmpty() || clean.startsWith("#")) return@forEach
                val parts = clean.split("=", limit = 2)
                if (parts.size == 2) {
                    props[parts[0].trim()] = parts[1].trim()
                }
            }
        }
        val ip = props["ip"] ?: error("Config missing 'ip'")
        require(isValidIpAddress(ip)) { "Invalid IP in config: $ip" }
        val port = props["port"]?.toIntOrNull() ?: error("Config missing or invalid 'port'")
        require(isValidPort(port)) { "Invalid port in config: $port" }
        val filePath = props["path"] ?: error("Config missing 'path'")
        require(isValidFile(filePath)) { "Invalid path in config: $filePath" }
        val absPath = File(filePath).absolutePath
        return ClientConfig(port, ip, absPath)
    }


    override fun parseArgs(args: Array<String>): IConfig {
        var ip: String? = null
        var port: Int? = null
        var filePath: String? = null
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
                "--path", "-fp" -> {
                    if (i + 1 >= args.size) {
                        error("Usage: ${args[i]} <path>")
                    }
                    val pathArg = args[i + 1]
                    println(File(pathArg).absolutePath)
                    filePath = if(isValidFile(pathArg)) File(pathArg).absolutePath else error("Invalid value for argument: ${args[i]} - invalid file path")
                    i += 2
                }
                "--help", "-h" ->{
                    println("Usage:\nFor help: --help or -h\nTo indicate ip(necessary argument): --ip or -i <ip>\nTo indicate port(necessary argument): --port or -p <port number>\nTo indicate file path: --path or -fp <seconds>")
                }
                else -> error("Unknown argument: ${args[i]} --help or -h for help")
            }
        }
        if (ip == null || port == null || filePath == null) {
            error("Necessary arguments: --ip <ip> --port <port> --path <path>")
        }
        return ClientConfig(port, ip, filePath)
    }
}
