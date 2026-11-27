package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

class SkinVariantTest {
    private fun glove(id: Int, model: String?, slot: BodySlot, name: String = "Leather") = CosmeticDefinition(
        id = id,
        type = CosmeticType.Glove,
        hash = "h$id",
        allowedSlots = listOf(slot),
        groupId = 1,
        variantName = name,
        model = model,
    )

    private val leftWide = glove(10, "wide", BodySlot.LeftHand)
    private val leftSlim = glove(11, "slim", BodySlot.LeftHand)
    private val rightWide = glove(12, "wide", BodySlot.RightHand)
    private val rightSlim = glove(13, "slim", BodySlot.RightHand)
    private val group = listOf(leftWide, leftSlim, rightWide, rightSlim)

    @Test
    fun `a piece that already matches the model is kept`() {
        assertEquals(rightSlim.id, CosmeticCatalog.skinVariant(rightSlim, group, slim = true))
        assertEquals(leftWide.id, CosmeticCatalog.skinVariant(leftWide, group, slim = false))
    }

    @Test
    fun `siblings stay on the same hand`() {
        assertEquals(leftSlim.id, CosmeticCatalog.skinVariant(leftWide, group, slim = true))
        assertEquals(rightSlim.id, CosmeticCatalog.skinVariant(rightWide, group, slim = true))
        assertEquals(leftWide.id, CosmeticCatalog.skinVariant(leftSlim, group, slim = false))
        assertEquals(rightWide.id, CosmeticCatalog.skinVariant(rightSlim, group, slim = false))
    }

    @Test
    fun `no sibling for the hand keeps the piece`() {
        val lonely = listOf(leftWide, rightSlim)
        assertEquals(leftWide.id, CosmeticCatalog.skinVariant(leftWide, lonely, slim = true))
    }

    @Test
    fun `an unowned sibling keeps the owned piece`() {
        val owned = setOf(leftWide.id, rightWide.id, rightSlim.id)
        assertEquals(leftWide.id, CosmeticCatalog.skinVariant(leftWide, group, slim = true, usable = owned::contains))
        assertEquals(rightSlim.id, CosmeticCatalog.skinVariant(rightWide, group, slim = true, usable = owned::contains))
    }

    @Test
    fun `model-free pieces are untouched`() {
        val plain = glove(20, null, BodySlot.LeftHand)
        assertEquals(plain.id, CosmeticCatalog.skinVariant(plain, group + plain, slim = true))
    }
}
