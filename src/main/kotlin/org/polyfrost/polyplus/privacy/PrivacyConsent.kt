package org.polyfrost.polyplus.privacy

import net.fabricmc.loader.api.FabricLoader
import java.io.File

object PrivacyConsent {
    enum class State { UNSET, ACCEPTED, DECLINED }

    private val lock = Any()

    @Volatile
    private var loaded = false

    @Volatile
    private var state: State = State.UNSET

    @Volatile
    private var termsVersion: Int = 0

    @Volatile
    private var privacyVersion: Int = 0

    private val file: File
        get() = File(FabricLoader.getInstance().gameDir.toFile(), "polyplus/privacy.json")

    val managedByLauncher: Boolean
        get() = LauncherEnvironment.isOneClient && LauncherEnvironment.launcherAcceptedTerms() != false

    @JvmStatic
    fun state(): State {
        if (!loaded) load()
        return state
    }

    @JvmStatic
    fun acceptedTermsVersion(): Int {
        if (!loaded) load()
        return termsVersion
    }

    @JvmStatic
    fun acceptedPrivacyVersion(): Int {
        if (!loaded) load()
        return privacyVersion
    }

    @JvmStatic
    fun needsPrompt(): Boolean = !managedByLauncher && state() == State.UNSET

    @JvmStatic
    fun allowsOnlineServices(): Boolean = when (state()) {
        State.ACCEPTED -> true
        State.DECLINED -> false
        State.UNSET -> managedByLauncher
    }

    @JvmStatic
    fun accept(terms: Int = 0, privacy: Int = 0) {
        if (!loaded) load()
        synchronized(lock) {
            state = State.ACCEPTED
            if (terms > 0) termsVersion = terms
            if (privacy > 0) privacyVersion = privacy
            save()
        }
    }

    @JvmStatic
    fun decline() {
        if (!loaded) load()
        synchronized(lock) {
            state = State.DECLINED
            save()
        }
    }

    private fun load() {
        synchronized(lock) {
            if (loaded) return
            loaded = true
            val text = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull() ?: return
            state = when (stringField(text, "state")?.lowercase()) {
                "accepted" -> State.ACCEPTED
                "declined" -> State.DECLINED
                else -> State.UNSET
            }
            termsVersion = intField(text, "terms_version") ?: 0
            privacyVersion = intField(text, "privacy_version") ?: 0
        }
    }

    private fun save() {
        runCatching {
            val target = file
            target.parentFile?.mkdirs()
            target.writeText(
                """
                {
                  "state": "${state.name.lowercase()}",
                  "terms_version": $termsVersion,
                  "privacy_version": $privacyVersion,
                  "recorded_at": ${System.currentTimeMillis()}
                }
                """.trimIndent(),
            )
        }
    }

    private fun stringField(text: String, name: String): String? =
        Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.getOrNull(1)

    private fun intField(text: String, name: String): Int? =
        Regex("\"$name\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
}
