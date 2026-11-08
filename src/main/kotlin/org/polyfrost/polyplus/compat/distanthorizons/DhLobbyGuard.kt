package org.polyfrost.polyplus.compat.distanthorizons

//? if dh {
import com.seibel.distanthorizons.api.interfaces.IDhApiUnsafeWrapper
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.Level
import org.polyfrost.polyplus.client.lobby.LOBBY_LOGGER
import org.polyfrost.polyplus.api.lobby.LobbyApi
import org.polyfrost.polyplus.api.lobby.LobbyListener
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap

object DhLobbyGuard : LobbyListener {
    private val seen: MutableSet<Any> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    @Volatile
    private var installed = false

    private var renderState: Any? = null
    private var levelField: Field? = null

    private val hooks = listOf(
        Triple("com.seibel.distanthorizons.core.api.internal.ClientApi", "renderLods", "LOD rendering"),
        Triple("com.seibel.distanthorizons.core.api.internal.ClientApi", "renderDeferredLodsForShaders", "deferred LOD rendering"),
        Triple("com.seibel.distanthorizons.core.api.internal.ClientApi", "renderFadeOpaque", "opaque fade"),
        Triple("com.seibel.distanthorizons.core.api.internal.ClientApi", "renderFadeTransparent", "transparent fade"),
        Triple("com.seibel.distanthorizons.core.api.internal.SharedApi", "applyChunkUpdate", "chunk ingestion"),
        Triple("com.seibel.distanthorizons.core.world.DhClientWorld", "getOrLoadLevel", "client level loading"),
        Triple("com.seibel.distanthorizons.core.world.DhClientWorld", "getLevel", "client level lookup"),
        Triple("com.seibel.distanthorizons.core.world.DhClientServerWorld", "getOrLoadLevel", "singleplayer level loading"),
        Triple("com.seibel.distanthorizons.common.commonMixins.MixinVanillaFogCommon", "cancelFog", "vanilla fog"),
    )

    @JvmStatic
    fun skipRender(): Boolean {
        if (!installed) install()
        return LobbyApi.isRendering()
    }

    @JvmStatic
    fun isLobby(wrapper: Any?): Boolean {
        val level = (wrapper as? IDhApiUnsafeWrapper)?.wrappedMcObject as? Level ?: return false
        if (LobbyApi.isLobbyLevel(level)) {
            if (!installed) install()
            seen.add(level)
            return true
        }
        return seen.isNotEmpty() && seen.contains(level)
    }

    override fun onSceneEnded() {
        val field = levelField ?: return
        val state = renderState ?: return
        runCatching {
            val level = (field.get(state) as? IDhApiUnsafeWrapper)?.wrappedMcObject ?: return
            if (seen.contains(level) || LobbyApi.isLobbyLevel(level as? Level)) field.set(state, null)
        }.onFailure { LOBBY_LOGGER.warn("Distant Horizons render state could not be released after the lobby", it) }
    }

    override fun onSceneBuilt(level: ClientLevel) {
        seen.add(level)
    }

    @Synchronized
    private fun install() {
        if (installed) return
        installed = true
        LobbyApi.addListener(this)
        runCatching {
            val api = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi")
            val state = api.getField("RENDER_STATE").get(null)
            levelField = state.javaClass.getField("clientLevelWrapper")
            renderState = state
        }.onFailure { LOBBY_LOGGER.warn("Distant Horizons render state not found, the lobby level may stay referenced until a world renders") }
        val missing = hooks.filter { (owner, method, _) ->
            runCatching { Class.forName(owner).declaredMethods.none { it.name == method } }.getOrDefault(true)
        }
        if (missing.isNotEmpty()) {
            LOBBY_LOGGER.warn("Distant Horizons lobby hooks missing, the lobby may show DH for: {}", missing.joinToString { it.third })
        }
    }
}
//?}
