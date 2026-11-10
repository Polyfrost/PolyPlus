package org.polyfrost.polyplus.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.serialize.ObjectSerializer
import org.polyfrost.oneconfig.api.config.v1.serialize.adapter.Adapter
import org.polyfrost.polyplus.PolyPlusConstants
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

class OneConfigBinding private constructor(
    override val key: String,
    override val mod: String,
    private val treeId: String,
    private val path: String,
    private val property: Property<Any?>,
    private val kind: Kind,
    private val scale: Double?,
    private val invert: Boolean,
    private val enumValues: Map<String, Any>?,
) : Binding {
    enum class Kind { BOOL, NUMBER, STRING, ENUM, COLOR }

    override val target: String = target(treeId, path)
    override val type: String = if (enumValues != null) "enum" else kind.name.lowercase()
    override val bounds: List<Double>? = if (kind == Kind.NUMBER) bounds(property) else null
    private val lock = Supplier { Property.Display.DISABLED }

    override fun snapshot(): JsonElement? {
        val value = property.get() ?: return null
        return when (kind) {
            Kind.BOOL -> JsonPrimitive(value as Boolean)
            Kind.NUMBER -> JsonPrimitive(value as Number)
            Kind.STRING -> JsonPrimitive(value as String)
            Kind.ENUM -> JsonPrimitive((value as Enum<*>).name)
            Kind.COLOR -> toJson(adapter(property.type)?.serialize(value) ?: return null)
        }
    }

    override fun apply(value: OptionValue): Boolean {
        val converted = if (enumValues != null) enumValues[value.asLookupKey()] else convert(value)
        return converted != null && !property.set(converted)
    }

    override fun restore(snapshot: JsonElement) {
        val value = fromSnapshot(snapshot) ?: error("Journal value $snapshot does not fit $target")
        property.set(value)
        ConfigManager.active().trees().firstOrNull { it.id == treeId }?.let { ConfigManager.active().save(it) }
    }

    override fun setLocked(locked: Boolean) {
        property.removeDisplayConditionIf { it === lock }
        if (locked) property.addDisplayCondition(lock)
    }

    private fun convert(value: OptionValue): Any? = when (kind) {
        Kind.BOOL -> (value as? OptionValue.Bool)?.value?.let { it != invert }
        Kind.NUMBER -> value.asDouble()?.let { number(it * (scale ?: 1.0)) }
        Kind.STRING -> (value as? OptionValue.Str)?.value
        Kind.ENUM -> (value as? OptionValue.Str)?.value?.let { enumConstant(property.type, it) }
        Kind.COLOR -> value.asArgb()?.let { adapter(property.type)?.deserialize(it.toLong()) }
    }

    private fun number(raw: Double): Any {
        val bounded = bounds?.let { Math.clamp(raw, it[0], it[1]) } ?: raw
        return castNumber(property.type, bounded)
    }

    private fun fromSnapshot(snapshot: JsonElement): Any? {
        val primitive = snapshot as? JsonPrimitive
        return when (kind) {
            Kind.BOOL -> primitive?.booleanOrNull
            Kind.NUMBER -> primitive?.doubleOrNull?.let { castNumber(property.type, it) }
            Kind.STRING -> primitive?.takeIf { it.isString }?.content
            Kind.ENUM -> primitive?.takeIf { it.isString }?.content?.let { enumConstant(property.type, it) }
            Kind.COLOR -> adapter(property.type)?.deserialize(fromJson(snapshot) ?: return null)
        }
    }

    companion object {
        fun target(treeId: String, path: String) = "oneconfig:$treeId:$path"

        internal fun create(
            key: String,
            mod: String,
            tree: Tree,
            path: String,
            scale: Double? = null,
            invert: Boolean = false,
            enumMap: JsonObject? = null,
        ): Result<OneConfigBinding> = runCatching {
            val property = tree.getProp(*path.split('.').toTypedArray()) ?: error("no property at '$path'")
            val kind = kindOf(property.type) ?: error("'$path' has unsupported type ${property.type.name}")
            require(scale == null || kind == Kind.NUMBER) { "scale needs a number, '$path' is ${kind.name.lowercase()}" }
            require(!invert || kind == Kind.BOOL) { "invert needs a boolean, '$path' is ${kind.name.lowercase()}" }
            val enumValues = enumMap?.mapValues { (from, to) ->
                enumTarget(property.type, kind, to) ?: error("enum value $from -> $to does not fit '$path' (${property.type.simpleName})")
            }
            OneConfigBinding(key, mod, tree.id, path, Property.recast(property), kind, scale, invert, enumValues)
        }

        private fun kindOf(type: Class<*>): Kind? = when {
            type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType -> Kind.BOOL
            type in NUMBER_TYPES -> Kind.NUMBER
            type == String::class.java -> Kind.STRING
            type.isEnum || type.superclass?.isEnum == true -> Kind.ENUM
            PolyColor::class.java.isAssignableFrom(type) && adapter(type) != null -> Kind.COLOR
            else -> null
        }

        private val NUMBER_TYPES = listOf(Int::class, Float::class, Double::class, Long::class, Short::class, Byte::class)
            .flatMap { listOfNotNull(it.javaPrimitiveType, it.javaObjectType) }

        private fun enumTarget(type: Class<*>, kind: Kind, to: JsonElement): Any? {
            val primitive = to as? JsonPrimitive ?: return null
            return when (kind) {
                Kind.ENUM -> if (primitive.isString) enumConstant(type, primitive.content) else primitive.intOrNull?.let { enumConstants(type)?.getOrNull(it) }
                Kind.NUMBER -> primitive.takeIf { !it.isString }?.doubleOrNull?.let { castNumber(type, it) }
                Kind.STRING -> primitive.takeIf { it.isString }?.content
                Kind.BOOL -> primitive.takeIf { !it.isString }?.booleanOrNull
                Kind.COLOR -> null
            }
        }

        private fun enumConstant(type: Class<*>, name: String): Any? {
            val constants = enumConstants(type) ?: return null
            val normalized = name.replace('-', '_')
            return constants.firstOrNull { (it as Enum<*>).name == name }
                ?: constants.firstOrNull { (it as Enum<*>).name.equals(normalized, ignoreCase = true) }
        }

        private fun enumConstants(type: Class<*>): Array<out Any>? = (if (type.isEnum) type else type.superclass).enumConstants

        private fun castNumber(type: Class<*>, value: Double): Any = when (type.kotlin.javaObjectType) {
            Int::class.javaObjectType -> Math.clamp(Math.round(value), Int.MIN_VALUE, Int.MAX_VALUE)
            Long::class.javaObjectType -> Math.round(value)
            Short::class.javaObjectType -> Math.clamp(Math.round(value), Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            Byte::class.javaObjectType -> Math.clamp(Math.round(value), Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toByte()
            Float::class.javaObjectType -> value.toFloat()
            else -> value
        }

        private fun bounds(property: Property<*>): List<Double>? {
            val min = (property.getMetadata<Any>("min") as? Number)?.toDouble() ?: return null
            val max = (property.getMetadata<Any>("max") as? Number)?.toDouble() ?: return null
            return if (max > min) listOf(min, max) else null
        }

        @Suppress("UNCHECKED_CAST")
        private fun adapter(type: Class<*>): Adapter<Any, Any>? = ObjectSerializer.INSTANCE.getAdapter(type) as Adapter<Any, Any>?

        private fun toJson(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is IntArray -> JsonArray(value.map(::JsonPrimitive))
            is Array<*> -> JsonArray(value.map(::toJson))
            is Iterable<*> -> JsonArray(value.map(::toJson))
            is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to toJson(it.value) })
            else -> error("can't journal ${value::class.java.name}")
        }

        private fun fromJson(element: JsonElement): Any? = when (element) {
            is JsonNull -> null
            is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNull ?: element.doubleOrNull
            is JsonArray -> element.map(::fromJson)
            is JsonObject -> element.mapValues { fromJson(it.value) }
        }
    }
}

object OneConfigBindings {
    private val LOGGER = LogManager.getLogger("PolyPlus/ServerBindings")
    private const val DIRECTORY = "assets/${PolyPlusConstants.ID}/server/bindings"

    private class BindingFile(val mod: String, val config: String, val options: Map<String, JsonObject>)

    private val awaiting = ConcurrentHashMap<String, MutableList<BindingFile>>()
    private val replayFailures: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun add(mod: String, config: String, options: String) {
        if (!FabricLoader.getInstance().isModLoaded(mod)) return
        val parsed = (Json.parseToJsonElement(options) as JsonObject).mapValues { it.value as JsonObject }
        awaiting.computeIfAbsent(config) { mutableListOf() } += BindingFile(mod, config, parsed)
    }

    fun initialize() {
        for (file in readFiles()) awaiting.computeIfAbsent(file.config) { mutableListOf() } += file
        ConfigManager.addTreeRegistrationListener(::onTreeRegistered)
        ConfigManager.active().trees().toList().forEach(::onTreeRegistered)
    }

    fun reportMissingTrees() {
        for ((config, files) in awaiting) for (file in files) for (key in file.options.keys) {
            OptionRegistry.unresolved(key, file.mod, "config '$config' was never registered")
        }
    }

    private fun onTreeRegistered(tree: Tree) {
        val id = tree.id ?: return
        replayJournal(id, tree)
        val files = awaiting.remove(id) ?: return
        for (file in files) resolve(file, tree)
    }

    private fun replayJournal(id: String, tree: Tree) {
        val prefix = OneConfigBinding.target(id, "")
        for (target in ServerIntegration.overrides.pendingTargets()) {
            if (!target.startsWith(prefix)) continue
            OneConfigBinding.create("", "", tree, target.removePrefix(prefix))
                .onSuccess { ServerIntegration.overrides.replay(target, it::restore) }
                .onFailure { if (replayFailures.add(target)) LOGGER.warn("Can't restore {} from the journal yet: {}", target, it.message) }
        }
    }

    private fun resolve(file: BindingFile, tree: Tree) {
        for ((key, spec) in file.options) {
            val result = runCatching {
                val paths = spec["paths"]?.let { (it as? JsonArray)?.map { p -> (p as JsonPrimitive).content } ?: error("paths is not an array") }
                    ?: listOf((spec["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("missing path"))
                val scale = spec["scale"]?.let { (it as? JsonPrimitive)?.doubleOrNull ?: error("scale is not a number") }
                val invert = spec["invert"]?.let { (it as? JsonPrimitive)?.booleanOrNull ?: error("invert is not a boolean") } ?: false
                val enumMap = spec["enum"]?.let { it as? JsonObject ?: error("enum is not an object") }
                paths.map { OneConfigBinding.create(key, file.mod, tree, it, scale, invert, enumMap).getOrThrow() }
            }
            result
                .onSuccess { it.forEach(OptionRegistry::register) }
                .onFailure { OptionRegistry.unresolved(key, file.mod, it.message ?: it.toString()) }
        }
    }

    private fun readFiles(): List<BindingFile> {
        val root = FabricLoader.getInstance().getModContainer(PolyPlusConstants.ID).flatMap { it.findPath(DIRECTORY) }.orElse(null)
        if (root == null) {
            LOGGER.warn("No server binding files found at {}", DIRECTORY)
            return emptyList()
        }
        val paths = Files.list(root).use { stream -> stream.filter { it.fileName.toString().endsWith(".json") }.sorted().toList() }
        return paths.mapNotNull { path ->
            runCatching {
                val json = Json.parseToJsonElement(Files.readString(path)) as JsonObject
                val mod = (json["mod"] as JsonPrimitive).content
                if (!FabricLoader.getInstance().isModLoaded(mod)) return@mapNotNull null
                val options = (json["options"] as JsonObject).mapValues { it.value as JsonObject }
                BindingFile(mod, (json["config"] as JsonPrimitive).content, options)
            }.onFailure { LOGGER.error("Skipping malformed server binding file {}", path.fileName, it) }.getOrNull()
        }
    }
}
