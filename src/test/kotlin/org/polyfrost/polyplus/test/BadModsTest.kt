package org.polyfrost.polyplus.test

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.features.BadMods
import org.polyfrost.polyplus.client.features.BadMods.Entry
import org.polyfrost.polyplus.client.features.BadMods.InstalledMod
import org.polyfrost.polyplus.client.features.BadMods.ProjectIds

class BadModsTest {

    private fun mod(
        name: String = "Some Mod",
        authors: List<String> = listOf("someone"),
        sha1: String = "0000",
        modrinth: String? = null,
    ) = InstalledMod(name.lowercase().replace(' ', '-'), name, authors, sha1, modrinth)

    @Test
    fun `hash matches only that exact file`() {
        val entry = Entry(hash = "ABCD", name = "Some Mod")
        assertTrue(BadMods.matches(entry, mod(sha1 = "abcd")))
        assertFalse(BadMods.matches(entry, mod(sha1 = "ef01")))
    }

    @Test
    fun `modrinth project id matches every version`() {
        val entry = Entry(projectIds = ProjectIds(modrinth = "rrls"), name = "RRLS")
        assertTrue(BadMods.matches(entry, mod(sha1 = "any", modrinth = "rrls")))
        assertFalse(BadMods.matches(entry, mod(sha1 = "any", modrinth = "sodium")))
    }

    @Test
    fun `name and author are ignored when an id is given`() {
        val entry = Entry(projectIds = ProjectIds(modrinth = "rrls"), name = "Some Mod", author = "someone")
        assertFalse(BadMods.matches(entry, mod()))
    }

    @Test
    fun `curseforge-only entries fall back to name and author`() {
        val entry = Entry(projectIds = ProjectIds(curseforge = "400308"), name = "OptiLegacy", author = "hydo_s")
        assertTrue(BadMods.matches(entry, mod(name = "optilegacy", authors = listOf("Hydo_S"))))
        assertFalse(BadMods.matches(entry, mod(name = "OptiLegacy", authors = listOf("impostor"))))
    }

    @Test
    fun `list from data storage parses`() {
        val json = """
            {"bad-mods": [{
                "project-ids": {"modrinth": "rrls"},
                "name": "Remove Reloading Screen", "author": "dimadencep",
                "explanation": "/oneclient/bad_mods_mds/rrls.md",
                "alternatives": [{"project-ids": {"modrinth": "abc"}}],
                "unknown": true
            }]}
        """.trimIndent()
        val list = Json { ignoreUnknownKeys = true }.decodeFromString<BadMods.BadModList>(json)
        val detections = BadMods.detect(list, listOf(mod(modrinth = "rrls"), mod(name = "Other")))
        assertEquals(1, detections.size)
        assertEquals("abc", detections.single().entry.alternatives.single().projectIds.modrinth)
    }

    @Test
    fun `explanation markdown becomes plain text`() {
        val md = "# Remove Reloading Screen (RRLS)\n\nOneClient already ships **safer** mods. See [docs](https://x.y).\n"
        assertEquals("OneClient already ships safer mods. See docs.", BadMods.plainText(md))
    }
}
