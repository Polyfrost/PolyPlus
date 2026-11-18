package org.polyfrost.polyplus.compat

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.world.World
import org.apache.logging.log4j.LogManager
import java.lang.reflect.Method

// Cera only applies resource pack lightmaps while a player exists, which the lobby never has
object CeraCompat {
    private val hook = ReflectiveHook(LogManager.getLogger("PolyPlus/CeraCompat"))

    private val lightMaps: Pair<Method, Method>? by lazy {
        if (!Mods.cera) return@lazy null
        hook.attempt("Cera has no lightmap hook; the lobby will ignore resource pack lightmaps", null) {
            val getter = Minecraft::class.java.getMethod("cera\$getLightMaps")
            getter to getter.returnType.getMethod(
                "apply", IntArray::class.java, World::class.java, Float::class.java, Float::class.java, Boolean::class.java, Float::class.java,
            )
        }
    }

    @JvmStatic
    fun applyLightmap(level: World, pixels: IntArray, flicker: Float, tickDelta: Float, gamma: Float) {
        val (getter, apply) = lightMaps ?: return
        if (hook.broken) return
        val nightVision = false
        hook.attempt("Could not apply Cera's lightmap to the lobby", Unit) {
            apply.invoke(getter.invoke(Minecraft.getInstance()), pixels, level, flicker, tickDelta, nightVision, gamma)
        }
    }
}
*///?}
