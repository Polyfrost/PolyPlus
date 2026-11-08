package org.polyfrost.polyplus.compat

import org.apache.logging.log4j.LogManager
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.UUID

object LinkedModOutfits {
    private const val ROOT = "gg.essential."
    private const val CHECK_INTERVAL = 20
    private const val SKIN_OVERRIDE = "skinOverriddenByServer"

    private val logger = LogManager.getLogger("PolyPlus/LinkedModOutfits")
    private val hook = ReflectiveHook(logger)

    private class Hooks(
        val playerType: Class<*>,
        val source: Method,
        val setSource: Method,
        val untracked: Method,
        val instance: Method,
        val connection: Method,
        val cosmetics: Method,
        val accountOutfits: Method,
    ) {
        private var visible: Method? = null
        private var skinOverride: Field? = null
        private var skinOverrideLooked = false

        fun skinOverridden(player: Any): Boolean {
            if (!skinOverrideLooked) {
                skinOverrideLooked = true
                skinOverride = Reflect.field(player.javaClass, SKIN_OVERRIDE)
            }
            return skinOverride?.getBoolean(player) == true
        }

        fun accountState(uuid: UUID): Any? {
            val manager = accountOutfits.invoke(cosmetics.invoke(connection.invoke(instance.invoke(null)))) ?: return null
            val method = visible ?: manager.javaClass.getMethod("getVisibleCosmeticsState", UUID::class.java).also { visible = it }
            return method.invoke(manager, uuid)
        }
    }

    private val hooks: Hooks? by lazy {
        runCatching(::resolve).onFailure { logger.debug("No linked outfit store", it) }.getOrNull()
    }

    @JvmStatic
    fun tick(player: Any, uuid: UUID, age: Int) {
        if (hook.broken || age % CHECK_INTERVAL != 1) return
        val hooks = hooks ?: return
        if (!hooks.playerType.isInstance(player)) return
        try {
            if (hooks.skinOverridden(player)) return
            val current = hooks.source.invoke(player) ?: return
            if ((hooks.untracked.invoke(current) as? Map<*, *>)?.isNotEmpty() != false) return
            val account = hooks.accountState(uuid) ?: return
            if ((hooks.untracked.invoke(account) as? Map<*, *>).isNullOrEmpty()) return
            hooks.setSource.invoke(player, account)
        } catch (e: Throwable) {
            hook.fail("Couldn't show another mod's outfit on a player", e)
        }
    }

    private fun resolve(): Hooks? {
        val loader = LinkedModOutfits::class.java.classLoader
        fun find(name: String): Class<*> = Class.forName(ROOT + name, false, loader)
        val playerType = find("mixins.impl.client.entity.AbstractClientPlayerExt")
        val state = find("gui.elementa.state.v2.State")
        val main = find("Essential")
        val instance = main.getMethod("getInstance")
        val connection = instance.returnType.getMethod("getConnectionManager")
        val cosmetics = connection.returnType.getMethod("getCosmeticsManager")
        val accountOutfits = cosmetics.returnType.getMethod("getInfraEquippedOutfitsManager")
        return Hooks(
            playerType = playerType,
            source = playerType.getMethod("getCosmeticsSource"),
            setSource = playerType.getMethod("setCosmeticsSource", state),
            untracked = state.getMethod("getUntracked"),
            instance = instance,
            connection = connection,
            cosmetics = cosmetics,
            accountOutfits = accountOutfits,
        )
    }
}
