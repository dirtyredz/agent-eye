package local.agenteye.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address

data class LanAddress(val address: Inet4Address, val prefix: Int, val network: Network)

class LanBinding(context: Context, private val changed: (LanAddress?) -> Unit) : AutoCloseable {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val networks = mutableMapOf<Network, LanAddress?>()
    private var closed = false
    private var previous: LanAddress? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh(network)
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = refresh(network)
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh(network)
        override fun onLost(network: Network) { main.post { if (!closed) { networks.remove(network); publish() } } }
    }
    init {
        manager.registerNetworkCallback(NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback)
    }
    private fun refresh(network: Network) {
        main.post {
            if (closed) return@post
            val capabilities = manager.getNetworkCapabilities(network)
            val links = manager.getLinkProperties(network)
            val addresses = links?.linkAddresses?.filter { it.address is Inet4Address }
            val link = addresses?.singleOrNull()
            networks[network] = if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                link != null && SubnetPolicy.allows(link.address, link.prefixLength, link.address)) {
                LanAddress(link.address as Inet4Address, link.prefixLength, network)
            } else null
            publish()
        }
    }
    private fun publish() {
        // Multiple Wi-Fi networks (even one ineligible) are an ambiguous topology.
        val current = if (networks.size == 1) networks.values.single() else null
        if (current != previous) { previous = current; changed(current) }
    }
    override fun close() {
        closed = true
        manager.unregisterNetworkCallback(callback)
        networks.clear()
        previous = null
    }
}
