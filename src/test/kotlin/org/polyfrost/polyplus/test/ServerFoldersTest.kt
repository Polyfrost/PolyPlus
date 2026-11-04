package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.featured.ServerFolders
import org.polyfrost.polyplus.client.featured.ServerFolders.Sort

class ServerFoldersTest {
    private val folders = mapOf("b" to "pvp", "c" to "Smp", "e" to "pvp")
    private val servers = listOf("d", "b", "c", "a", "e")

    private fun layout(sort: Sort, order: List<String> = emptyList()) =
        ServerFolders.layoutBy(servers, { folders[it] }, { it }, sort, order).map { it.folder to it.items }

    @Test
    fun `custom keeps the saved server order and the chosen folder order`() {
        assertEquals(
            listOf(null to listOf("d", "a"), "Smp" to listOf("c"), "pvp" to listOf("b", "e")),
            layout(Sort.CUSTOM, listOf("Smp", "pvp")),
        )
    }

    @Test
    fun `alphabetical sorts folders and servers ignoring case`() {
        assertEquals(
            listOf(null to listOf("a", "d"), "pvp" to listOf("b", "e"), "Smp" to listOf("c")),
            layout(Sort.A_Z),
        )
        assertEquals(
            listOf(null to listOf("d", "a"), "Smp" to listOf("c"), "pvp" to listOf("e", "b")),
            layout(Sort.Z_A),
        )
    }
}
