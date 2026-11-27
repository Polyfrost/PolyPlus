package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticGroupView
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticGroupKey
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

class GroupContainingTest {
    private fun cape(id: Int, groupId: Int) =
        CosmeticDefinition(id = id, type = CosmeticType.Cape, hash = "h$id", groupId = groupId, grouped = true)

    private fun group(id: Int, vararg variants: CosmeticDefinition) =
        CosmeticGroupView(id, CosmeticType.Cape, "Group $id", emptyList(), variants.toList(), grouped = true)

    private val red = cape(11, groupId = 1)
    private val other = cape(30, groupId = 11)
    private val clashing = group(11, other)
    private val owning = group(1, red)
    private val groups = listOf(clashing, owning)

    @Test
    fun `a cosmetic id finds its own group even when another group shares that number`() {
        assertEquals(1, CosmeticCatalog.groupFor(red.id, red.groupKey, groups)?.groupId)
    }

    @Test
    fun `an unknown definition still finds the group listing it`() {
        assertEquals(1, CosmeticCatalog.groupFor(red.id, null, groups)?.groupId)
    }

    @Test
    fun `a cosmetic id never resolves a group whose number it happens to share`() {
        assertNull(CosmeticCatalog.groupFor(1, null, groups))
        assertEquals(clashing, CosmeticCatalog.groupFor(other.id, other.groupKey, groups))
    }

    @Test
    fun `nothing matching gives no group`() {
        assertNull(CosmeticCatalog.groupFor(500, null, groups))
    }

    @Test
    fun `an ungrouped cosmetic and a group with the same id resolve separately`() {
        val pet = CosmeticDefinition(id = 20, type = CosmeticType.Pet, hash = "h20")
        val ungrouped = CosmeticGroupView(20, CosmeticType.Pet, "Pet", emptyList(), listOf(pet))
        val cape = cape(31, groupId = 20)
        val grouped = group(20, cape)
        val views = listOf(grouped, ungrouped)

        assertEquals(CosmeticGroupKey(20, false), pet.groupKey)
        assertEquals(CosmeticGroupKey(20, true), cape.groupKey)
        assertEquals(ungrouped, CosmeticCatalog.groupFor(pet.id, pet.groupKey, views))
        assertEquals(grouped, CosmeticCatalog.groupFor(cape.id, cape.groupKey, views))
    }

    @Test
    fun `a refresh that starts grouping cosmetics drops the old ungrouped keys`() {
        val old = CosmeticDefinition(id = 11, type = CosmeticType.Cape, hash = "h11", groupId = 1)
        val hidden = CosmeticDefinition(id = 40, type = CosmeticType.Cape, hash = "h40", groupId = 4)
        val kept = CosmeticCatalog.retainedGroupKeys(listOf(CosmeticGroupKey(1, true)), listOf(old, hidden), listed = setOf(11))

        assertEquals(setOf(CosmeticGroupKey(1, true), CosmeticGroupKey(4, false)), kept)
    }
}
