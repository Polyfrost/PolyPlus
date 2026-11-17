@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
//? if >= 26.3 {
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
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
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
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
import org.polyfrost.oneconfig.internal.legacy.InputConstants
import org.polyfrost.oneconfig.internal.legacy.KeyCodes
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
)

internal object KeyBindCapture {
    var target by mutableStateOf<String?>(null)
        internal set

    var revision by mutableIntStateOf(0)
        internal set
}

//? if >= 26.3 {
internal object KeyBindsPlatform {
    private val mc: Minecraft get() = Minecraft.getInstance()
    private val hooked: MutableSet<Event<*>> = Collections.newSetFromMap(WeakHashMap())
    private var installed = false
    private var swallowKey: Int? = null
    private var swallowButton: Int? = null
    private val capture = BooleanSupplier { KeyBindCapture.target != null }
    private val owners = HashMap<String, String>()

    const val keymap: Boolean = true

    fun rows(): List<KeyBindRow> {
        val all = mc.options.keyMappings
        return all.sorted().mapIndexed { index, key -> row(key, all, index) }
    }

    fun keyLabel(keyId: String): String? = runCatching { InputConstants.getKey(keyId).displayName.string }.getOrNull()

    fun text(key: String): String = Component.translatable(key).string

    fun anyChanged(): Boolean = mc.options.keyMappings.any { !it.isDefault }

    fun reset(id: String) {
        val key = KeyMapping.get(id) ?: return
        key.setKey(key.defaultKey)
        commit()
    }

    fun clear(id: String) {
        val key = KeyMapping.get(id) ?: return
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
        if (KeyBindCapture.target == null) KeybindManager.addInputCapture(capture)
        KeyBindCapture.target = id
        KeyBindCapture.revision++
    }

    fun stop() {
        if (KeyBindCapture.target == null) return
        KeyBindCapture.target = null
        KeybindManager.removeInputCapture(capture)
        KeyBindCapture.revision++
    }

    private fun bind(key: InputConstants.Key) {
        val id = KeyBindCapture.target ?: return
        KeyMapping.get(id)?.setKey(key)
        stop()
        commit()
    }

    private fun commit() {
        KeyMapping.resetMapping()
        mc.options.save()
        KeyBindCapture.revision++
    }

    private fun row(key: KeyMapping, all: Array<KeyMapping>, order: Int): KeyBindRow {
        val conflicts = if (key.isUnbound) {
            emptyList()
        } else {
            all.filter { other -> other !== key && key.same(other) && (!other.isDefault || !key.isDefault) }
                .map { Component.translatable(it.name).string }
        }
        val text = if (conflicts.isEmpty()) null else Component.translatable("controls.keybinds.duplicateKeybinds", conflicts.joinToString(", ")).string
        return KeyBindRow(
            key.name,
            Component.translatable(key.name).string,
            if (key.isUnbound) "" else key.translatedKeyMessage.string,
            key.saveString(),
            key.category.label().string,
            owner(key.category.id().namespace),
            order,
            key.isDefault,
            key.isUnbound,
            conflicts,
            text,
        )
    }

    private fun owner(namespace: String): String = owners.getOrPut(namespace) {
        if (namespace == "minecraft") return@getOrPut "Minecraft"
        FabricLoader.getInstance().getModContainer(namespace)
            .map { it.metadata.name }
            .filter { it.isNotBlank() }
            .orElse(namespace.replaceFirstChar { it.uppercase() })
    }

    private fun install() {
        if (installed) return
        installed = true
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ -> hook(screen) }
    }

    private fun hook(screen: Screen) {
        val press = ScreenKeyboardEvents.allowKeyPress(screen)
        if (hooked.add(press)) {
            press.register { _, event ->
                if (KeyBindCapture.target == null) return@register true
                swallowKey = event.key()
                bind(if (event.isEscape) InputConstants.UNKNOWN else InputConstants.getKey(event))
                false
            }
        }
        val release = ScreenKeyboardEvents.allowKeyRelease(screen)
        if (hooked.add(release)) {
            release.register { _, event ->
                if (swallowKey != event.key()) return@register true
                swallowKey = null
                false
            }
        }
        val chars = ScreenKeyboardEvents.allowCharType(screen)
        if (hooked.add(chars)) {
            chars.register { _, _ -> KeyBindCapture.target == null && swallowKey == null }
        }
        val click = ScreenMouseEvents.allowMouseClick(screen)
        if (hooked.add(click)) {
            click.register { _, event ->
                if (KeyBindCapture.target == null) return@register true
                swallowButton = event.button()
                bind(InputConstants.Type.MOUSE.getOrCreate(event.button()))
                false
            }
        }
        val mouseUp = ScreenMouseEvents.allowMouseRelease(screen)
        if (hooked.add(mouseUp)) {
            mouseUp.register { _, event ->
                if (swallowButton != event.button()) return@register true
                swallowButton = null
                false
            }
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

    const val keymap: Boolean = true

    fun rows(): List<KeyBindRow> {
        val all = bindings()
        return all.sorted().mapIndexed { index, key -> row(key, all, index) }
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
        if (KeyBindCapture.target == null) KeybindManager.addInputCapture(capture)
        KeyBindCapture.target = id
        KeyBindCapture.revision++
    }

    fun stop() {
        if (KeyBindCapture.target == null) return
        KeyBindCapture.target = null
        KeybindManager.removeInputCapture(capture)
        KeyBindCapture.revision++
    }

    @JvmStatic
    fun consumeKey(key: Int, pressed: Boolean): Boolean {
        if (KeyBindCapture.target != null) {
            if (key <= 0) return true
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
        if (KeyBindCapture.target != null) {
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

    private fun find(id: String): KeyBinding? = bindings().firstOrNull { it.name == id }

    private fun isDefault(key: KeyBinding): Boolean = key.keyCode == key.defaultKeyCode

    private fun bind(code: Int) {
        val id = KeyBindCapture.target ?: return
        find(id)?.let { mc.options.setKeyCode(it, code) }
        stop()
        commit()
    }

    private fun commit() {
        KeyBinding.resetMapping()
        mc.options.save()
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

    private fun row(key: KeyBinding, all: Array<KeyBinding>, order: Int): KeyBindRow {
        val code = key.keyCode
        val unbound = code == 0
        val conflicts = if (unbound) {
            emptyList()
        } else {
            all.filter { other -> other !== key && other.keyCode == code && (!isDefault(other) || !isDefault(key)) }
                .map { I18n.translate(it.name) }
        }
        val text = if (conflicts.isEmpty()) null else "This key is also used for: " + conflicts.joinToString(", ")
        return KeyBindRow(
            key.name,
            I18n.translate(key.name),
            if (unbound) "" else keyText(code),
            keyId(code),
            I18n.translate(key.category),
            owner(key),
            order,
            isDefault(key),
            unbound,
            conflicts,
            text,
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
*///?} else {
/*internal object KeyBindsPlatform {
    const val keymap: Boolean = false

    fun rows(): List<KeyBindRow> = emptyList()

    fun keyLabel(keyId: String): String? = null

    fun text(key: String): String = key

    fun anyChanged(): Boolean = false

    fun reset(id: String) {}

    fun clear(id: String) {}

    fun resetAll() {}

    fun start(id: String) {}

    fun stop() {}
}
*///?}
