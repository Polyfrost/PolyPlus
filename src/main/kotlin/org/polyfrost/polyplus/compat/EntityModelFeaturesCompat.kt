package org.polyfrost.polyplus.compat

//? if > 1.8.9 {
import net.minecraft.world.entity.Entity
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.lobby.LobbyHerobrine
import java.lang.reflect.Method
import java.util.function.Function

object EntityModelFeaturesCompat {
    private const val API_CLASS = "traben.entity_model_features.EMFAnimationApi"

    private val hook = ReflectiveHook(LogManager.getLogger("PolyPlus/EntityModelFeaturesCompat"))
    private val api: Class<*>? by lazy {
        if (!Mods.entityModelFeatures) return@lazy null
        hook.attempt("Entity Model Features has no animation API; emotes may be overridden by its animations", null) {
            Class.forName(API_CLASS)
        }
    }
    private val animatedCheck: Method? by lazy { find("isModelAnimatedByEMF", 1) }
    private val currentEntity: Method? by lazy { find("getCurrentEntity", 0) }
    private var registered = false

    @JvmStatic
    fun register() {
        if (registered || hook.broken || !Mods.entityModelFeatures) return
        registered = true
        val method = find("registerPauseCondition", 1) ?: return
        hook.attempt("Could not keep Entity Model Features animations off emoting players", Unit) {
            method.invoke(null, Function<Any?, Boolean> { holdsOwnPose(it) })
        }
    }

    @JvmStatic
    fun holdsOwnPose(entity: Any?): Boolean {
        if (entity is PlayerEmotesAccess && entity.`polyplus$emoteController`().isActive) return true
        return entity is Entity && LobbyHerobrine.shows(entity.id)
    }

    @JvmStatic
    fun animatesRoot(model: Any?): Boolean {
        if (model == null || hook.broken || !Mods.entityModelFeatures) return false
        val check = animatedCheck ?: return false
        return hook.attempt("Could not read Entity Model Features model state; capes follow the PolyPlus root pose", false) {
            check.invoke(null, model) == true && !holdsOwnPose(currentEntity?.invoke(null))
        }
    }

    private fun find(name: String, parameters: Int): Method? {
        val type = api ?: return null
        return hook.attempt("Entity Model Features has no $name; its animations may override PolyPlus poses", null) {
            Reflect.methodByArity(type, name, parameters)
        }
    }
}
//?}
