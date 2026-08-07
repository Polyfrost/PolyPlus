package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.PolyPlusSentry

/**
 * Covers the crash reports that are never worth reporting, matched on text.
 *
 * The live path recognises these from the throwable it is handed; the same verdict has to be
 * reachable from the crash report file left in `crash-reports/`, which is all the postmortem
 * uploader has to go on when it picks the file up on the next launch.
 */
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

        val DEBUG_CRASH_REPORT = """
            ---- Minecraft Crash Report ----

            Time: 2026-08-01 23:53:16
            Description: Manually triggered debug crash

            java.lang.Throwable: Manually triggered debug crash
            	at net.minecraft.client.KeyboardHandler.keyPress(KeyboardHandler.java:412)
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

    /** Builds what the uploader's `summarize` makes of a crash report: `<description>: <throwable>`. */
    private fun summary(report: String): String {
        val description = report.lineSequence()
            .first { it.startsWith("Description:") }
            .removePrefix("Description:")
            .trim()
        val throwable = THROWABLE_LINE.find(report)?.value?.trim() ?: return description
        return "$description: $throwable"
    }

    private fun throwableWith(vararg frames: Pair<String, String>): Throwable =
        RuntimeException("boom").apply {
            stackTrace = frames
                .map { (className, methodName) -> StackTraceElement(className, methodName, null, -1) }
                .toTypedArray()
        }

    @Test
    fun `ignores crashes the player asked for`() {
        assertTrue(PolyPlusSentry.isIgnoredCrashReport(summary(CRASHPATCH_REPORT), CRASHPATCH_REPORT))
        assertTrue(PolyPlusSentry.isIgnoredCrashReport(summary(DEBUG_CRASH_REPORT), DEBUG_CRASH_REPORT))
        assertTrue(PolyPlusSentry.isIgnoredCrashReport(summary(SKYHANNI_REPORT), SKYHANNI_REPORT))
    }

    @Test
    fun `ignores watchdogs and memory exhaustion`() {
        assertTrue(PolyPlusSentry.isIgnoredCrashReport(summary(SHUTDOWN_WATCHDOG_REPORT), SHUTDOWN_WATCHDOG_REPORT))
        assertTrue(PolyPlusSentry.isIgnoredCrashReport(summary(SERVER_WATCHDOG_REPORT), SERVER_WATCHDOG_REPORT))
        assertTrue(PolyPlusSentry.isIgnoredCrashReport(summary(OUT_OF_MEMORY_REPORT), OUT_OF_MEMORY_REPORT))
    }

    @Test
    fun `keeps crashes worth fixing`() {
        assertFalse(PolyPlusSentry.isIgnoredCrashReport(summary(GENUINE_REPORT), GENUINE_REPORT))
    }

    @Test
    fun `uploads only crashes PolyPlus appears in`() {
        val otherModCrash = """
            ---- Minecraft Crash Report ----

            Description: Initializing game

            java.lang.RuntimeException: Could not execute entrypoint stage 'main' due to errors, provided by 'controlify' at 'dev.isxander.controlify.ControlifyBootstrap'!
            	at net.fabricmc.loader.impl.FabricLoaderImpl.invokeEntrypoints(FabricLoaderImpl.java:411)
            	at dev.isxander.controlify.ControlifyBootstrap.onInitialize(ControlifyBootstrap.java:25)
            Caused by: org.spongepowered.asm.mixin.injection.throwables.InjectionError: Critical injection failure: Redirector onKey${'$'}initializeVelocityCipher in krypton.mixins.json:shared.network.pipeline.encryption.ServerLoginNetworkHandlerMixin from mod krypton failed injection check
        """.trimIndent()

        val vanillaCrash = """
            ---- Minecraft Crash Report ----

            Description: Unexpected error

            java.lang.NullPointerException: Cannot invoke "net.minecraft.client.network.ClientPlayerEntity.getInventory()" because "this.client.player" is null
            	at net.minecraft.client.network.ClientPlayerInteractionManager.syncSelectedSlot(ClientPlayerInteractionManager.java:308)
            	at net.minecraft.client.MinecraftClient.tick(MinecraftClient.java:1888)
        """.trimIndent()

        val driverFatal = """
            # A fatal error has been detected by the Java Runtime Environment:
            #  EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=0x00007ffb6fbe646c
            # Problematic frame:
            # C  [nvoglv64.dll+0xdc646c]
        """.trimIndent()

        assertFalse(PolyPlusSentry.involvesPolyPlus(otherModCrash))
        assertFalse(PolyPlusSentry.involvesPolyPlus(vanillaCrash))
        assertFalse(PolyPlusSentry.involvesPolyPlus(driverFatal))
        assertFalse(PolyPlusSentry.involvesPolyPlus(GENUINE_REPORT))
    }

    @Test
    fun `keeps crashes our own code and mixins are in`() {
        val ourCode = """
            ---- Minecraft Crash Report ----

            Description: Rendering entity

            java.lang.NullPointerException: bone was null
            	at org.polyfrost.polyplus.client.cosmetics.render.CosmeticRenderer.render(CosmeticRenderer.kt:39)
            	at net.minecraft.client.renderer.entity.LivingEntityRenderer.render(LivingEntityRenderer.java:112)
        """.trimIndent()

        val ourMixin = """
            ---- Minecraft Crash Report ----

            Description: Rendering screen

            java.lang.IndexOutOfBoundsException: Index 3 out of bounds for length 0
            	at net.minecraft.client.gui.screens.ChatScreen.handler${'$'}zzk000${'$'}polyplus${'$'}onKeyPressed(ChatScreen.java:8123)
            	at net.minecraft.client.gui.screens.ChatScreen.keyPressed(ChatScreen.java:214)
        """.trimIndent()

        assertTrue(PolyPlusSentry.involvesPolyPlus(ourCode))
        assertTrue(PolyPlusSentry.involvesPolyPlus(ourMixin))
    }

    @Test
    fun `reaches the same verdict from a live throwable`() {
        val foreign = throwableWith(
            "tomeko.legacyskyblock.neu.PetFetcher" to "getIcon",
            "java.util.concurrent.ThreadPoolExecutor" to "runWorker",
        )
        val ourCode = throwableWith(
            "org.polyfrost.polyplus.client.cosmetics.render.CosmeticRenderer" to "render",
            "net.minecraft.client.renderer.entity.LivingEntityRenderer" to "render",
        )
        val ourMixin = throwableWith(
            "net.minecraft.client.gui.screens.ChatScreen" to "handler\$zzk000\$polyplus\$onKeyPressed",
            "net.minecraft.client.gui.screens.ChatScreen" to "keyPressed",
        )

        assertFalse(PolyPlusSentry.involvesPolyPlus(foreign))
        assertTrue(PolyPlusSentry.involvesPolyPlus(ourCode))
        assertTrue(PolyPlusSentry.involvesPolyPlus(ourMixin))
    }

    @Test
    fun `finds us further down the cause chain`() {
        val ours = throwableWith("org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog" to "refreshCatalog")
        val wrapped = throwableWith("java.util.concurrent.CompletableFuture" to "wrapInCompletionException")
            .initCause(ours)

        assertTrue(PolyPlusSentry.involvesPolyPlus(wrapped))
    }

    @Test
    fun `terminates on a self-referencing cause chain`() {
        val foreign = throwableWith("com.ishland.c2me.common.CheckedThreadLocalRandom" to "handleNotOwner")
        val outer = throwableWith("net.minecraft.client.Minecraft" to "runTick").initCause(foreign)
        foreign.initCause(outer)

        assertFalse(PolyPlusSentry.involvesPolyPlus(outer))
    }

    @Test
    fun `matches the description alone, as the live path has it`() {
        assertTrue(PolyPlusSentry.isIgnoredCrashReport("Unexpected error: java.lang.RuntimeException: Crash requested by CrashPatch"))
        assertTrue(PolyPlusSentry.isIgnoredCrashReport("Client shutdown: java.lang.Error: Watchdog (took too long)"))
        assertFalse(PolyPlusSentry.isIgnoredCrashReport(summary(GENUINE_REPORT)))
        assertFalse(PolyPlusSentry.isIgnoredCrashReport(null))
    }
}
