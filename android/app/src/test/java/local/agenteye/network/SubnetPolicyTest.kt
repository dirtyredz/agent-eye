package local.agenteye.network

import java.net.InetAddress
import java.net.Inet6Address
import org.junit.Assert.*
import org.junit.Test

class SubnetPolicyTest {
    private fun allows(phone: String, prefix: Int, peer: String) =
        SubnetPolicy.allows(InetAddress.getByName(phone), prefix, InetAddress.getByName(peer))
    @Test fun exactSubnetAndMasks() {
        assertTrue(allows("192.168.68.130", 24, "192.168.68.200"))
        assertFalse(allows("192.168.68.130", 24, "192.168.69.12"))
        assertTrue(allows("192.168.68.130", 23, "192.168.69.12"))
        assertTrue(allows("192.168.68.130", 25, "192.168.68.254"))
        assertFalse(allows("192.168.68.130", 25, "192.168.68.126"))
        for (peer in listOf("192.168.68.128", "192.168.68.255")) assertFalse(allows("192.168.68.130", 25, peer))
        assertFalse(allows("192.168.68.0", 24, "192.168.68.2"))
        assertFalse(allows("192.168.68.255", 24, "192.168.68.2"))
    }
    @Test fun privateBoundariesAndInvalidInputs() {
        for (ip in listOf("10.0.0.1", "10.255.255.254", "172.16.0.1", "172.31.255.254", "192.168.0.1", "192.168.255.254")) {
            assertTrue(SubnetPolicy.privateV4(InetAddress.getByName(ip)))
        }
        for (ip in listOf("9.255.255.255", "11.0.0.1", "172.15.255.255", "172.32.0.1", "192.167.1.1", "192.169.1.1", "0.0.0.0", "127.0.0.1", "169.254.1.2", "224.0.0.1", "8.8.8.8", "::1")) {
            assertFalse(allows("192.168.68.2", 24, ip))
            assertFalse(allows(ip, 24, "192.168.68.2"))
        }
        for (prefix in listOf(-1, 0, 7, 15, 31, 32, 33)) assertFalse(allows("192.168.68.2", prefix, "192.168.68.3"))
        val mapped = ByteArray(16).apply { this[10] = -1; this[11] = -1; this[12] = 192.toByte(); this[13] = 168.toByte(); this[14] = 68; this[15] = 3 }
        assertFalse(SubnetPolicy.allows(InetAddress.getByName("192.168.68.2"), 24, Inet6Address.getByAddress(null, mapped, -1)))
    }
}
