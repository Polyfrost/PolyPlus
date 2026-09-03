package org.polyfrost.polyplus.client.network.eos

import java.lang.management.ManagementFactory

object EosFailureDiagnosis {
    const val FD_SETSIZE = 1024L

    fun explain(openFds: Long?, backendReachable: Boolean): String? {
        if (openFds != null && openFds >= FD_SETSIZE) {
            return "This game has $openFds files open, at or over libcurl's FD_SETSIZE limit of $FD_SETSIZE, " +
                "so EOS HTTPS requests are likely failing instantly however healthy the network is. " +
                "Removing mods or resource packs, or fixing whichever mod leaks sockets, frees the " +
                "descriptors EOS needs. Note that this is a limitation of Epic's SDK, not of your connection."
        }
        if (backendReachable) {
            return "The Poly+ backend is connected, so the network itself is up; EOS specifically cannot reach " +
                "api.epicgames.dev. A DNS blocker, VPN or per-application firewall rule is the usual cause."
        }
        return null
    }

    fun openFileDescriptors(): Long? = runCatching {
        (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.UnixOperatingSystemMXBean)
            ?.openFileDescriptorCount
    }.getOrNull()
}
