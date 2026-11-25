package org.polyfrost.polyplus.client.gui.vanillamenu

import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig

/** The last few addresses typed into Direct Connect, shown in their own section of the vanilla server list. */
object RecentDirectConnects {
    const val MAX = 3

    @JvmStatic
    fun addresses(): List<String> =
        PolyPlusMainMenuConfig.recentDirectConnects.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX)

    @JvmStatic
    fun record(address: String?) {
        val trimmed = address?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        store(listOf(trimmed) + addresses().filterNot { it.equals(trimmed, ignoreCase = true) })
    }

    @JvmStatic
    fun remove(address: String?) {
        store(addresses().filterNot { it.equals(address?.trim(), ignoreCase = true) })
    }

    private fun store(list: List<String>) {
        PolyPlusMainMenuConfig.recentDirectConnects = list.take(MAX).joinToString("\n")
        PolyPlusMainMenuConfig.save()
    }
}
