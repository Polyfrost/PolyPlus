package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticUiItem
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticVariantUi
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeEntry
import org.polyfrost.polyplus.client.gui.wardrobe.selectedVariantId
import org.polyfrost.polyplus.client.network.http.responses.CosmeticGroupKey
import org.polyfrost.polyplus.client.network.http.responses.CosmeticList
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import kotlinx.serialization.json.Json

class CosmeticGroupKeyTest {
    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }

        val COLLIDING = """
            {
              "cosmetics": [
                {
                  "id": 20,
                  "grouped": false,
                  "type": "pet",
                  "name": "Owl",
                  "variants": [{ "id": 20, "name": "Owl", "hash": "a" }]
                },
                {
                  "id": 20,
                  "grouped": true,
                  "type": "cape",
                  "name": "Pride",
                  "variants": [
                    { "id": 31, "name": "Rainbow", "hash": "b" },
                    { "id": 32, "name": "Trans", "hash": "c" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val LEGACY = """
            {"cosmetics": [{"id": 5, "type": "cape", "name": "Old", "variants": [{"id": 9, "hash": "d"}]}]}
        """.trimIndent()
    }

    private fun item(groupId: Int, grouped: Boolean, vararg variantIds: Int) = CosmeticUiItem(
        groupId = groupId,
        type = CosmeticType.Cape,
        name = "Cape",
        collection = "Cape Collection",
        owned = true,
        equipped = false,
        variants = variantIds.map { CosmeticVariantUi(it, "Variant $it") },
        equippedVariantId = null,
        grouped = grouped,
    )

    @Test
    fun `a group and an ungrouped cosmetic sharing an id keep their own type and key`() {
        val list = JSON.decodeFromString<CosmeticList>(COLLIDING)
        val (pet, cape) = list.contents

        assertNotEquals(pet.key, cape.key)

        val definitions = list.contents.flatMap { it.flatten() }.associateBy { it.id }
        assertEquals(CosmeticType.Pet, definitions.getValue(20).type)
        assertEquals(CosmeticGroupKey(20, false), definitions.getValue(20).groupKey)
        assertEquals(CosmeticType.Cape, definitions.getValue(31).type)
        assertEquals(CosmeticGroupKey(20, true), definitions.getValue(31).groupKey)
        assertEquals(CosmeticGroupKey(20, true), definitions.getValue(32).groupKey)
    }

    @Test
    fun `a server without the flag keys every entry the same way as before`() {
        val entry = JSON.decodeFromString<CosmeticList>(LEGACY).contents.single()

        assertEquals(CosmeticGroupKey(5, false), entry.key)
        assertEquals(CosmeticGroupKey(5, false), entry.flatten().single().groupKey)
    }

    @Test
    fun `wardrobe picks and list keys never mix a group with an ungrouped cosmetic`() {
        val ungrouped = item(20, grouped = false, 20)
        val grouped = item(20, grouped = true, 31, 32)
        val picks = mapOf(grouped.key to 32)

        assertEquals(32, selectedVariantId(grouped, picks))
        assertEquals(20, selectedVariantId(ungrouped, picks))
        assertNotEquals(WardrobeEntry.Owned(ungrouped).key, WardrobeEntry.Owned(grouped).key)
    }
}
