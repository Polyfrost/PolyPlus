package org.polyfrost.polyplus.privacy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.io.File

object PrivacyConsent {
    enum class State { UNSET, ACCEPTED, DECLINED }

    // we cannot use PolyPlusClient's Json instance here because this is read during pre-launch
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class Stored(
        val state: String = "unset",
        @SerialName("terms_version") val termsVersion: Int = 0,
        @SerialName("privacy_version") val privacyVersion: Int = 0,
        @SerialName("recorded_at") val recordedAt: Long = 0,
    )

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
            val stored = runCatching {
                file.takeIf { it.isFile }?.readText()?.let { json.decodeFromString<Stored>(it) }
            }.getOrNull() ?: return
            state = when (stored.state.lowercase()) {
                "accepted" -> State.ACCEPTED
                "declined" -> State.DECLINED
                else -> State.UNSET
            }
            termsVersion = stored.termsVersion
            privacyVersion = stored.privacyVersion
        }
    }

    private fun save() {
        runCatching {
            val target = file
            target.parentFile?.mkdirs()
            target.writeText(
                json.encodeToString(
                    Stored(state.name.lowercase(), termsVersion, privacyVersion, System.currentTimeMillis()),
                ),
            )
        }
    }
}
