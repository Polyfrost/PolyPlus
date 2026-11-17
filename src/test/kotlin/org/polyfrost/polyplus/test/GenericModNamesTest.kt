package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.platform.v1.ModInfo
import org.polyfrost.polyplus.client.features.GenericModNames

class GenericModNamesTest {

    @Test
    fun `a branded mod gets a generic card title and stays searchable by its old name`() {
        val tree = Tree("polyblur.json", "PolyBlur", null, null)

        GenericModNames.rename(tree, emptySet())

        assertEquals("Motion Blur", tree.getMetadata<String>("mod_card_title"))
        assertEquals(listOf("PolyBlur"), tree.getMetadata<List<String>>("aliases"))
    }

    @Test
    fun `other mods are left alone`() {
        val tree = Tree("sodium", "Sodium", null, null)

        GenericModNames.rename(tree, emptySet())

        assertNull(tree.getMetadata<String>("mod_card_title"))
        assertNull(tree.getMetadata<Any>("aliases"))
    }

    @Test
    fun `renaming twice does not repeat the alias and keeps existing ones`() {
        val tree = Tree("polysprint.json", "PolySprint", null, null)
        tree.addMetadata("aliases", listOf("sprint"))

        repeat(2) { GenericModNames.rename(tree, emptySet()) }

        assertEquals(listOf("sprint", "PolySprint"), tree.getMetadata<List<String>>("aliases"))
    }

    @Test
    fun `mod details survive when the config id does not match the mod id`() {
        val tree = Tree("nametag.json", "PolyNametag", null, null)
        val mod = ModInfo("polynametag", "PolyNametag", "1.2.4", null, null, "Polyfrost", null, "Nametag options")

        GenericModNames.rename(tree, setOf(mod))

        assertEquals("Polyfrost", tree.getMetadata<String>("mod_card_authors"))
        assertEquals("1.2.4", tree.getMetadata<String>("mod_card_version"))
        assertEquals("Nametag options", tree.getMetadata<String>("mod_card_description"))
        assertNull(tree.getMetadata<String>("mod_card_credits"))
    }
}
