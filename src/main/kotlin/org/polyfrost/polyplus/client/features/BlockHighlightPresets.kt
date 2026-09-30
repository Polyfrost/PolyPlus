package org.polyfrost.polyplus.client.features

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.math.roundToInt

object BlockHighlightPresets {
    private const val CONFIG = "tektonikal.customblockhighlight.config.BlockHighlightConfig"
    private const val CONFIG_MANAGER = "tektonikal.customblockhighlight.config.ConfigManager"

    private const val FIRST_OPEN_MARKER = ".cbh_info"

    val NAMES = listOf("vanilla", "classic", "fancy", "sweat", "trans")
    val LABELS = listOf("Vanilla", "Classic", "Fancy", "Sweat", "Trans")
    const val DEFAULT = "vanilla"

    val available: Boolean by lazy {
        runCatching { Class.forName(CONFIG_MANAGER, false, javaClass.classLoader) }.isSuccess
    }

    private fun manager(): Class<*> = Class.forName(CONFIG_MANAGER, true, javaClass.classLoader)
    private fun config(): Class<*> = Class.forName(CONFIG, true, javaClass.classLoader)
    private fun gson(): Gson = manager().getField("GSON").get(null) as Gson

    fun markerPath(): Path = FabricLoader.getInstance().configDir.resolve(FIRST_OPEN_MARKER)

    fun presetJson(name: String): String {
        val text = manager().getResourceAsStream("/assets/presets/$name.json")
            ?.use { it.readBytes().decodeToString() }
            ?: error("Custom Block Highlight has no '$name' preset")
        val gson = gson()
        return gson.toJson(gson.fromJson(text, config()))
    }

    fun currentJson(): String = gson().toJson(config().getField("ACTIVE_INSTANCE").get(null))

    fun presetJsons(): Map<String, String> = NAMES.mapNotNull { name ->
        runCatching { name to presetJson(name) }.getOrNull()
    }.toMap()

    fun apply(name: String) {
        val preset = manager().getMethod("loadPreset", String::class.java).invoke(null, name)
            ?: error("Custom Block Highlight has no '$name' preset")
        activate(preset)
    }

    fun applyJson(json: String) {
        val manager = manager()
        val config = manager.getMethod("loadFromJsonString", String::class.java).invoke(null, json)
            ?: error("Custom Block Highlight rejected the onboarding config")
        activate(config)
    }

    private fun activate(preset: Any) {
        val manager = manager()
        val configClass = config()
        configClass.getField("ACTIVE_INSTANCE").set(null, preset)
        configClass.getMethod("applyValuesToOptionInstances").invoke(preset)
        manager.getMethod("save").invoke(null)
        markerPath().let { if (!it.exists()) it.createFile() }
    }
}

class BlockHighlightStyle(
    val layers: List<Layer>,
    val fill: Fill?,
) {
    enum class Depth { NORMAL, ALWAYS_PASS, HIDDEN_ONLY }
    enum class Faces { AIR_EXPOSED, ALL, CONCEALED, LOOKAT }

    class Rainbow(val speed: Float, val delay: Int, val saturation: Float, val brightness: Float) {
        fun color(millis: Long, primary: Boolean): Int {
            val hue = (Math.ceil((millis + if (primary) 0 else delay).toDouble()) * speed / 50.0) % 360.0
            return java.awt.Color.HSBtoRGB((hue / 360.0).toFloat(), saturation, brightness)
        }
    }

    class Colors(val first: Int, val second: Int, val alpha: Int, val rainbow: Rainbow?) {
        fun at(millis: Long): Pair<Int, Int> =
            if (rainbow == null) first to second
            else rainbow.color(millis, true) to rainbow.color(millis, false)
    }

    class Layer(
        val colors: Colors,
        val width: Float,
        val depth: Depth,
        val faces: Faces,
        val scale: Float,
        val cutFromCenter: Float,
        val cutFromCorner: Float,
        val innerMult: Float,
        val outerMult: Float,
    )

    class Fill(val colors: Colors, val depth: Depth, val faces: Faces, val scale: Float)

    companion object {
        private val VANILLA_LAYER = Layer(
            Colors(0, 0, 102, null), 2.5f, Depth.NORMAL, Faces.ALL, 1f, 0f, 0f, 1f, 1f,
        )

        fun parse(json: String): BlockHighlightStyle {
            val root = JsonParser.parseString(json).asJsonObject
            val modRendering = root.bool("enableModRendering", true)
            val layers = buildList {
                if (root.bool("drawVanillaOutline", false)) add(VANILLA_LAYER)
                if (!modRendering) return@buildList
                val primary = root.getAsJsonObject("primary")
                if (primary?.bool("enabled", true) != true) return@buildList
                listOf("tertiary", "secondary", "primary").forEach { key ->
                    val line = root.getAsJsonObject(key) ?: return@forEach
                    if (line.bool("enabled", false)) add(line.layer())
                }
            }
            val fill = if (modRendering && root.bool("fillEnabled", false)) {
                Fill(
                    root.getAsJsonObject("fillCol").colors(),
                    root.enum("fillDepthTest", Depth.HIDDEN_ONLY),
                    root.enum("fillType", Faces.ALL),
                    scale(root.float("fillExpandBlocks", 0f), root.float("fillExpandPercent", 1f)),
                )
            } else {
                null
            }
            return BlockHighlightStyle(layers, fill)
        }

        private fun scale(blocks: Float, percent: Float) = (1f + 2f * blocks) * percent

        private fun JsonObject.layer() = Layer(
            getAsJsonObject("color").colors(),
            float("lineWidth", 2.5f),
            enum("lineDepthTest", Depth.ALWAYS_PASS),
            enum("outlineType", Faces.ALL),
            scale(float("lineExpandBlocks", 0f), float("lineExpandPercentage", 1f)),
            float("cutFromCenter", 0f),
            float("cutFromCorner", 0f),
            float("innerThicknessMult", 1f),
            float("outerThicknessMult", 1f),
        )

        private fun JsonObject.colors(): Colors {
            val rainbow = getAsJsonObject("rainbowSettings")?.takeIf { it.bool("enabled", false) }?.let {
                Rainbow(it.float("speed", 5f), it.float("delay", 250f).toInt(), it.float("saturation", 1f), it.float("brightness", 1f))
            }
            return Colors(
                int("col1", 0) and 0xFFFFFF,
                int("col2", 0) and 0xFFFFFF,
                int("alpha", 255).coerceIn(0, 255),
                rainbow,
            )
        }

        private fun JsonObject.bool(key: String, default: Boolean) =
            get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: default

        private fun JsonObject.float(key: String, default: Float) =
            get(key)?.takeIf { it.isJsonPrimitive }?.asFloat ?: default

        private fun JsonObject.int(key: String, default: Int) =
            get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: default

        private inline fun <reified E : Enum<E>> JsonObject.enum(key: String, default: E): E =
            get(key)?.takeIf { it.isJsonPrimitive }?.asString
                ?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
    }
}

class BlockHighlightDraft(json: String) {
    private val root: JsonObject = JsonParser.parseString(json).asJsonObject
    private val primary get() = root.obj("primary")
    private val lineColor get() = primary.obj("color")
    private val fillColor get() = root.obj("fillCol")

    fun toJson(): String = root.toString()

    fun matches(json: String): Boolean = root == JsonParser.parseString(json)

    var outlineStart: Int
        get() = lineColor.rgb("col1")
        set(value) = lineColor.setRgb("col1", value)
    var outlineEnd: Int
        get() = lineColor.rgb("col2")
        set(value) = lineColor.setRgb("col2", value)
    var outlineOpacity: Float
        get() = lineColor.int("alpha", 255) / 255f
        set(value) = lineColor.addProperty("alpha", (value.coerceIn(0f, 1f) * 255).roundToInt())
    var outlineWidth: Float
        get() = primary.float("lineWidth", 2.5f)
        set(value) = primary.addProperty("lineWidth", value)
    var outlineRainbow: Boolean
        get() = lineColor.obj("rainbowSettings").bool("enabled", false)
        set(value) = lineColor.obj("rainbowSettings").addProperty("enabled", value)

    var seeThrough: Boolean
        get() = primary.string("lineDepthTest") == DEPTH_ALWAYS
        set(value) {
            LAYERS.forEach { root.obj(it).addProperty("lineDepthTest", if (value) DEPTH_ALWAYS else DEPTH_NORMAL) }
            if (fill) root.addProperty("fillDepthTest", if (value) DEPTH_ALWAYS else DEPTH_NORMAL)
        }

    var fill: Boolean
        get() = root.bool("fillEnabled", false)
        set(value) {
            root.addProperty("fillEnabled", value)
            if (value) root.addProperty("fillDepthTest", if (seeThrough) DEPTH_ALWAYS else DEPTH_NORMAL)
        }
    var fillStart: Int
        get() = fillColor.rgb("col1")
        set(value) = fillColor.setRgb("col1", value)
    var fillEnd: Int
        get() = fillColor.rgb("col2")
        set(value) = fillColor.setRgb("col2", value)
    var fillOpacity: Float
        get() = fillColor.int("alpha", 255) / 255f
        set(value) = fillColor.addProperty("alpha", (value.coerceIn(0f, 1f) * 255).roundToInt())
    var fillRainbow: Boolean
        get() = fillColor.obj("rainbowSettings").bool("enabled", false)
        set(value) = fillColor.obj("rainbowSettings").addProperty("enabled", value)

    var animations: Boolean
        get() = ANIMATIONS.any { root.bool(it, false) }
        set(value) = ANIMATIONS.forEach { root.addProperty(it, value) }

    private companion object {
        const val DEPTH_NORMAL = "NORMAL"
        const val DEPTH_ALWAYS = "ALWAYS_PASS"
        val LAYERS = listOf("primary", "secondary", "tertiary")
        val ANIMATIONS = listOf("doEasing", "fadeIn", "fadeOut", "scale", "animateLineThickness")

        fun JsonObject.obj(key: String): JsonObject =
            getAsJsonObject(key) ?: JsonObject().also { add(key, it) }

        fun JsonObject.bool(key: String, default: Boolean) =
            get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: default

        fun JsonObject.float(key: String, default: Float) =
            get(key)?.takeIf { it.isJsonPrimitive }?.asFloat ?: default

        fun JsonObject.int(key: String, default: Int) =
            get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: default

        fun JsonObject.string(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString

        fun JsonObject.rgb(key: String) = int(key, 0) and 0xFFFFFF

        fun JsonObject.setRgb(key: String, rgb: Int) = addProperty(key, (0xFF shl 24) or (rgb and 0xFFFFFF))
    }
}
