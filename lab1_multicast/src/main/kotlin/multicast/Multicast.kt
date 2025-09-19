package multicast

import java.net.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class Multicast(
    private val ip: String,
    private val port: Int,
    private val reportIntervalSec: Long
) {
    private val report: Byte = 1
    private val leave: Byte = 2
    private val members = ConcurrentHashMap<String, Long>()

    @Volatile
    private var running = true

    private fun getIp(): List<String> {
        val ipList = mutableListOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) {
                continue
            }
            for (address in networkInterface.inetAddresses) {
                if (address.isLoopbackAddress) {
                    continue
                }
                ipList.add(address.hostAddress)
            }
        }
        return ipList
    }

    private fun pickNetworkInterface(groupInet: InetAddress): NetworkInterface {
        val wantIpv6 = groupInet is Inet6Address
        val ifaces = NetworkInterface.getNetworkInterfaces().toList()
        println(ifaces)
        val candidates = ifaces.filter { iface ->
            try {
                iface.isUp && !iface.isLoopback && iface.supportsMulticast() && iface.inetAddresses.toList()
                    .any { addr ->
                        (addr is Inet6Address) == wantIpv6 && !addr.isLoopbackAddress
                    }
            }
            catch (e: Exception) {
                false
            }
        }
        return candidates.firstOrNull() ?: run {
            ifaces.firstOrNull { it.supportsMulticast() && it.isUp }
                ?: throw RuntimeException("No suitable network interface found")
        }
    }

    private fun pickNetworkInterface(groupInet: InetAddress, mode: Int): NetworkInterface {
        val wantIpv6 = groupInet is Inet6Address
        val ifaces = NetworkInterface.getNetworkInterfaces().toList()
        println(ifaces)
        ifaces.forEach { iface ->
            println("${iface.name} (${iface.displayName}): ${iface.inetAddresses.toList()}")
        }
        val wifiCandidates = ifaces.filter { iface ->
            try {
                val isWifi = iface.name.startsWith("wlan") ||
                        iface.name.startsWith("wlp") ||
                        iface.displayName.contains("wi-fi", ignoreCase = true) ||
                        iface.displayName.contains("wireless", ignoreCase = true) ||
                        iface.displayName.contains("wifi", ignoreCase = true)

                isWifi && iface.isUp && !iface.isLoopback && iface.supportsMulticast() &&
                        iface.inetAddresses.toList().any { addr ->
                            (addr is Inet6Address) == wantIpv6 && !addr.isLoopbackAddress
                        }
            } catch (e: Exception) {
                false
            }
        }
        wifiCandidates.firstOrNull()?.let {
            return it
        }
        val otherCandidates = ifaces.filter { iface ->
            try {
                iface.isUp && !iface.isLoopback && iface.supportsMulticast() &&
                        iface.inetAddresses.toList().any { addr ->
                            (addr is Inet6Address) == wantIpv6 && !addr.isLoopbackAddress
                        }
            } catch (e: Exception) {
                false
            }
        }
        return otherCandidates.firstOrNull() ?: run {
            ifaces.firstOrNull { it.supportsMulticast() && it.isUp } ?:
            throw RuntimeException("No suitable network interface found")
        }
    }

    private fun printIfChanged(prev: Set<String>) {
        val cur = members.keys.toSet()
        if (cur != prev) {
            println("\n=== Multicast Members (${cur.size}) ===")
            val myIps = getIp().toSet()
            cur.forEach { ip ->
                if (ip in myIps) {
                    println("Your ip: $ip")
                } else {
                    println(ip)
                }
            }
            println("===========================\n")
        }
    }

    fun start() {
        try {
            val group = InetAddress.getByName(ip)
            val ni = if (!System.getProperty("os.name").lowercase().contains("win")) pickNetworkInterface(group) else pickNetworkInterface(group, 0)
            val groupSockAddr = InetSocketAddress(group, port)
            println("Using multicast group: $ip:$port")
            println("Update interval (sec): $reportIntervalSec")

            val senderSocket = DatagramSocket().apply {
                reuseAddress = true
            }

            val listener = MulticastSocket(port).apply {
                reuseAddress = true
                soTimeout = 1000
                timeToLive = 1
                networkInterface = ni
                joinGroup(groupSockAddr, ni)
            }
            Runtime.getRuntime().addShutdownHook(Thread {
                running = false
                try {
                    val leavePacket = DatagramPacket(byteArrayOf(leave), 1, group, port)
                    senderSocket.send(leavePacket)
                    println("Sent leave message")
                } catch (_: Exception) {
                }
                try {
                    listener.leaveGroup(groupSockAddr, ni)
                    listener.close()
                    senderSocket.close()
                } catch (_: Exception) {
                }
            })

            val listenerThread = thread {
                val buffer = ByteArray(1024)
                var previousSet = emptySet<String>()
                while (running) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        listener.receive(packet)
                        val senderIp = packet.address.hostAddress
                        val now = Instant.now().epochSecond
                        if (packet.length > 0) {
                            val typ = packet.data[packet.offset]
                            when (typ) {
                                report -> {
                                    members[senderIp] = now
                                    println("Received report from: $senderIp")
                                }

                                leave -> {
                                    members.remove(senderIp)
                                    println("Received leave from: $senderIp")
                                }

                                else -> {}
                            }
                        }
                        val expiry = now - (2 * reportIntervalSec)
                        members.entries.removeAll { (_, lastSeen) -> lastSeen < expiry }
                        printIfChanged(previousSet)
                        previousSet = members.keys.toSet()
                    } catch (e: SocketTimeoutException) {
                        continue
                    } catch (e: SocketException) {
                        if (!running) break
                        println("Socket error: ${e.message}")
                    } catch (e: Exception) {
                        println("Error in listener: ${e.message}")
                    }
                }
            }

            val senderThread = thread {
                val message = byteArrayOf(report)
                val packet = DatagramPacket(message, message.size, group, port)
                while (running) {
                    try {
                        senderSocket.send(packet)
                        println("Report sent to $ip:$port")
                    } catch (e: Exception) {
                        println("Error sending report: ${e.message}")
                    }
                    try {
                        Thread.sleep(reportIntervalSec * 1000)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
            listenerThread.join()
            senderThread.join()
        } catch (e: Exception) {
            println("Error starting multicast: ${e.message}")
            e.printStackTrace()
        } finally {
            running = false
        }
    }
}