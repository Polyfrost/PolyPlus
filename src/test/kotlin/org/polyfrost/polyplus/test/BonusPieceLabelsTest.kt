package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.gui.wardrobe.BonusPiece
import org.polyfrost.polyplus.client.gui.wardrobe.bonusPieceLabels
import org.polyfrost.polyplus.client.gui.wardrobe.joinBonusNames
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

class BonusPieceLabelsTest {
    @Test
    fun `two gloves from one group name both hands`() {
        val labels = bonusPieceLabels(
            listOf(
                BonusPiece("Ember Gloves", "Ember Gloves", BodySlot.LeftHand),
                BonusPiece("Ember Gloves", "Ember Gloves", BodySlot.RightHand),
                BonusPiece("Ember Crown", "Ember Crown", null),
            ),
        )
        assertEquals(listOf("Left Ember Gloves", "Right Ember Gloves", "Ember Crown"), labels)
        assertEquals("Left Ember Gloves, Right Ember Gloves and Ember Crown", joinBonusNames(labels))
    }

    @Test
    fun `two variants from one group name each variant`() {
        val labels = bonusPieceLabels(
            listOf(
                BonusPiece("Halo", "Gold", null),
                BonusPiece("Halo", "Silver", null),
            ),
        )
        assertEquals(listOf("Halo (Gold)", "Halo (Silver)"), labels)
    }

    @Test
    fun `a lone glove from a two-handed group names its hand`() {
        val labels = bonusPieceLabels(
            listOf(
                BonusPiece(
                    "Ember Gloves",
                    "Ember Gloves",
                    BodySlot.LeftHand,
                    groupSides = setOf(BodySlot.LeftHand, BodySlot.RightHand),
                ),
                BonusPiece("Ember Crown", "Ember Crown", null),
            ),
        )
        assertEquals(listOf("Left Ember Gloves", "Ember Crown"), labels)
    }

    @Test
    fun `a lone piece from a group with colourways names its colourway`() {
        val labels = bonusPieceLabels(
            listOf(
                BonusPiece("Halo", "Gold", null, groupVariants = setOf("Gold", "Silver")),
                BonusPiece(
                    "Gloves",
                    "Red",
                    BodySlot.RightHand,
                    groupVariants = setOf("Red", "Blue"),
                    groupSides = setOf(BodySlot.LeftHand, BodySlot.RightHand),
                ),
            ),
        )
        assertEquals(listOf("Halo (Gold)", "Right Gloves (Red)"), labels)
    }

    @Test
    fun `different groups keep their plain names`() {
        val labels = bonusPieceLabels(listOf(BonusPiece("Halo", "Gold", null), BonusPiece("Wings", "Gold", null)))
        assertEquals(listOf("Halo", "Wings"), labels)
    }
}
