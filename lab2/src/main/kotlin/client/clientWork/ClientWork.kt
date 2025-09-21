package org.client.clientWork
import org.client.config.ClientConfig
import java.io.*
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ClientWork(
    private val config: ClientConfig
) {

    fun sendFile() {
        val path: Path = Paths.get(config.filePath).toAbsolutePath().normalize()
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return
        }
        val fileNameBytes = path.fileName.toString().toByteArray(Charsets.UTF_8)
        if (fileNameBytes.size > 4096) {
            return
        }
        val fileSize = Files.size(path)
        if (fileSize > 1_000_000_000_000L) {
            return
        }
        println("Connecting to: ${config.ip}:${config.port} ...")

        Socket(config.ip, config.port).use { socket ->
            socket.getOutputStream().use { out ->
                socket.getInputStream().use { ins ->
                    val dataOut = DataOutputStream(BufferedOutputStream(out))
                    val dataIn = DataInputStream(BufferedInputStream(ins))
                    dataOut.writeInt(fileNameBytes.size)
                    dataOut.write(fileNameBytes)
                    dataOut.writeLong(fileSize)
                    dataOut.flush()
                    Files.newInputStream(path).use { fileIn ->
                        val buffer = ByteArray(8192)
                        var sent: Long = 0
                        while (true) {
                            val read = fileIn.read(buffer)
                            if (read == -1) break
                            dataOut.write(buffer, 0, read)
                            sent += read
                        }
                        dataOut.flush()
                        println("Sent $sent bytes")
                    }
                    val success = dataIn.readBoolean()
                    if (success) {
                        println("File successfully sent")
                    } else {
                        println("Failed while sending file")
                    }
                }
            }
        }
    }
}