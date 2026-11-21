package org.polyfrost.polyplus.test

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.polyplus.client.gui.screens.packs.PackFiles
import org.polyfrost.polyplus.client.gui.screens.packs.PackMovePlan

class PackMovePlanTest {
    @Test
    fun `moving a pack into a symlinked alias of its folder plans nothing`(@TempDir root: Path) {
        val real = Files.createDirectory(root.resolve("a"))
        Files.writeString(real.resolve("pack.zip"), "content")
        val alias = Files.createSymbolicLink(root.resolve("b"), real)

        val plan = PackMovePlan.of(listOf("file/a/pack.zip"), { root.resolve("a/pack.zip") }, alias)

        assertEquals(emptyList<Any>(), plan.moves)
        assertEquals(emptyList<Path>(), plan.skipped)
    }

    @Test
    fun `moving a relative symlink pack deeper keeps it pointing at the same file`(@TempDir root: Path) {
        Files.writeString(root.resolve("real.zip"), "content")
        val link = Files.createSymbolicLink(Files.createDirectory(root.resolve("a")).resolve("pack.zip"), Path.of("../real.zip"))
        val target = root.resolve("b/deeper/pack.zip")

        PackFiles.move(link, target, overwrite = false)

        assertTrue(Files.isSymbolicLink(target))
        assertEquals("content", Files.readString(target))
    }

    @Test
    fun `moving a relative symlink pack out of a symlinked folder resolves against the real parent`(@TempDir root: Path) {
        val outside = Files.createDirectories(root.resolve("outside/a"))
        Files.writeString(root.resolve("outside/real.zip"), "content")
        Files.createSymbolicLink(outside.resolve("pack.zip"), Path.of("../real.zip"))
        val packs = Files.createDirectory(root.resolve("packs"))
        Files.writeString(packs.resolve("real.zip"), "decoy")
        Files.createSymbolicLink(packs.resolve("b"), outside)
        val target = packs.resolve("c/pack.zip")

        PackFiles.move(packs.resolve("b/pack.zip"), target, overwrite = false)

        assertEquals("content", Files.readString(target))
        assertTrue(Files.notExists(outside.resolve("pack.zip"), LinkOption.NOFOLLOW_LINKS))
    }

    @Test
    fun `two links to the same file are distinct packs`(@TempDir root: Path) {
        Files.writeString(root.resolve("real.zip"), "content")
        Files.createSymbolicLink(Files.createDirectory(root.resolve("a")).resolve("pack.zip"), Path.of("../real.zip"))
        Files.createSymbolicLink(Files.createDirectory(root.resolve("b")).resolve("pack.zip"), Path.of("../real.zip"))

        val plan = PackMovePlan.of(listOf("file/a/pack.zip"), { root.resolve("a/pack.zip") }, root.resolve("b"))

        assertEquals(1, plan.moves.size)
    }

    @Test
    fun `overwriting the target of a link is refused`(@TempDir root: Path) {
        val real = Files.writeString(root.resolve("pack.zip"), "content")
        val link = Files.createSymbolicLink(Files.createDirectory(root.resolve("a")).resolve("pack.zip"), Path.of("../pack.zip"))

        assertThrows(IOException::class.java) { PackFiles.move(link, real, overwrite = true) }

        assertEquals("content", Files.readString(real))
        assertTrue(Files.isSymbolicLink(link))
    }

    @Test
    fun `moving a link leaves unrelated dotfiles beside the target alone`(@TempDir root: Path) {
        Files.writeString(root.resolve("real.zip"), "content")
        val link = Files.createSymbolicLink(Files.createDirectory(root.resolve("a")).resolve("pack.zip"), Path.of("../real.zip"))
        val dest = Files.createDirectories(root.resolve("b/c"))
        val bystander = Files.writeString(dest.resolve(".polyplus-link-0-pack.zip"), "keep")

        PackFiles.move(link, dest.resolve("pack.zip"), overwrite = false)

        assertEquals("content", Files.readString(dest.resolve("pack.zip")))
        assertEquals("keep", Files.readString(bystander))
    }

    @Test
    fun `a link through a symlinked folder and dot-dot keeps its real target`(@TempDir root: Path) {
        Files.createDirectories(root.resolve("deep/x"))
        Files.writeString(root.resolve("deep/real.zip"), "content")
        Files.writeString(root.resolve("real.zip"), "decoy")
        Files.createSymbolicLink(root.resolve("via"), root.resolve("deep/x"))
        val link = Files.createSymbolicLink(Files.createDirectory(root.resolve("a")).resolve("pack.zip"), Path.of("../via/../real.zip"))
        val target = root.resolve("b/c/pack.zip")

        PackFiles.move(link, target, overwrite = false)

        assertEquals("content", Files.readString(target))
    }

    @Test
    fun `an absolute move writes absolute link text`(@TempDir root: Path) {
        Files.writeString(root.resolve("real.zip"), "content")
        val link = Files.createSymbolicLink(Files.createDirectory(root.resolve("a")).resolve("pack.zip"), Path.of("../real.zip"))
        val target = root.resolve("pack.zip")

        PackFiles.move(link, target, overwrite = false, absoluteLink = true)

        assertTrue(Files.readSymbolicLink(target).isAbsolute)
        assertEquals("content", Files.readString(target))
    }

    @Test
    fun `overwriting a link the source chains through is refused`(@TempDir root: Path) {
        Files.writeString(root.resolve("real.zip"), "content")
        val middle = Files.createSymbolicLink(root.resolve("pack.zip"), Path.of("real.zip"))
        val link = Files.createSymbolicLink(Files.createDirectory(root.resolve("a")).resolve("pack.zip"), Path.of("../pack.zip"))

        assertThrows(IOException::class.java) { PackFiles.move(link, middle, overwrite = true) }

        assertEquals("content", Files.readString(middle))
        assertEquals("content", Files.readString(link))
    }

    @Test
    fun `replacing a folder pack with a link into it is refused`(@TempDir root: Path) {
        val outer = Files.createDirectory(root.resolve("big"))
        Files.writeString(outer.resolve("pack.mcmeta"), "{}")
        Files.writeString(Files.createDirectory(outer.resolve("inner")).resolve("pack.mcmeta"), "{}")
        val link = Files.createSymbolicLink(Files.createDirectory(root.resolve("a")).resolve("big"), Path.of("../big/inner"))

        assertThrows(IOException::class.java) { PackFiles.move(link, outer, overwrite = true) }

        assertTrue(Files.isRegularFile(outer.resolve("inner/pack.mcmeta")))
        assertTrue(Files.isSymbolicLink(link))
    }

    //? if > 1.8.9 {
    @Test
    fun `a link chain is not moved to the top level`(@TempDir root: Path) {
        Files.writeString(root.resolve("real.zip"), "content")
        Files.createSymbolicLink(root.resolve("middle.zip"), Path.of("real.zip"))
        val link = Files.createSymbolicLink(Files.createDirectory(root.resolve("a")).resolve("pack.zip"), Path.of("../middle.zip"))

        assertThrows(IOException::class.java) { PackFiles.move(link, root.resolve("pack.zip"), overwrite = false, absoluteLink = true) }

        assertEquals("content", Files.readString(link))
    }

    @Test
    fun `a link to a file not named zip is not moved to the top level`(@TempDir root: Path) {
        Files.writeString(root.resolve("real.bin"), "content")
        val link = Files.createSymbolicLink(Files.createDirectory(root.resolve("a")).resolve("pack.zip"), Path.of("../real.bin"))

        assertThrows(IOException::class.java) { PackFiles.move(link, root.resolve("pack.zip"), overwrite = false, absoluteLink = true) }

        assertEquals("content", Files.readString(link))
    }
    //?} else {
    /*@Test
    fun `a link chain moves to the top level`(@TempDir root: Path) {
        Files.writeString(root.resolve("real.zip"), "content")
        Files.createSymbolicLink(root.resolve("middle.zip"), Path.of("real.zip"))
        val link = Files.createSymbolicLink(Files.createDirectory(root.resolve("a")).resolve("pack.zip"), Path.of("../middle.zip"))

        PackFiles.move(link, root.resolve("pack.zip"), overwrite = false, absoluteLink = true)

        assertEquals("content", Files.readString(root.resolve("pack.zip")))
    }
    *///?}
}
