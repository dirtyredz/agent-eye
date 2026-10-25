package local.agenteye.network

import java.net.Inet4Address
import java.net.InetAddress

object SubnetPolicy {
    fun privateV4(address: InetAddress): Boolean {
        if (address !is Inet4Address) return false
        val b = address.address.map { it.toInt() and 255 }
        return b[0] == 10 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168)
    }

    private fun number(address: InetAddress): Long = address.address.fold(0L) { n, b ->
        (n shl 8) or (b.toLong() and 255)
    }

    fun allows(phone: InetAddress, prefix: Int, peer: InetAddress): Boolean {
        if (prefix !in 1..30 || !privateV4(phone) || !privateV4(peer)) return false
        val mask = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        val network = number(phone) and mask
        val broadcast = network or (mask xor 0xffffffffL)
        val local = number(phone)
        val remote = number(peer)
        // Reject prefixes extending outside this RFC1918 allocation too.
        val minimum = when (phone.address[0].toInt() and 255) { 10 -> 8; 172 -> 12; else -> 16 }
        return prefix >= minimum && local != network && local != broadcast &&
            remote != network && remote != broadcast && (remote and mask) == network
    }
}
