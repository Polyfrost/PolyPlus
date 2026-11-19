package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import com.mojang.blaze3d.platform.NativeImage
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerSkin
//?} else {
/*import net.minecraft.client.resources.PlayerSkin
*///?}
import org.slf4j.LoggerFactory

internal object LivePlayerMods {
    private val LOG = LoggerFactory.getLogger("polyplus/live-player")
    private val warned = HashSet<String>()

    private fun warnOnce(key: String, error: Throwable) {
        if (warned.add(key)) LOG.warn("[live-player] {} compat failed", key, error)
    }

    private fun classOrNull(name: String): Class<*>? =
        runCatching { Class.forName(name, false, LivePlayerMods::class.java.classLoader) }.getOrNull()

    private val mountOpacityHook: Class<*>? = runCatching { Class.forName(MOUNT_OPACITY_HOOK) }.getOrNull()

    private val mountOpacitySetter: MethodHandle? = runCatching {
        val hook = mountOpacityHook ?: return@runCatching null
        MethodHandles.lookup()
            .findVirtual(hook, "mountopacity\$setOpacity", MethodType.methodType(Void.TYPE, Float::class.javaPrimitiveType))
            .asType(MethodType.methodType(Void.TYPE, Any::class.java, Float::class.javaPrimitiveType))
    }.getOrNull()

    fun mountOpacity(state: Any, opacity: Float) {
        if (mountOpacityHook?.isInstance(state) != true) return
        mountOpacitySetter?.invokeExact(state, opacity)
    }

    private val transitionSetter: Method? = runCatching {
        Class.forName(TRANSITION_EXTENDER).getMethod("setTransitionEntity", Entity::class.java)
    }.getOrNull()

    fun attachEntity(state: Any, entity: Entity?) {
        val setter = transitionSetter ?: return
        if (!setter.declaringClass.isInstance(state)) return
        runCatching { setter.invoke(state, entity) }.onFailure { warnOnce("transition", it) }
    }

    private class Wavey(
        val config: Field,
        val movement: Field,
        val vanilla: Any,
        val simulated: Any,
        val wind: Field,
        val waves: Any,
        val holder: Class<*>,
        val update: Method,
        val gravityRequest: Method,
        val simulate: Method,
        val delegate: Constructor<*>,
    )

    private val wavey: Wavey? by lazy {
        runCatching {
            val base = classOrNull(WAVEY_MOD_BASE) ?: return@runCatching null
            val config = base.getField("config")
            val movementType = Class.forName(WAVEY_MOVEMENT)
            val configType = Class.forName(WAVEY_CONFIG)
            val movement = configType.getField("capeMovement")
            val windType = Class.forName(WAVEY_WIND)
            val holder = Class.forName(WAVEY_HOLDER)
            val player = Class.forName(WAVEY_PLAYER)
            Wavey(
                config,
                movement,
                movementType.enumConstants.first { (it as Enum<*>).name == "VANILLA" },
                movementType.enumConstants.first { (it as Enum<*>).name == "BASIC_SIMULATION_3D" },
                configType.getField("windMode"),
                windType.enumConstants.first { (it as Enum<*>).name == "WAVES" },
                holder,
                holder.getMethod("updateSimulation", Int::class.javaPrimitiveType),
                holder.getMethod("setGravityVectorRequest", Boolean::class.javaPrimitiveType),
                holder.getMethod("simulate", player),
                Class.forName(WAVEY_DELEGATE).constructors.first { it.parameterCount == 1 },
            )
        }.onFailure { warnOnce("wavey-capes", it) }.getOrNull()
    }

    val waveyInstalled: Boolean get() = wavey != null

    fun <T> withWaveyCapes(simulated: Boolean, block: () -> T): T {
        val mod = wavey ?: return block()
        val config = runCatching { mod.config.get(null) }.getOrNull() ?: return block()
        val saved = runCatching { mod.movement.get(config) }.getOrNull() ?: return block()
        val savedWind = runCatching { mod.wind.get(config) }.getOrNull() ?: return block()
        val wanted = if (!simulated) mod.vanilla else if (saved == mod.vanilla) mod.simulated else saved
        mod.movement.set(config, wanted)
        if (simulated) mod.wind.set(config, mod.waves)
        try {
            return if (simulated) withModEnabled(WAVEY_ID, block) else block()
        } finally {
            mod.movement.set(config, saved)
            mod.wind.set(config, savedWind)
        }
    }

    private val toggleLookup: Pair<Method, Any?>? by lazy {
        runCatching {
            val toggles = Class.forName(MOD_TOGGLES)
            val method = runCatching { toggles.getMethod("handle", String::class.java) }.getOrNull()
                ?: toggles.getMethod("toggleFor", String::class.java)
            val owner = if (Modifier.isStatic(method.modifiers)) null else toggles.getField("INSTANCE").get(null)
            method to owner
        }.getOrNull()
    }

    private fun enabledField(toggle: Any): Field? {
        var type: Class<*>? = toggle.javaClass
        while (type != null) {
            val field = runCatching { type.getDeclaredField("enabled") }.getOrNull()
            if (field != null && field.type == Boolean::class.javaPrimitiveType) {
                field.isAccessible = true
                return field
            }
            type = type.superclass
        }
        return null
    }

    private fun <T> withModEnabled(modId: String, block: () -> T): T {
        val (method, owner) = toggleLookup ?: return block()
        val toggle = runCatching { method.invoke(owner, modId) }.getOrNull() ?: return block()
        val field = enabledField(toggle) ?: return block()
        val saved = field.getBoolean(toggle)
        if (saved) return block()
        field.setBoolean(toggle, true)
        try {
            return block()
        } finally {
            field.setBoolean(toggle, saved)
        }
    }

    fun stepCape(entity: Entity) {
        val mod = wavey ?: return
        if (!mod.holder.isInstance(entity)) return
        runCatching {
            withWaveyCapes(true) {
                mod.update.invoke(entity, CAPE_PARTS)
                mod.gravityRequest.invoke(entity, true)
                mod.simulate.invoke(entity, mod.delegate.newInstance(entity))
            }
        }.onFailure { warnOnce("wavey-capes-step", it) }
    }

    private class SkinLayers(
        val texture: Method,
        val helper: Any,
        val create: Method,
        val inject: Method,
        val injectedMesh: Method,
        val injectedOffset: Method,
        val offsets: Map<String, Any>,
    )

    private val skinLayers: SkinLayers? by lazy {
        runCatching {
            val util = classOrNull(SKIN_LAYERS_UTIL) ?: return@runCatching null
            val api = Class.forName(SKIN_LAYERS_API)
            val helper = api.getMethod("getMeshHelper").invoke(null)
            val helperType = Class.forName(SKIN_LAYERS_MESH_HELPER)
            val mesh = Class.forName(SKIN_LAYERS_MESH)
            val offset = Class.forName(SKIN_LAYERS_OFFSET)
            val injector = Class.forName(SKIN_LAYERS_INJECTOR)
            SkinLayers(
                util.getMethod("getTexture", Identifier::class.java, Class.forName(SKIN_LAYERS_SKULL_SETTINGS)),
                helper,
                helperType.getMethod(
                    "create3DMesh",
                    NativeImage::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                ),
                injector.getMethod("setInjectedMesh", mesh, offset),
                injector.getMethod("getInjectedMesh"),
                injector.getMethod("getOffsetProvider"),
                OFFSET_NAMES.associateWith { offset.getField(it).get(null) },
            )
        }.onFailure { warnOnce("skin-layers", it) }.getOrNull()
    }

    val skinLayersInstalled: Boolean get() = skinLayers != null

    private class Meshes(val skin: Identifier, val slim: Boolean, val parts: List<Pair<Any, String>>)

    private var meshes: Meshes? = null
    private var nextMeshAttempt = 0L

    private fun meshesFor(mod: SkinLayers, skin: PlayerSkin): Meshes? {
        val id = LivePlayerSkins.texture(skin)
        val slim = LivePlayerSkins.slim(skin)
        meshes?.let { if (it.skin == id && it.slim == slim) return it }
        val now = System.nanoTime()
        if (now < nextMeshAttempt) return null
        nextMeshAttempt = now + MESH_RETRY_NANOS
        val image = mod.texture.invoke(null, id, null) as? NativeImage ?: return null
        if (image.width != SKIN_SIZE || image.height != SKIN_SIZE) return null
        fun mesh(width: Int, height: Int, depth: Int, u: Int, v: Int, topPivot: Boolean, rotation: Float): Any =
            mod.create.invoke(mod.helper, image, width, height, depth, u, v, topPivot, rotation)
        val arm = if (slim) SLIM_ARM else WIDE_ARM
        val parts = listOf(
            mesh(HEAD_SIZE, HEAD_SIZE, HEAD_SIZE, HEAD_U, 0, false, HEAD_ROTATION) to "HEAD",
            mesh(HEAD_SIZE, LIMB_LENGTH, LIMB_DEPTH, TORSO_U, LAYER_V, true, 0f) to "BODY",
            mesh(arm, LIMB_LENGTH, LIMB_DEPTH, LEFT_LIMB_U, LEFT_LIMB_V, true, ARM_ROTATION) to if (slim) "LEFT_ARM_SLIM" else "LEFT_ARM",
            mesh(arm, LIMB_LENGTH, LIMB_DEPTH, RIGHT_ARM_U, LAYER_V, true, ARM_ROTATION) to if (slim) "RIGHT_ARM_SLIM" else "RIGHT_ARM",
            mesh(LIMB_DEPTH, LIMB_LENGTH, LIMB_DEPTH, 0, LEFT_LIMB_V, true, 0f) to "LEFT_LEG",
            mesh(LIMB_DEPTH, LIMB_LENGTH, LIMB_DEPTH, 0, LAYER_V, true, 0f) to "RIGHT_LEG",
        )
        return Meshes(id, slim, parts).also { meshes = it }
    }

    private fun layerParts(model: LivePlayerModel): List<ModelPart> =
        listOf(model.hat, model.jacket, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants)

    private val outerCoverage = HashMap<Identifier, Boolean>()

    fun hasOuterLayer(skin: PlayerSkin): Boolean? {
        val mod = skinLayers ?: return null
        val id = LivePlayerSkins.texture(skin)
        outerCoverage[id]?.let { return it }
        val image = runCatching { mod.texture.invoke(null, id, null) as? NativeImage }.getOrNull() ?: return null
        if (image.width != SKIN_SIZE || image.height != SKIN_SIZE) return null
        val hat = opaquePixels(image, HAT_REGION)
        val body = BODY_LAYER_REGIONS.sumOf { opaquePixels(image, it) }
        val layered = hat >= HAT_FACE_PIXELS * MIN_COVERAGE || body >= BODY_FACE_PIXELS * MIN_COVERAGE
        outerCoverage[id] = layered
        return layered
    }

    private fun opaquePixels(image: NativeImage, region: IntArray): Int {
        var count = 0
        for (y in region[1] until region[3]) for (x in region[0] until region[2]) {
            if ((LivePlayerSkins.readPixel(image, x, y) ushr ALPHA_SHIFT) != 0) count++
        }
        return count
    }

    fun <T> withSkinLayers(model: LivePlayerModel?, skin: PlayerSkin?, block: () -> T): T {
        val mod = skinLayers ?: return block()
        if (model == null) return block()
        val parts = layerParts(model)
        val saved = runCatching { parts.map { mod.injectedMesh.invoke(it) to mod.injectedOffset.invoke(it) } }
            .onFailure { warnOnce("skin-layers-read", it) }
            .getOrNull() ?: return block()
        val wanted = skin?.let { runCatching { meshesFor(mod, it) }.onFailure { e -> warnOnce("skin-layers-mesh", e) }.getOrNull() }
        try {
            parts.forEachIndexed { i, part ->
                val entry = wanted?.parts?.get(i)
                mod.inject.invoke(part, entry?.first, entry?.second?.let(mod.offsets::get))
            }
            return block()
        } finally {
            parts.forEachIndexed { i, part -> mod.inject.invoke(part, saved[i].first, saved[i].second) }
        }
    }

    private const val CAPE_PARTS = 16
    private const val ALPHA_SHIFT = 24
    private const val SKIN_SIZE = 64
    private const val HEAD_SIZE = 8
    private const val HEAD_U = 32
    private const val HEAD_ROTATION = 0.6f
    private const val TORSO_U = 16
    private const val LAYER_V = 32
    private const val LIMB_LENGTH = 12
    private const val LIMB_DEPTH = 4
    private const val WIDE_ARM = 4
    private const val SLIM_ARM = 3
    private const val ARM_ROTATION = -2f
    private const val LEFT_LIMB_U = 48
    private const val LEFT_LIMB_V = 48
    private const val RIGHT_ARM_U = 40
    private const val MESH_RETRY_NANOS = 500_000_000L

    private val HAT_REGION = intArrayOf(32, 0, 64, 16)
    private val BODY_LAYER_REGIONS = listOf(
        intArrayOf(16, 32, 40, 48),
        intArrayOf(40, 32, 56, 48),
        intArrayOf(48, 48, 64, 64),
    )
    private const val HAT_FACE_PIXELS = 384
    private const val BODY_FACE_PIXELS = 752
    private const val MIN_COVERAGE = 0.15f

    private val OFFSET_NAMES = listOf("HEAD", "BODY", "LEFT_ARM", "LEFT_ARM_SLIM", "RIGHT_ARM", "RIGHT_ARM_SLIM", "LEFT_LEG", "RIGHT_LEG")

    private const val MOUNT_OPACITY_HOOK = "dev.microcontrollers.mountopacity.hook.EntityRenderStateHook"
    private const val TRANSITION_EXTENDER = "dev.tr7zw.transition.mc.entitywrapper.EntityRenderStateExtender"
    private const val WAVEY_MOD_BASE = "dev.tr7zw.waveycapes.versionless.ModBase"
    private const val WAVEY_CONFIG = "dev.tr7zw.waveycapes.versionless.config.Config"
    private const val MOD_TOGGLES = "org.polyfrost.oneconfig.api.ui.v1.ModToggles"
    private const val WAVEY_ID = "waveycapes"
    private const val WAVEY_WIND = "dev.tr7zw.waveycapes.versionless.WindMode"
    private const val WAVEY_MOVEMENT = "dev.tr7zw.waveycapes.versionless.CapeMovement"
    private const val WAVEY_HOLDER = "dev.tr7zw.waveycapes.versionless.CapeHolder"
    private const val WAVEY_PLAYER = "dev.tr7zw.waveycapes.versionless.nms.MinecraftPlayer"
    private const val WAVEY_DELEGATE = "dev.tr7zw.waveycapes.delegate.PlayerDelegate"
    private const val SKIN_LAYERS_UTIL = "dev.tr7zw.skinlayers.SkinUtil"
    private const val SKIN_LAYERS_API = "dev.tr7zw.skinlayers.api.SkinLayersAPI"
    private const val SKIN_LAYERS_MESH_HELPER = "dev.tr7zw.skinlayers.api.MeshHelper"
    private const val SKIN_LAYERS_MESH = "dev.tr7zw.skinlayers.api.Mesh"
    private const val SKIN_LAYERS_OFFSET = "dev.tr7zw.skinlayers.api.OffsetProvider"
    private const val SKIN_LAYERS_INJECTOR = "dev.tr7zw.skinlayers.accessor.ModelPartInjector"
    private const val SKIN_LAYERS_SKULL_SETTINGS = "dev.tr7zw.skinlayers.accessor.SkullSettings"
}
//?}
