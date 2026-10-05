package org.polyfrost.polyplus.test

//? if > 1.8.9 {
import net.minecraft.SharedConstants
import net.minecraft.network.chat.Component
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.privacy.RichTextPrivacy

class RichTextPrivacyTest {

    companion object {
        private val DEBUGIFY = setOf("debugify.name")

        @JvmStatic
        @BeforeAll
        fun setupEnvironment() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `vanilla translations still resolve`() {
        val component = Component.translatable("gui.done")
        assertEquals("Done", RichTextPrivacy.unresolved(component, component.string))
    }

    @Test
    fun `non-Debugify mod translations still resolve`() {
        val probe = Component.translatableWithFallback("shulkerboxtooltip.config.title", "[NO_SHULKERBOXTOOLTIP_CONFIG]")
        assertEquals("[NO_SHULKERBOXTOOLTIP_CONFIG]", RichTextPrivacy.unresolved(probe, probe.string))
    }

    @Test
    fun `an allowed key with plain args still formats normally`() {
        val component = Component.translatable("chat.type.text", "bob", "hello")
        assertEquals("<bob> hello", RichTextPrivacy.unresolved(component, component.string))
    }

    @Test
    fun `a Debugify key answers with its fallback, exactly like a client without it`() {
        val probe = Component.translatableWithFallback("debugify.name", "[NO_DEBUGIFY]")
        assertEquals("[NO_DEBUGIFY]", RichTextPrivacy.unresolved(probe, DEBUGIFY))
    }

    @Test
    fun `a Debugify key with no fallback answers with the key`() {
        assertEquals("debugify.name", RichTextPrivacy.unresolved(Component.translatable("debugify.name"), DEBUGIFY))
    }

    @Test
    fun `a Debugify fallback formats its args exactly like a client without it`() {
        val args = arrayOf<Any>("a", Component.literal("b"))
        for (fallback in listOf("[NO %s %2\$s%%]", "[NO %3\$s]", "[NO %d]")) {
            val probe = Component.translatableWithFallback("debugify.name", fallback, *args)
            val untranslated = Component.translatableWithFallback("polyplus.test.untranslated", fallback, *args)
            assertEquals(untranslated.string, RichTextPrivacy.unresolved(probe, DEBUGIFY), fallback)
        }
    }

    @Test
    fun `a Debugify keybind does not resolve`() {
        assertEquals("debugify.name", RichTextPrivacy.unresolved(Component.keybind("debugify.name"), DEBUGIFY))
    }

    @Test
    fun `args cannot smuggle a Debugify key through a vanilla key`() {
        val smuggler = Component.translatable("chat.type.text", Component.translatable("debugify.name"), Component.literal("hi"))
        assertEquals("<debugify.name> hi", RichTextPrivacy.unresolved(smuggler, DEBUGIFY))
    }

    @Test
    fun `a vanilla key still resolves while Debugify is blocked`() {
        assertEquals("Done", RichTextPrivacy.unresolved(Component.translatable("gui.done"), DEBUGIFY))
    }

    @Test
    fun `literal text and siblings survive`() {
        val component = Component.literal("hello ")
            .append(Component.translatable("gui.done"))
            .append(Component.literal("!"))
        assertEquals("hello Done!", RichTextPrivacy.unresolved(component, component.string))
    }

    @Test
    fun `text adjusted by another mod survives when nothing is blocked`() {
        assertEquals("[adjusted]", RichTextPrivacy.unresolved(Component.translatable("gui.done"), "[adjusted]", DEBUGIFY))
    }

    @Test
    fun `a blocked key overrides text adjusted by another mod`() {
        assertEquals("gui.done", RichTextPrivacy.unresolved(Component.translatable("gui.done"), "[adjusted]", setOf("gui.done")))
    }
}
//?}
