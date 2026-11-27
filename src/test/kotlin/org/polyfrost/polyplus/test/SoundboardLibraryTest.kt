package org.polyfrost.polyplus.test

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.party.soundboard.Soundboard
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class SoundboardLibraryTest {
    companion object {
        const val TRICKY = "a,b=c% d.ogg"
        const val PLAIN = "plain.wav"
        const val LOUD = "loud.wav"
        const val LEGACY = "50%.wav"
        const val STARRED = "x y+z.mp3"

        @JvmStatic
        @BeforeAll
        fun seedConfig() {
            PolyPlusConfig.voiceSoundVolumes = "a%2Cb%3Dc%25+d.ogg=1.5,plain.wav=0.5,loud.wav=9,50%.wav=0.75,=1,broken"
            PolyPlusConfig.voiceSoundFavorites = "a%2Cb%3Dc%25+d.ogg, x+y%2Bz.mp3 ,,"
        }
    }

    private val seeded = mapOf(TRICKY to 1.5f, PLAIN to 0.5f, LOUD to 2f, LEGACY to 0.75f)

    @AfterEach
    fun restore() {
        for (id in Soundboard.volumes.keys - seeded.keys) Soundboard.setVolume(id, 1f)
        for ((id, volume) in seeded) Soundboard.setVolume(id, volume)
    }

    private fun decodeVolumes(raw: String): Map<String, Float> = raw.split(',').associate {
        URLDecoder.decode(it.substringBefore('='), StandardCharsets.UTF_8) to it.substringAfter('=').toFloat()
    }

    @Test
    fun `saved volumes load with commas, equals, percents and spaces in names`() {
        assertEquals(seeded, Soundboard.volumes)
        assertEquals(1.5f, Soundboard.volumeOf(TRICKY))
        assertEquals(1f, Soundboard.volumeOf("missing.wav"))
    }

    @Test
    fun `saved favourites load with tricky names`() {
        assertEquals(setOf(TRICKY, STARRED), Soundboard.favorites)
        assertTrue(Soundboard.isFavorite(TRICKY))
        assertFalse(Soundboard.isFavorite(PLAIN))
    }

    @Test
    fun `favourites sort first`() {
        val sounds = listOf(PLAIN, STARRED, TRICKY).map { Soundboard.Sound(it, it, Path.of(it)) }

        assertEquals(listOf(STARRED, TRICKY, PLAIN), Soundboard.ordered(sounds).map { it.id })
    }

    @Test
    fun `changing a volume saves every name so it reads back the same`() {
        val name = "new, = %+.wav"
        Soundboard.setVolume(name, 0.25f)

        val raw = PolyPlusConfig.voiceSoundVolumes
        assertEquals(5, raw.split(',').size, raw)
        assertEquals(seeded + (name to 0.25f), decodeVolumes(raw))
    }

    @Test
    fun `volumes are clamped and the default volume is not stored`() {
        Soundboard.setVolume(PLAIN, 5f)
        assertEquals(Soundboard.MAX_VOLUME, Soundboard.volumeOf(PLAIN))

        Soundboard.setVolume(PLAIN, 1f)
        assertFalse(PLAIN in Soundboard.volumes)
        assertFalse(decodeVolumes(PolyPlusConfig.voiceSoundVolumes).containsKey(PLAIN))
    }
}
