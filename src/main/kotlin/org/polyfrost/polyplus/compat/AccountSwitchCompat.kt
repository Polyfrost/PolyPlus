package org.polyfrost.polyplus.compat

import org.apache.logging.log4j.LogManager
import java.util.UUID

object AccountSwitchCompat {
    private const val BUS_OWNER = "gg.essential.Essential"
    private const val SESSION = "gg.essential.util.USession"
    private const val REAUTH_EVENT = "gg.essential.event.client.ReAuthEvent"

    private val logger = LogManager.getLogger("PolyPlus/AccountSwitchCompat")

    private val poster: ((UUID, String, String) -> Unit)? by lazy(::resolve)

    @Volatile
    private var failed = false

    fun notifySwitch(uuid: UUID, name: String, accessToken: String) {
        val post = poster ?: return
        runCatching { post(uuid, name, accessToken) }.onFailure(::fail)
    }

    private fun resolve(): ((UUID, String, String) -> Unit)? {
        val loader = AccountSwitchCompat::class.java.classLoader
        val owner = runCatching { Class.forName(BUS_OWNER, false, loader) }.getOrNull() ?: return null
        return runCatching {
            val bus = owner.getField("EVENT_BUS").get(null) ?: error("$BUS_OWNER.EVENT_BUS is null")
            val post = bus.javaClass.getMethod("post", Any::class.java)
            val sessionType = Class.forName(SESSION, false, loader)
            val session = sessionType.getConstructor(UUID::class.java, String::class.java, String::class.java)
            val event = Class.forName(REAUTH_EVENT, false, loader).getConstructor(sessionType)
            val handler: (UUID, String, String) -> Unit = { uuid, name, token ->
                post.invoke(bus, event.newInstance(session.newInstance(uuid, name, token)))
            }
            handler
        }.onFailure(::fail).getOrNull()
    }

    private fun fail(error: Throwable) {
        if (failed) return
        failed = true
        logger.debug("Could not pass the account switch on to another loaded mod", error)
    }
}
