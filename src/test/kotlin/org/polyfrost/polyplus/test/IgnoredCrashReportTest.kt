package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.PolyPlusSentry

class IgnoredCrashReportTest {

    private companion object {
        val THROWABLE_LINE = Regex("(?m)^[\\w.\$]+(?:Exception|Error|Throwable)(?::.*)?$")

        val CRASHPATCH_REPORT = """
            ---- Minecraft Crash Report ----
            // Don't be sad. I'll do better next time, I promise!

            Time: 2026-08-01 23:53:16
            Description: Unexpected error

            java.lang.RuntimeException: Crash requested by CrashPatch
            	at net.minecraft.client.Minecraft.handler${'$'}blj000${'$'}crashpatch${'$'}debugCrash(Minecraft.java:9637)
            	at net.minecraft.client.Minecraft.runTick(Minecraft.java:1288)
        """.trimIndent()

        val LOCALISED_CRASHPATCH_REPORT = """
            ---- Minecraft Crash Report ----
            // Don't be sad. I'll do better next time, I promise!

            Time: 2026-08-01 23:53:16
            Description: 意外错误

            java.lang.RuntimeException: 由 CrashPatch 请求触发的崩溃
            	at net.minecraft.client.Minecraft.handler${'$'}bna000${'$'}crashpatch${'$'}debugCrash(Minecraft.java:14154)
            	at net.minecraft.client.Minecraft.runTick(Minecraft.java:1391)
        """.trimIndent()

        val DEBUG_CRASH_REPORT = """
            ---- Minecraft Crash Report ----

            Time: 2026-08-01 23:53:16
            Description: Manually triggered debug crash

            java.lang.Throwable: Manually triggered debug crash
            	at net.minecraft.client.KeyboardHandler.tick(KeyboardHandler.java:676)
        """.trimIndent()

        val LOCALISED_DEBUG_CRASH_REPORT = """
            ---- Minecraft Crash Report ----

            Time: 2026-08-01 23:53:16
            Description: 手动触发的调试崩溃

            java.lang.Throwable: 手动触发的调试崩溃
            	at net.minecraft.class_309.method_1454(class_309.java:507)
        """.trimIndent()

        val SHUTDOWN_WATCHDOG_REPORT = """
            ---- Minecraft Crash Report ----

            Time: 2026-08-01 23:53:16
            Description: Client shutdown

            java.lang.Error: Watchdog (Client shutdown took too long)
            	at net.minecraft.client.ClientShutdownWatchdog.run(ClientShutdownWatchdog.java:41)
        """.trimIndent()

        val SERVER_WATCHDOG_REPORT = """
            ---- Minecraft Crash Report ----

            Time: 2026-08-01 23:53:16
            Description: Watching Server

            java.lang.Error: Watchdog (Took too long to tick)
            	at net.minecraft.server.dedicated.ServerWatchdog.run(ServerWatchdog.java:63)
        """.trimIndent()

        val BARE_WATCHDOG_REPORT = """
            ---- Minecraft Crash Report ----

            Time: 2026-08-01 23:53:16
            Description: Client shutdown

            java.lang.Error: Watchdog
            	at java.base/java.lang.Object.wait0(Native Method)
        """.trimIndent()

        val OUT_OF_MEMORY_REPORT = """
            ---- Minecraft Crash Report ----

            Time: 2026-08-01 23:53:16
            Description: Rendering overlay

            java.lang.RuntimeException: Failed to allocate texture
            	at com.mojang.blaze3d.platform.NativeImage.<init>(NativeImage.java:88)
            Caused by: java.lang.OutOfMemoryError: Java heap space
            	at java.base/java.nio.HeapByteBuffer.<init>(HeapByteBuffer.java:64)
        """.trimIndent()

        val SKYHANNI_REPORT = """
            ---- Minecraft Crash Report ----

            Time: 2026-08-01 23:53:16
            Description: Ticking client

            java.lang.RuntimeException: SkyHanni crash
            	at at.hannibal2.skyhanni.features.misc.CrashOnTick.onTick(CrashOnTick.kt:24)
        """.trimIndent()

        val GENUINE_REPORT = """
            ---- Minecraft Crash Report ----

            Time: 2026-08-01 23:53:16
            Description: Rendering screen

            java.lang.NullPointerException: Cannot invoke "net.minecraft.world.entity.player.Inventory.getSelected()"
            	at org.example.mod.HudRenderer.render(HudRenderer.java:57)
            	at net.minecraft.client.gui.Gui.render(Gui.java:204)
        """.trimIndent()
    }

    private fun frame(className: String, methodName: String) =
        StackTraceElement(className, methodName, "${className.substringAfterLast('.')}.java", 1)

    private fun <T : Throwable> T.withTrace(vararg frames: StackTraceElement): T = apply { stackTrace = frames }

    private fun summary(report: String): String {
        val description = report.lineSequence()
            .first { it.startsWith("Description:") }
            .removePrefix("Description:")
            .trim()
        val throwable = THROWABLE_LINE.find(report)?.value?.trim() ?: return description
        return "$description: $throwable"
    }

    @Test
    fun `ignores crashes the player asked for`() {
        assertTrue(PolyPlusSentry.isIgnoredCrashReport(summary(CRASHPATCH_REPORT), CRASHPATCH_REPORT))
        assertTrue(PolyPlusSentry.isIgnoredCrashReport(summary(DEBUG_CRASH_REPORT), DEBUG_CRASH_REPORT))
        assertTrue(PolyPlusSentry.isIgnoredCrashReport(summary(SKYHANNI_REPORT), SKYHANNI_REPORT))
    }

    @Test
    fun `ignores crashes the player asked for when the message is translated`() {
        assertTrue(
            PolyPlusSentry.isIgnoredCrashReport(summary(LOCALISED_CRASHPATCH_REPORT), LOCALISED_CRASHPATCH_REPORT),
        )
        assertTrue(
            PolyPlusSentry.isIgnoredCrashReport(summary(LOCALISED_DEBUG_CRASH_REPORT), LOCALISED_DEBUG_CRASH_REPORT),
        )
    }

    @Test
    fun `matches the translated throwable the live path is handed`() {
        val crashPatch = RuntimeException("由 CrashPatch 请求触发的崩溃").withTrace(
            frame("net.minecraft.class_310", "handler${'$'}bna000${'$'}crashpatch${'$'}debugCrash"),
            frame("net.minecraft.class_310", "method_1523"),
        )
        assertTrue(PolyPlusSentry.isDeliberateCrash(crashPatch))

        val debugCrash = Throwable("手动触发的调试崩溃").withTrace(
            frame("net.minecraft.class_309", "method_1454"),
            frame("net.minecraft.class_310", "method_1523"),
        )
        assertTrue(PolyPlusSentry.isDeliberateCrash(RuntimeException("崩溃", debugCrash)))

        val skyHanni = RuntimeException("SkyHanni crash").withTrace(
            frame("at.hannibal2.skyhanni.features.misc.CrashOnDeath", "onDeath"),
        )
        assertTrue(PolyPlusSentry.isDeliberateCrash(skyHanni))
    }

    @Test
    fun `keeps genuine crashes out of the keyboard handler`() {
        val genuine = NullPointerException("Cannot invoke getSelected()").withTrace(
            frame("org.example.mod.KeybindHandler", "onKey"),
            frame("net.minecraft.class_309", "method_1454"),
        )
        assertFalse(PolyPlusSentry.isDeliberateCrash(genuine))
    }

    @Test
    fun `ignores watchdogs and memory exhaustion`() {
        assertTrue(PolyPlusSentry.isIgnoredCrashReport(summary(SHUTDOWN_WATCHDOG_REPORT), SHUTDOWN_WATCHDOG_REPORT))
        assertTrue(PolyPlusSentry.isIgnoredCrashReport(summary(SERVER_WATCHDOG_REPORT), SERVER_WATCHDOG_REPORT))
        assertTrue(PolyPlusSentry.isIgnoredCrashReport(summary(BARE_WATCHDOG_REPORT), BARE_WATCHDOG_REPORT))
        assertTrue(PolyPlusSentry.isIgnoredCrashReport(summary(OUT_OF_MEMORY_REPORT), OUT_OF_MEMORY_REPORT))
    }

    @Test
    fun `keeps crashes worth fixing`() {
        assertFalse(PolyPlusSentry.isIgnoredCrashReport(summary(GENUINE_REPORT), GENUINE_REPORT))
    }

    @Test
    fun `matches the description alone, as the live path has it`() {
        assertTrue(PolyPlusSentry.isIgnoredCrashReport("Unexpected error: java.lang.RuntimeException: Crash requested by CrashPatch"))
        assertTrue(PolyPlusSentry.isIgnoredCrashReport("Client shutdown: java.lang.Error: Watchdog (took too long)"))
        assertTrue(PolyPlusSentry.isIgnoredCrashReport("[HARD CRASH] Client shutdown: java.lang.Error: Watchdog"))
        assertFalse(PolyPlusSentry.isIgnoredCrashReport(summary(GENUINE_REPORT)))
        assertFalse(PolyPlusSentry.isIgnoredCrashReport(null))
    }
}
