package proxy

import entities.ClientMessage
import entities.GreetingMessage
import entities.GreetingResponse
import entities.ServerResponse
import enums.AddressType
import enums.MessageCode
import enums.SocksVersion
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ProtocolException

class ByteSliceMessageSource(
    private val `in`: InputStream,
    private val out: OutputStream
) {

    private val buffer = ByteArray(6 + 1 + 256)

    fun readGreetingMessage(): GreetingMessage {
        try {
            var totallyRead = 0
            var r = `in`.read(buffer, totallyRead, buffer.size)
            totallyRead += r
            while (!greetingMessageEnoughBytes(buffer.copyOf(totallyRead))) {
                r = `in`.read(buffer, totallyRead, buffer.size - totallyRead)
                totallyRead += r
            }
            val slice = buffer.copyOf(totallyRead)
            if (!correctGreetingMessage(slice)) {
                throw ProtocolException("Get greeting message: protocol")
            }
            return makeMessageFromBytes(slice)
        } catch (e: Exception) {
            throw e
        }
    }

    fun writeGreetingResponse(answer: GreetingResponse) {
        try {
            val outBuf = ByteArray(2)
            outBuf[0] = answer.socksVersion.b
            outBuf[1] = answer.authMethod
            out.write(outBuf, 0, 2)
        } catch (e: Exception) {
            throw e
        } finally {
            out.flush()
        }
    }


    fun readClientMessage(): ClientMessage {
        try {
            var totallyRead = 0
            var r = `in`.read(buffer, totallyRead, buffer.size)
            totallyRead += r
            while (true) {
                val flag = clientMessageEnoughBytes(buffer.copyOf(totallyRead))
                if (flag) {
                    break
                }
                r = `in`.read(buffer, totallyRead, buffer.size - totallyRead)
                totallyRead += r
            }
            val slice = buffer.copyOf(totallyRead)
            if (!correctClientMessage(slice)) {
                throw ProtocolException("Read client message: protocol")
            }
            return makeClientMessageFromBytes(slice)
        } catch (e: Exception) {
            throw e
        }
    }

    fun writeServerAnswer(answer: ServerResponse) {
        try {
            val tmp = ByteArray(6 + 16 + 256)
            tmp[0] = answer.socksVersion.b
            tmp[1] = answer.answerCode.toByte()
            tmp[2] = 0
            tmp[3] = answer.addressType.b

            var messageSize: Int
            when (answer.addressType) {
                AddressType.IPV4 -> {
                    if (answer.addressPayload.size < 4) {
                        throw IOException("Invalid ipv4 payload")
                    }
                    System.arraycopy(answer.addressPayload, 0, tmp, 4, 4)
                    tmp[8] = ((answer.port ushr 8) and 0xff).toByte()
                    tmp[9] = (answer.port and 0xff).toByte()
                    messageSize = 6 + 4
                }

                AddressType.DOMAIN_NAME -> {
                    val len = answer.addressPayload.size
                    tmp[4] = len.toByte()
                    System.arraycopy(answer.addressPayload, 0, tmp, 5, len)
                    tmp[5 + len] = ((answer.port ushr 8) and 0xff).toByte()
                    tmp[5 + len + 1] = (answer.port and 0xff).toByte()
                    messageSize = 6 + 1 + len
                }

                AddressType.IPV6 -> {
                    if (answer.addressPayload.size < 16) {
                        throw IOException("Invalid ipv6 payload")
                    }
                    System.arraycopy(answer.addressPayload, 0, tmp, 4, 16)
                    tmp[4 + 16] = ((answer.port ushr 8) and 0xff).toByte()
                    tmp[4 + 16 + 1] = (answer.port and 0xff).toByte()
                    messageSize = 6 + 16
                }

                else -> throw IOException("Unsupported address type")
            }
            var wrote = 0
            while (wrote < messageSize) {
                val toWrite = messageSize - wrote
                val end = wrote + toWrite
                out.write(tmp, wrote, toWrite)
                wrote = end
            }
        } catch (e: Exception) {
            throw e
        } finally {
            out.flush()
        }
    }

    private fun greetingMessageEnoughBytes(info: ByteArray): Boolean {
        if (info.size < 2) {
            return false
        }
        return info.size >= 2 + info[1].toInt()
    }

    private fun correctGreetingMessage(info: ByteArray): Boolean {
        if (info.isEmpty()) {
            return false
        }
        if (info[0] != SocksVersion.SOCKS5.b) {
            return false
        }
        return info.size == 2 + info[1].toInt()
    }

    private fun makeMessageFromBytes(info: ByteArray): GreetingMessage {
        val nm = info[1]
        val auth = if (info.size > 2) info.copyOfRange(2, info.size) else ByteArray(0)
        return GreetingMessage(SocksVersion.SOCKS5, nm, auth)
    }

    private fun clientMessageEnoughBytes(info: ByteArray): Boolean {
        if (info.size < 4) {
            return false
        }
        return try {
            when (info[3]) {
                1.toByte() -> info.size >= 6 + 4
                3.toByte() -> {
                    val len = if (info.size >= 5) info[4].toInt() else return false
                    info.size >= 6 + 1 + len
                }

                4.toByte() -> info.size >= 6 + 16
                else -> false
            }
        } catch (e: Exception) {
            throw e
        }
    }

    private fun correctClientMessage(info: ByteArray): Boolean {
        if (info[0] != SocksVersion.SOCKS5.b) {
            return false
        }
        if (info[1] != 1.toByte() && info[1] != 2.toByte() && info[1] != 3.toByte()) {
            return false
        }
        if (info[2] != 0.toByte()) {
            return false
        }
        return when (info[3]) {
            1.toByte() -> info.size == 6 + 4
            3.toByte() -> {
                val l = info[4].toInt()
                info.size == 6 + 1 + l
            }

            4.toByte() -> info.size == 6 + 16
            else -> false
        }
    }

    private fun makeClientMessageFromBytes(info: ByteArray): ClientMessage {
        return when (info[3]) {
            1.toByte() -> {
                val addr = info.copyOfRange(4, 4 + 4)
                val port = ((info[8].toInt() and 0xff) shl 8) or (info[9].toInt() and 0xff)
                ClientMessage(
                    SocksVersion.SOCKS5,
                    MessageCode.fromByte(info[1]),
                    AddressType.fromByte(info[3]),
                    addr,
                    port
                )
            }

            3.toByte() -> {
                val len = info[4].toInt()
                val addr = info.copyOfRange(5, 5 + len)
                val port = ((info[5 + len].toInt() and 0xff) shl 8) or (info[5 + len + 1].toInt() and 0xff)
                ClientMessage(
                    SocksVersion.SOCKS5,
                    MessageCode.fromByte(info[1]),
                    AddressType.fromByte(info[3]),
                    addr,
                    port
                )
            }

            4.toByte() -> {
                val addr = info.copyOfRange(4, 4 + 16)
                val port = ((info[4 + 16].toInt() and 0xff) shl 8) or (info[4 + 16 + 1].toInt() and 0xff)
                ClientMessage(
                    SocksVersion.SOCKS5,
                    MessageCode.fromByte(info[1]),
                    AddressType.fromByte(info[3]),
                    addr,
                    port
                )
            }

            else -> throw ProtocolException("Make client message from bytes: unexpected address type")
        }
    }

}