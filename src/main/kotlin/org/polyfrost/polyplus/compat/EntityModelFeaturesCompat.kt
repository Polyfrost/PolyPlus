package org.polyfrost.polyplus.compat

//? if > 1.8.9 {
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.world.entity.Entity
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.lobby.LobbyHerobrine
import java.lang.reflect.Method
import java.util.function.Function

object EntityModelFeaturesCompat {
    private val logger = LogManager.getLogger("PolyPlus/EntityModelFeaturesCompat")
    private const val MOD_ID = "entity_model_features"
    private const val API_CLASS = "traben.entity_model_features.EMFAnimationApi"

    private val present by lazy { FabricLoader.getInstance().isModLoaded(MOD_ID) }
    private val api: Class<*>? by lazy {
        if (!present) return@lazy null
        try {
            Class.forName(API_CLASS)
        } catch (e: Throwable) {
            fail("Entity Model Features has no animation API; emotes may be overridden by its animations", e)
            null
        }
    }
    private val animatedCheck: Method? by lazy { find("isModelAnimatedByEMF", 1) }
    private val currentEntity: Method? by lazy { find("getCurrentEntity", 0) }
    private var registered = false
    private var broken = false

    @JvmStatic
    fun register() {
        if (registered || broken || !present) return
        registered = true
        val method = find("registerPauseCondition", 1) ?: return
        try {
            method.invoke(null, Function<Any?, Boolean> { holdsOwnPose(it) })
        } catch (e: Throwable) {
            fail("Could not keep Entity Model Features animations off emoting players", e)
        }
    }

    @JvmStatic
    fun holdsOwnPose(entity: Any?): Boolean {
        if (entity is PlayerEmotesAccess && entity.`polyplus$emoteController`().isActive) return true
        return entity is Entity && LobbyHerobrine.shows(entity.id)
    }

    @JvmStatic
    fun animatesRoot(model: Any?): Boolean {
        if (model == null || broken || !present) return false
        val check = animatedCheck ?: return false
        return try {
            check.invoke(null, model) == true && !holdsOwnPose(currentEntity?.invoke(null))
        } catch (e: Throwable) {
            fail("Could not read Entity Model Features model state; capes follow the PolyPlus root pose", e)
            false
        }
    }

    private fun find(name: String, parameters: Int): Method? {
        val type = api ?: return null
        return try {
            type.methods.first { it.name == name && it.parameterCount == parameters }
        } catch (e: Throwable) {
            fail("Entity Model Features has no $name; its animations may override PolyPlus poses", e)
            null
        }
    }

    private fun fail(message: String, e: Throwable) {
        if (broken) return
        broken = true
        logger.warn(message, e)
    }
}
//?}
