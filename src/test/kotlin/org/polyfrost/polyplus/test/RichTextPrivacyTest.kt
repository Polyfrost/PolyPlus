package org.polyfrost.polyplus.test

import net.minecraft.SharedConstants
import net.minecraft.network.chat.Component
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.privacy.RichTextPrivacy

class RichTextPrivacyTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupEnvironment() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `vanilla translations still resolve`() {
        assertEquals("Done", RichTextPrivacy.unresolved(Component.translatable("gui.done")))
    }

    @Test
    fun `mod translations do not resolve`() {
        // getString() would give "RESOLVED" here - that is the leak.
        val leaky = Component.translatableWithFallback("polyplus.hostWorld", "RESOLVED")
        assertEquals("polyplus.hostWorld", RichTextPrivacy.unresolved(leaky))
    }

    @Test
    fun `args cannot smuggle a mod key through a vanilla key`() {
        val smuggler = Component.translatableWithFallback("gui.done", "RESOLVED", Component.translatable("polyplus.hostWorld"))
        assertEquals("gui.done", RichTextPrivacy.unresolved(smuggler))
    }

    @Test
    fun `keybinds do not resolve`() {
        assertEquals("key.polyplus.test", RichTextPrivacy.unresolved(Component.keybind("key.polyplus.test")))
    }

    @Test
    fun `literal text and siblings survive`() {
        val component = Component.literal("hello ")
            .append(Component.translatable("polyplus.hostWorld"))
            .append(Component.literal("!"))
        assertEquals("hello polyplus.hostWorld!", RichTextPrivacy.unresolved(component))
    }
}
