package org.polyfrost.polyplus.test

import gg.sona.eos.Eos
import gg.sona.eos.EosClientCredentials
import gg.sona.eos.EosInitializeOptions
import gg.sona.eos.EosPlatform
import gg.sona.eos.EosPlatformFlags
import gg.sona.eos.EosPlatformOptions
import gg.sona.eos.EosResult
import gg.sona.eos.common.EosExternalCredentialType
import gg.sona.eos.logging.EosLogCategory
import gg.sona.eos.logging.EosLogLevel
import gg.sona.eos.logging.EosLogging
import java.io.FileInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.polyfrost.polyplus.client.network.eos.EosConstants
import org.polyfrost.polyplus.client.network.eos.EosFailureDiagnosis
import org.polyfrost.polyplus.client.network.eos.EosSdkBridgeImpl

@EnabledIfEnvironmentVariable(named = EosFdCeilingHarness.ARM_ENV, matches = "control|flood")
class EosFdCeilingHarness {
    private val sdkLog = CopyOnWriteArrayList<String>()

    @Volatile private var platform: EosPlatform? = null

    @Test
    fun `EOS reaches Epic with the process holding descriptors above FD_SETSIZE`() {
        val flooding = System.getenv(ARM_ENV) == "flood"
        val openFds = EosFailureDiagnosis.openFileDescriptors()
        assertTrue(openFds != null, "This JVM won't report open descriptors, so the harness cannot run here")

        val padding = mutableListOf<FileInputStream>()
        try {
            if (flooding) floodDescriptors(padding)
            report("open descriptors before EOS starts: ${EosFailureDiagnosis.openFileDescriptors()}")

            val result = attemptLogin()
            report("EOS::Connect::Login answered: $result")
            sdkLog.filter { it.contains("curl", ignoreCase = true) || it.contains("connect", ignoreCase = true) }
                .distinct()
                .take(20)
                .forEach { report("  SDK: $it") }

            assertNotEquals(
                EosResult.NoConnection,
                result,
                "EOS could not reach api.epicgames.dev while holding " +
                    "${EosFailureDiagnosis.openFileDescriptors()} descriptors. If the control arm passed on this " +
                    "same machine, the FD_SETSIZE ceiling is confirmed.",
            )
        } finally {
            padding.forEach { runCatching { it.close() } }
            runCatching { platform?.close() }
            EosSdkBridgeImpl.markSdkRetired()
            runCatching { Eos.shutdown() }
        }
    }

    private fun floodDescriptors(padding: MutableList<FileInputStream>) {
        val target = EosFailureDiagnosis.FD_SETSIZE + HEADROOM
        while ((EosFailureDiagnosis.openFileDescriptors() ?: Long.MAX_VALUE) < target) {
            padding += runCatching { FileInputStream("/dev/null") }.getOrElse {
                throw AssertionError(
                    "Could only open ${padding.size} descriptors before hitting the limit; " +
                        "raise 'ulimit -n' above $target and run the harness again",
                    it,
                )
            }
        }
        report("padded the process up to ${EosFailureDiagnosis.openFileDescriptors()} open descriptors")
    }

    private fun attemptLogin(): EosResult {
        val init = Eos.initialize(EosInitializeOptions.create(EosConstants.PRODUCT_NAME, EosConstants.PRODUCT_VERSION))
        assertTrue(
            init == EosResult.Success || init == EosResult.AlreadyConfigured,
            "EOS::Initialize failed: $init",
        )

        EosLogging.setCallback { message -> sdkLog += message.message }
        EosLogging.setLogLevel(EosLogCategory.AllCategories, EosLogLevel.Info)

        val platform = Eos.createPlatform(
            EosPlatformOptions.create(
                productId = EosConstants.PRODUCT_ID,
                sandboxId = EosConstants.SANDBOX_ID,
                deploymentId = EosConstants.DEPLOYMENT_ID,
                clientCredentials = EosClientCredentials.of(EosConstants.CLIENT_ID, EosConstants.CLIENT_SECRET),
            ).apply { flags = EosPlatformFlags.DisableOverlay or EosPlatformFlags.DisableSocialOverlay },
        )
        this.platform = platform

        val login = platform.connect
            .login(EosExternalCredentialType.OpenIdAccessToken, "harness-token-that-is-deliberately-invalid")
            .orTimeout(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LOGIN_TIMEOUT_SECONDS)
        while (!login.isDone && System.nanoTime() < deadline) {
            platform.tick()
            Thread.sleep(TICK_INTERVAL_MS)
        }

        return runCatching { login.get().result }.getOrElse { error ->
            throw AssertionError("EOS never answered the login within ${LOGIN_TIMEOUT_SECONDS}s", error)
        }
    }

    private fun report(line: String) = println("[eos-fd-harness] $line")

    companion object {
        const val ARM_ENV = "EOS_FD_HARNESS"

        private const val HEADROOM = 64L
        private const val LOGIN_TIMEOUT_SECONDS = 45L
        private const val TICK_INTERVAL_MS = 20L
    }
}
