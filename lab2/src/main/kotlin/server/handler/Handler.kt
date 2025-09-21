package org.server.handler

import org.server.speedMonitor.SpeedMonitor
import java.io.*
import java.net.Socket
import java.nio.file.*
import kotlin.concurrent.thread

class Handler(
    private val socket: Socket,
    private val updateTime: Long
) : Runnable {

    override fun run() {
        val monitor = SpeedMonitor()
        val uploadsDir = Paths.get("uploads").toAbsolutePath().normalize()
        Files.createDirectories(uploadsDir)

        try {
            socket.getInputStream().use { input ->
                socket.getOutputStream().use { output ->
                    val dataIn = DataInputStream(BufferedInputStream(input))
                    val dataOut = DataOutputStream(BufferedOutputStream(output))
                    val nameLen = dataIn.readInt()
                    if (nameLen <= 0 || nameLen > 4096) {
                        println("Incorrect file name length")
                        return
                    }

                    val nameBytes = ByteArray(nameLen)
                    dataIn.readFully(nameBytes)
                    val fileName = String(nameBytes, Charsets.UTF_8)
                    val fileSize = dataIn.readLong()
                    if (fileSize < 0 || fileSize > 1_000_000_000_000L) {
                        println("Incorrect file name size")
                        return
                    }

                    val safeName = Paths.get(fileName).fileName.toString()
                    val targetPath = uploadsDir.resolve(safeName).normalize()
                    if (!targetPath.startsWith(uploadsDir)) {
                        return
                    }

                    FileOutputStream(targetPath.toFile()).use { fileOut ->
                        val buffer = ByteArray(8192)
                        var received: Long = 0
                        val speedThread = thread(start = true) {
                            while (!socket.isClosed && received < fileSize) {
                                Thread.sleep(updateTime)
                                val (instant, avg) = monitor.reportSpeeds()
                                println("Client ${socket.remoteSocketAddress}: " +
                                        "Speed: %.2f KB/s, AVG Speed: %.2f KB/s".format(
                                            instant / 1024, avg / 1024
                                        ))
                            }
                        }

                        while (received < fileSize) {
                            val read = dataIn.read(buffer)
                            if (read == -1) break
                            fileOut.write(buffer, 0, read)
                            received += read
                            monitor.addBytes(read)
                        }

                        speedThread.interrupt()
                        val success = (received == fileSize)
                        dataOut.writeBoolean(success)
                        dataOut.flush()
                        println("Client ${socket.remoteSocketAddress}: " +
                                if (success) "File $safeName received" else "File is damaged")
                    }
                }
            }
        } catch (e: Exception) {
            println("${socket.remoteSocketAddress}: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (_: IOException) {}
        }
    }
}