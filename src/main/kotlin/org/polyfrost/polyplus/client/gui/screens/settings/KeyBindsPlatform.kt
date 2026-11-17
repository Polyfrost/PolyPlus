@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
//? if > 1.8.9 {
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeyModifiers
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
import org.polyfrost.oneconfig.api.ui.v1.keybind.internal.MinecraftKeybindBridgeImpl
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.Collections
import java.util.WeakHashMap
import java.util.function.BooleanSupplier
//?} elif = 1.8.9 {
/*import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.options.GameOptions
import net.minecraft.client.options.KeyBinding
import net.minecraft.client.resource.language.I18n
import org.lwjgl.input.Keyboard
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeyModifiers
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
import org.polyfrost.oneconfig.api.ui.v1.keybind.internal.MinecraftKeybindBridgeImpl
import org.polyfrost.oneconfig.internal.legacy.InputConstants
import org.polyfrost.oneconfig.internal.legacy.KeyCodes
import org.polyfrost.oneconfig.internal.legacy.chat.Component
import java.util.function.BooleanSupplier
*///?}

internal class KeyBindRow(
    val id: String,
    val name: String,
    val key: String,
    val keyId: String,
    val category: String,
    val owner: String,
    val order: Int,
    val isDefault: Boolean,
    val unbound: Boolean,
    val conflicts: List<String>,
    val conflictText: String?,
    val chord: Boolean = false,
    val chordIds: List<String> = emptyList(),
) {
    fun uses(id: String): Boolean = keyId == id || id in chordIds

    val allIds: List<String> get() = if (unbound) emptyList() else (listOf(keyId) + chordIds).distinct()
}

internal object KeyBindCapture {
    var target by mutableStateOf<String?>(null)
        internal set

    var preview by mutableStateOf<String?>(null)
        internal set

    var revision by mutableIntStateOf(0)
        internal set

    @Volatile
    internal var overChip = false
}

//? if > 1.8.9 {
internal object KeyBindsPlatform {
    private val mc: Minecraft get() = Minecraft.getInstance()
    private val hooked: MutableSet<Event<*>> = Collections.newSetFromMap(WeakHashMap())
    private var installed = false
    private var swallowKey: Int? = null
    private var swallowButton: Int? = null
    private var cancelOnRelease = false
    private val capture = BooleanSupplier { KeyBindCapture.target != null }
    private val owners = HashMap<String, String>()
    private var byId: Map<String, KeyMapping> = emptyMap()
    private val chordKeys = LinkedHashSet<Int>()
    private val chordMouse = LinkedHashSet<Int>()
    private val heldKeys = HashSet<Int>()
    private val heldMouse = HashSet<Int>()

    const val keymap: Boolean = true

    fun rows(): List<KeyBindRow> {
        val all = mc.options.keyMappings
        val ids = HashMap<String, KeyMapping>()
        val rows = all.sorted().mapIndexed { index, key ->
            val id = if (key.name in ids) key.name + "#" + index else key.name
            ids[id] = key
            row(key, id, all, index)
        }
        byId = ids
        return rows
    }

    fun keyLabel(keyId: String): String? = runCatching { InputConstants.getKey(keyId).displayName.string }.getOrNull()

    fun text(key: String): String = Component.translatable(key).string

    fun anyChanged(): Boolean = mc.options.keyMappings.any { !it.isDefault }

    fun reset(id: String) {
        val key = find(id) ?: return
        key.setKey(key.defaultKey)
        commit()
    }

    fun clear(id: String) {
        val key = find(id) ?: return
        if (key.isUnbound) return
        key.setKey(InputConstants.UNKNOWN)
        commit()
    }

    fun resetAll() {
        mc.options.keyMappings.forEach { it.setKey(it.defaultKey) }
        commit()
    }

    fun start(id: String) {
        install()
        ClientPlatform.currentScreen()?.let(::hook)
        resetChord()
        if (KeyBindCapture.target == null) KeybindManager.addInputCapture(capture)
        KeyBindCapture.target = id
        KeyBindCapture.revision++
    }

    fun stop() {
        resetChord()
        if (KeyBindCapture.target == null) return
        KeyBindCapture.target = null
        KeybindManager.removeInputCapture(capture)
        KeyBindCapture.revision++
    }

    //? if < 26.1 {
    /*@JvmStatic
    fun blockChar(): Boolean = KeyBindCapture.target != null || swallowKey != null
    *///?}

    private fun bridge(): MinecraftKeybindBridgeImpl? = runCatching { MinecraftKeybindBridgeImpl.instance() }.getOrNull()

    private fun mirrored(key: KeyMapping): Boolean = runCatching { bridge()?.bindFor(key) != null }.getOrDefault(false)

    private fun chordTarget(): KeyMapping? = KeyBindCapture.target?.let(::find)?.takeIf(::mirrored)

    private fun resetChord() {
        KeyBindCapture.overChip = false
        chordKeys.clear()
        chordMouse.clear()
        heldKeys.clear()
        heldMouse.clear()
        if (KeyBindCapture.preview == null) return
        runCatching { bridge()?.clearPreview() }
        KeyBindCapture.preview = null
    }

    private fun preview(key: KeyMapping) {
        KeyBindCapture.preview = runCatching {
            bridge()?.let {
                it.setPreview(key, chordKeys.toIntArray().takeIf { keys -> keys.isNotEmpty() }, chordMouse.toIntArray().takeIf { mouse -> mouse.isNotEmpty() })
                it.previewFor(key)?.string
            }
        }.getOrNull()
    }

    private fun finishChord() {
        if (heldKeys.isNotEmpty() || heldMouse.isNotEmpty()) return
        val key = chordTarget()
        val keys = chordKeys.toIntArray().takeIf { it.isNotEmpty() }
        val mouse = chordMouse.toIntArray().takeIf { it.isNotEmpty() }
        if (key != null && (keys != null || mouse != null)) runCatching { bridge()?.menuRebind(key, keys, mouse, true) }
        stop()
        commit()
    }

    private fun keyPressed(code: Int, escape: Boolean, key: () -> InputConstants.Key): Boolean {
        if (KeyBindCapture.target == null) return true
        val chord = if (escape) null else chordTarget()
        if (chord != null) {
            if (code > 0) {
                chordKeys.add(code)
                heldKeys.add(code)
                preview(chord)
            }
            return false
        }
        swallowKey = code
        bind(if (escape) InputConstants.UNKNOWN else key())
        return false
    }

    private fun keyReleased(code: Int): Boolean {
        if (heldKeys.remove(code)) {
            finishChord()
            return false
        }
        if (swallowKey != code) return true
        swallowKey = null
        return false
    }

    private fun mousePressed(button: Int): Boolean {
        if (KeyBindCapture.target == null) return true
        if (button == InputConstants.MOUSE_BUTTON_LEFT && KeyBindCapture.overChip) {
            cancelOnRelease = true
            return true
        }
        val chord = chordTarget()
        if (chord != null) {
            chordMouse.add(button)
            heldMouse.add(button)
            preview(chord)
            return false
        }
        swallowButton = button
        bind(InputConstants.Type.MOUSE.getOrCreate(button))
        return false
    }

    private fun mouseReleased(button: Int): Boolean {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && cancelOnRelease) {
            cancelOnRelease = false
            stop()
            return true
        }
        if (heldMouse.remove(button)) {
            finishChord()
            return false
        }
        if (swallowButton != button) return true
        swallowButton = null
        return false
    }

    private fun screenRemoved() {
        swallowKey = null
        swallowButton = null
        cancelOnRelease = false
        if (heldKeys.isNotEmpty() || heldMouse.isNotEmpty()) stop()
    }

    private fun chordIds(key: KeyMapping): List<String> = runCatching {
        bridge()?.bindFor(key)?.let { bind ->
            //? if >= 26.3 {
            (bind.keyCodes ?: IntArray(0)).filter { it > 0 }.map { InputConstants.Type.KEYBOARD.getOrCreate(it).name } +
            //?} else {
            /*(bind.keyCodes ?: IntArray(0)).filter { it > 0 }.map { InputConstants.Type.KEYSYM.getOrCreate(it).name } +
            *///?}
                modifierIds(bind.mods) +
                (bind.mouseBtns ?: IntArray(0)).filter { it >= 0 }.map { InputConstants.Type.MOUSE.getOrCreate(it).name }
        }
    }.getOrNull().orEmpty()

    private fun modifierIds(mods: Byte): List<String> = buildList {
        if (KeyModifiers.has(mods, KeyModifiers.CTRL)) addAll(listOf("key.keyboard.left.control", "key.keyboard.right.control"))
        if (KeyModifiers.has(mods, KeyModifiers.SHIFT)) addAll(listOf("key.keyboard.left.shift", "key.keyboard.right.shift"))
        if (KeyModifiers.has(mods, KeyModifiers.ALT)) addAll(listOf("key.keyboard.left.alt", "key.keyboard.right.alt"))
        if (KeyModifiers.has(mods, KeyModifiers.META)) addAll(listOf("key.keyboard.left.win", "key.keyboard.right.win"))
    }

    private fun bind(key: InputConstants.Key) {
        val id = KeyBindCapture.target ?: return
        find(id)?.setKey(key)
        stop()
        commit()
    }

    private fun find(id: String): KeyMapping? = byId[id]

    private fun commit() {
        KeyMapping.resetMapping()
        mc.options.save()
        runCatching { MinecraftKeybindBridgeImpl.instance()?.reconcile() }
        KeyBindCapture.revision++
    }

    private fun row(key: KeyMapping, id: String, all: Array<KeyMapping>, order: Int): KeyBindRow {
        val conflicts = if (key.isUnbound) {
            emptyList()
        } else {
            //? if >= 1.21.10 {
            all.filter { other -> other !== key && key.same(other) && (!other.isDefault || !key.isDefault) }
            //?} else {
            /*all.filter { other -> other !== key && key.same(other) }
            *///?}
                .map { Component.translatable(it.name).string }
        }
        val text = if (conflicts.isEmpty()) null else Component.translatable("controls.keybinds.duplicateKeybinds", conflicts.joinToString(", ")).string
        return KeyBindRow(
            id,
            Component.translatable(key.name).string,
            if (key.isUnbound) "" else key.translatedKeyMessage.string,
            key.saveString(),
            //? if >= 1.21.10 {
            key.category.label().string,
            owner(key.category.id().namespace),
            //?} else {
            /*Component.translatable(key.category).string,
            categoryOwner(key),
            *///?}
            order,
            key.isDefault,
            key.isUnbound,
            conflicts,
            text,
            mirrored(key),
            chordIds(key),
        )
    }

    private fun owner(namespace: String): String = owners.getOrPut(namespace) {
        if (namespace == "minecraft") return@getOrPut "Minecraft"
        FabricLoader.getInstance().getModContainer(namespace)
            .map { it.metadata.name }
            .filter { it.isNotBlank() }
            .orElse(namespace.replaceFirstChar { it.uppercase() })
    }

    //? if < 1.21.10 {
    /*private val vanillaCategories = setOf(
        "key.categories.movement",
        "key.categories.misc",
        "key.categories.multiplayer",
        "key.categories.gameplay",
        "key.categories.ui",
        "key.categories.inventory",
        "key.categories.creative",
        "key.categories.spectator",
    )

    private val ignoredTokens = setOf(
        "key", "keys", "keybind", "keybinds", "keybinding", "keybindings", "category", "categories",
        "minecraft", "java", "fabricloader", "fabric",
    )

    private fun categoryOwner(key: KeyMapping): String {
        val category = key.category
        if (category in vanillaCategories) return "Minecraft"
        return owners.getOrPut(category + "|" + key.name) {
            val compact = category.lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            (tokens(key.name) + tokens(category) + compact)
                .filter { it.isNotEmpty() && it !in ignoredTokens }
                .distinct()
                .firstNotNullOfOrNull { id ->
                    FabricLoader.getInstance().getModContainer(id).map { it.metadata.name }.filter { it.isNotBlank() }.orElse(null)
                }
                ?: Component.translatable(category).string
        }
    }

    private fun tokens(text: String): List<String> = text.lowercase().split('.', ':', ' ', '/')
    *///?}

    private fun install() {
        if (installed) return
        installed = true
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ -> hook(screen) }
    }

    //? if >= 1.21.10 {
    private fun hook(screen: Screen) {
        hookRemove(screen)
        val press = ScreenKeyboardEvents.allowKeyPress(screen)
        if (hooked.add(press)) {
            press.register { _, event -> keyPressed(event.key(), event.isEscape) { InputConstants.getKey(event) } }
        }
        val release = ScreenKeyboardEvents.allowKeyRelease(screen)
        if (hooked.add(release)) {
            release.register { _, event -> keyReleased(event.key()) }
        }
        //? if >= 26.1 {
        val chars = ScreenKeyboardEvents.allowCharType(screen)
        if (hooked.add(chars)) {
            chars.register { _, _ -> KeyBindCapture.target == null && swallowKey == null }
        }
        //?}
        val click = ScreenMouseEvents.allowMouseClick(screen)
        if (hooked.add(click)) {
            click.register { _, event -> mousePressed(event.button()) }
        }
        val mouseUp = ScreenMouseEvents.allowMouseRelease(screen)
        if (hooked.add(mouseUp)) {
            mouseUp.register { _, event -> mouseReleased(event.button()) }
        }
    }
    //?} else {
    /*private fun hook(screen: Screen) {
        hookRemove(screen)
        val press = ScreenKeyboardEvents.allowKeyPress(screen)
        if (hooked.add(press)) {
            press.register { _, key, scancode, _ -> keyPressed(key, key == InputConstants.KEY_ESCAPE) { InputConstants.getKey(key, scancode) } }
        }
        val release = ScreenKeyboardEvents.allowKeyRelease(screen)
        if (hooked.add(release)) {
            release.register { _, key, _, _ -> keyReleased(key) }
        }
        val click = ScreenMouseEvents.allowMouseClick(screen)
        if (hooked.add(click)) {
            click.register { _, _, _, button -> mousePressed(button) }
        }
        val mouseUp = ScreenMouseEvents.allowMouseRelease(screen)
        if (hooked.add(mouseUp)) {
            mouseUp.register { _, _, _, button -> mouseReleased(button) }
        }
    }
    *///?}

    private fun hookRemove(screen: Screen) {
        val remove = ScreenEvents.remove(screen)
        if (hooked.add(remove)) {
            remove.register { screenRemoved() }
        }
    }
}
//?} elif = 1.8.9 {
/*internal object KeyBindsPlatform {
    private const val LEGACY_PREFIX = "key.legacy."
    private val mc: Minecraft get() = Minecraft.getInstance()
    private val capture = BooleanSupplier { KeyBindCapture.target != null }
    private val owners = HashMap<String, String>()
    private var swallowKey: Int? = null
    private var swallowButton: Int? = null
    private var cancelOnRelease = false
    private val vanillaCategories = setOf(
        "key.categories.movement",
        "key.categories.misc",
        "key.categories.multiplayer",
        "key.categories.gameplay",
        "key.categories.ui",
        "key.categories.inventory",
        "key.categories.stream",
    )
    private val ignoredTokens = setOf(
        "key", "keys", "keybind", "keybinds", "keybinding", "keybindings", "category", "categories",
        "minecraft", "java", "fabricloader",
    )
    private val fallbackText = mapOf("controls.keybinds.title" to "Key Binds")
    private var byId: Map<String, KeyBinding> = emptyMap()
    private val chordKeys = LinkedHashSet<Int>()
    private val chordMouse = LinkedHashSet<Int>()
    private val heldKeys = HashSet<Int>()
    private val heldMouse = HashSet<Int>()

    const val keymap: Boolean = true

    fun rows(): List<KeyBindRow> {
        val all = bindings()
        val ids = HashMap<String, KeyBinding>()
        val rows = all.sorted().mapIndexed { index, key ->
            val id = if (key.name in ids) key.name + "#" + index else key.name
            ids[id] = key
            row(key, id, all, index)
        }
        byId = ids
        return rows
    }

    fun keyLabel(keyId: String): String? {
        if (keyId.startsWith(LEGACY_PREFIX)) return keyId.removePrefix(LEGACY_PREFIX).toIntOrNull()?.let { GameOptions.getKeyName(it) }
        return runCatching { label(InputConstants.getKey(keyId)) }.getOrNull()
    }

    fun text(key: String): String {
        val text = I18n.translate(key)
        return if (text != key) text else fallbackText[key] ?: key
    }

    fun anyChanged(): Boolean = bindings().any { !isDefault(it) }

    fun reset(id: String) {
        val key = find(id) ?: return
        mc.options.setKeyCode(key, key.defaultKeyCode)
        commit()
    }

    fun clear(id: String) {
        val key = find(id) ?: return
        if (key.keyCode == 0) return
        mc.options.setKeyCode(key, 0)
        commit()
    }

    fun resetAll() {
        bindings().forEach { it.setKeyCode(it.defaultKeyCode) }
        commit()
    }

    fun start(id: String) {
        resetChord()
        if (KeyBindCapture.target == null) KeybindManager.addInputCapture(capture)
        KeyBindCapture.target = id
        KeyBindCapture.revision++
    }

    fun stop() {
        resetChord()
        if (KeyBindCapture.target == null) return
        KeyBindCapture.target = null
        KeybindManager.removeInputCapture(capture)
        KeyBindCapture.revision++
    }

    @JvmStatic
    fun consumeKey(key: Int, pressed: Boolean): Boolean {
        if (KeyBindCapture.target != null) {
            if (key <= 0) return true
            val chord = if (key == Keyboard.KEY_ESCAPE) null else chordTarget()
            if (chord != null) {
                if (pressed) {
                    val code = KeyCodes.fromLegacy(key).value
                    if (code > 0) {
                        chordKeys.add(code)
                        heldKeys.add(key)
                        preview(chord)
                    }
                } else if (heldKeys.remove(key)) {
                    finishChord()
                }
                return true
            }
            if (!pressed) return false
            swallowKey = key
            bind(if (key == Keyboard.KEY_ESCAPE) 0 else key)
            return true
        }
        val held = swallowKey ?: return false
        if (key <= 0) return true
        if (key != held) return false
        if (!pressed) swallowKey = null
        return true
    }

    @JvmStatic
    fun consumeMouse(button: Int, pressed: Boolean): Boolean {
        if (button < 0) return false
        if (button == 0 && cancelOnRelease && !pressed) {
            cancelOnRelease = false
            stop()
            return false
        }
        if (KeyBindCapture.target != null) {
            if (button == 0 && KeyBindCapture.overChip) {
                if (pressed) cancelOnRelease = true
                return false
            }
            val chord = chordTarget()
            if (chord != null) {
                if (pressed) {
                    chordMouse.add(KeyCodes.mouseFromLegacy(button))
                    heldMouse.add(button)
                    preview(chord)
                    return true
                }
                if (!heldMouse.remove(button)) return false
                finishChord()
                return true
            }
            if (!pressed) return false
            swallowButton = button
            bind(button - 100)
            return true
        }
        if (pressed || swallowButton != button) return false
        swallowButton = null
        return true
    }

    private fun bindings(): Array<KeyBinding> = mc.options.keyBindings

    private fun find(id: String): KeyBinding? = byId[id]

    private fun isDefault(key: KeyBinding): Boolean = key.keyCode == key.defaultKeyCode

    private fun chordIds(key: KeyBinding): List<String> = runCatching {
        bridge()?.bindFor(key)?.let { bind ->
            (bind.keyCodes ?: IntArray(0)).filter { it > 0 }.map { InputConstants.Type.KEYBOARD.getOrCreate(it).name } +
                modifierIds(bind.mods) +
                (bind.mouseBtns ?: IntArray(0)).filter { it > 0 }.map { InputConstants.Type.MOUSE.getOrCreate(it).name }
        }
    }.getOrNull().orEmpty()

    private fun modifierIds(mods: Byte): List<String> = buildList {
        if (KeyModifiers.has(mods, KeyModifiers.CTRL)) addAll(listOf("key.keyboard.left.control", "key.keyboard.right.control"))
        if (KeyModifiers.has(mods, KeyModifiers.SHIFT)) addAll(listOf("key.keyboard.left.shift", "key.keyboard.right.shift"))
        if (KeyModifiers.has(mods, KeyModifiers.ALT)) addAll(listOf("key.keyboard.left.alt", "key.keyboard.right.alt"))
        if (KeyModifiers.has(mods, KeyModifiers.META)) addAll(listOf("key.keyboard.left.win", "key.keyboard.right.win"))
    }

    private fun bridge(): MinecraftKeybindBridgeImpl? = runCatching { MinecraftKeybindBridgeImpl.instance() }.getOrNull()

    private fun mirrored(key: KeyBinding): Boolean = runCatching { bridge()?.bindFor(key) != null }.getOrDefault(false)

    private fun chordTarget(): KeyBinding? = KeyBindCapture.target?.let(::find)?.takeIf(::mirrored)

    private fun resetChord() {
        KeyBindCapture.overChip = false
        chordKeys.clear()
        chordMouse.clear()
        heldKeys.clear()
        heldMouse.clear()
        if (KeyBindCapture.preview == null) return
        runCatching { bridge()?.clearPreview() }
        KeyBindCapture.preview = null
    }

    private fun preview(key: KeyBinding) {
        KeyBindCapture.preview = runCatching {
            bridge()?.let {
                it.setPreview(key, chordKeys.toIntArray().takeIf { keys -> keys.isNotEmpty() }, chordMouse.toIntArray().takeIf { mouse -> mouse.isNotEmpty() })
                it.previewFor(key)?.string
            }
        }.getOrNull()
    }

    private fun finishChord() {
        if (heldKeys.isNotEmpty() || heldMouse.isNotEmpty()) return
        val key = chordTarget()
        val keys = chordKeys.toIntArray().takeIf { it.isNotEmpty() }
        val mouse = chordMouse.toIntArray().takeIf { it.isNotEmpty() }
        if (key != null && (keys != null || mouse != null)) runCatching { bridge()?.menuRebind(key, keys, mouse, true) }
        stop()
        commit()
    }

    private fun comboText(key: KeyBinding, base: String): String = runCatching {
        bridge()?.bindFor(key)?.let { MinecraftKeybindBridgeImpl.comboLabel(it, Component.literal(base))?.string }
    }.getOrNull() ?: base

    private fun clashes(key: KeyBinding, other: KeyBinding): Boolean = runCatching {
        bridge()?.takeIf { it.bindFor(key) != null || it.bindFor(other) != null }?.menuConflict(key, other)
    }.getOrNull() ?: (other.keyCode == key.keyCode)

    private fun bind(code: Int) {
        val id = KeyBindCapture.target ?: return
        find(id)?.let { mc.options.setKeyCode(it, code) }
        stop()
        commit()
    }

    private fun commit() {
        KeyBinding.resetMapping()
        mc.options.save()
        runCatching { MinecraftKeybindBridgeImpl.instance()?.reconcile() }
        KeyBindCapture.revision++
    }

    private fun keyId(code: Int): String {
        val key = KeyCodes.fromLegacy(code)
        return if (key == InputConstants.UNKNOWN && code != 0) LEGACY_PREFIX + code else key.name
    }

    private fun keyText(code: Int): String {
        val key = KeyCodes.fromLegacy(code)
        return if (key == InputConstants.UNKNOWN) GameOptions.getKeyName(code) else label(key)
    }

    private fun label(key: InputConstants.Key): String {
        val text = key.displayName.string
        if (key.type != InputConstants.Type.MOUSE || !text.startsWith("key.")) return text
        return when (key.name) {
            "key.mouse.left" -> "Left Button"
            "key.mouse.right" -> "Right Button"
            "key.mouse.middle" -> "Middle Button"
            else -> "Button " + key.value
        }
    }

    private fun row(key: KeyBinding, id: String, all: Array<KeyBinding>, order: Int): KeyBindRow {
        val code = key.keyCode
        val unbound = code == 0
        val conflicts = if (unbound) {
            emptyList()
        } else {
            all.filter { other -> other !== key && clashes(key, other) }
                .map { I18n.translate(it.name) }
        }
        val text = if (conflicts.isEmpty()) null else "This key is also used for: " + conflicts.joinToString(", ")
        return KeyBindRow(
            id,
            I18n.translate(key.name),
            if (unbound) "" else comboText(key, keyText(code)),
            keyId(code),
            I18n.translate(key.category),
            owner(key),
            order,
            isDefault(key),
            unbound,
            conflicts,
            text,
            mirrored(key),
            chordIds(key),
        )
    }

    private fun owner(key: KeyBinding): String {
        if (key.category in vanillaCategories) return "Minecraft"
        return owners.getOrPut(key.category + "|" + key.name) {
            val compact = key.category.lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            (tokens(key.name) + tokens(key.category) + compact)
                .filter { it.isNotEmpty() && it !in ignoredTokens }
                .distinct()
                .firstNotNullOfOrNull { id ->
                    FabricLoader.getInstance().getModContainer(id).map { it.metadata.name }.filter { it.isNotBlank() }.orElse(null)
                }
                ?: I18n.translate(key.category)
        }
    }

    private fun tokens(text: String): List<String> = text.lowercase().split('.', ':', ' ', '/')
}
*///?}
