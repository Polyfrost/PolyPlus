package org.polyfrost.polyplus.client.gui.screens.settings

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.gui.chat.ChatGui
import net.minecraft.client.options.GameOptions
import net.minecraft.client.render.Window
import net.minecraft.client.render.model.PlayerModelPart
import net.minecraft.client.resource.language.I18n
import net.minecraft.client.sound.SoundCategory
import net.minecraft.entity.living.player.PlayerEntity
import java.util.Locale
import kotlin.math.roundToInt

internal object SettingsLegacyOptions {
    private val mc: Minecraft get() = Minecraft.getInstance()
    private val options: GameOptions get() = mc.options

    private val steps = mapOf(
        GameOptions.Option.FOV to 1f,
        GameOptions.Option.RENDER_DISTANCE to 1f,
        GameOptions.Option.FRAMERATE_LIMIT to 10f,
        GameOptions.Option.MIPMAP_LEVELS to 1f,
    )

    fun text(key: String): String = I18n.translate(key)

    fun save() = options.save()

    fun rows(vararg list: GameOptions.Option): List<SettingRow> = list.mapNotNull { row(it) }

    fun row(option: GameOptions.Option, wide: Boolean = false, enabled: Boolean = true): SettingRow? {
        val key = option.getName()
        val kind = when {
            option.isFloat() -> range(option)
            option.isBoolean() -> toggle(option)
            else -> choice(option)
        } ?: return null
        return SettingRow(key, text(key), null, kind, enabled, wide)
    }

    fun soundRows(): List<SettingRow> = SoundCategory.values().map { category ->
        val key = "soundCategory." + category.getName()
        SettingRow(
            key,
            text(key),
            null,
            SettingKind.Range(options.getSoundCategoryVolume(category), 0f..1f, 0f, true, ::percentOrOff) {
                options.setSoundCategoryVolume(category, it.coerceIn(0f, 1f))
            },
            wide = category == SoundCategory.MASTER,
        )
    }

    fun skinRows(): List<SettingRow> = PlayerModelPart.values().map { part ->
        SettingRow("part-" + part.getKey(), part.getName().getString(), null, SettingKind.Toggle(part in options.getPlayerModelParts()) {
            options.setPlayerModelPart(part, it)
            options.save()
        })
    }

    fun currentLanguage(): String = mc.getLanguageManager().getLanguage().getCode()

    fun languages(): List<LanguageEntry> = mc.getLanguageManager().getLanguages().map { language ->
        val label = language.toString()
        LanguageEntry(language.getCode(), label, label.lowercase(Locale.ROOT))
    }

    fun applyLanguage(code: String) {
        val manager = mc.getLanguageManager()
        if (code == manager.getLanguage().getCode()) return
        val language = manager.getLanguages().firstOrNull { it.getCode() == code } ?: return
        manager.setLanguage(language)
        options.language = code
        mc.reloadResources()
        mc.textRenderer.setUnicode(manager.isUnicode() || options.forceUnicodeFont)
        mc.textRenderer.setBidirectional(manager.isBidirectional())
        options.save()
    }

    fun isEnglish(): Boolean = runCatching { currentLanguage().lowercase(Locale.ROOT) }.getOrNull()?.startsWith("en_") == true

    private fun toggle(option: GameOptions.Option): SettingKind = SettingKind.Toggle(options.getBoolean(option)) { on ->
        if (options.getBoolean(option) != on) options.set(option, 1)
    }

    private fun range(option: GameOptions.Option): SettingKind {
        val min = option.denormalize(0f)
        val max = option.denormalize(1f)
        val current = options.getFloat(option).coerceIn(min, max)
        return SettingKind.Range(current, min..max, steps[option] ?: 0f, option != GameOptions.Option.MIPMAP_LEVELS, { valueText(option, it) }) { value ->
            options.set(option, option.clampAndRoundToStepMultiple(value))
        }
    }

    private fun choice(option: GameOptions.Option): SettingKind? {
        val (labels, current) = choiceState(option) ?: return null
        return SettingKind.Choice(labels, current) { index ->
            val now = choiceState(option)?.second ?: current
            val delta = ((index - now) % labels.size + labels.size) % labels.size
            if (delta != 0) {
                options.set(option, delta)
                if (option == GameOptions.Option.GUI_SCALE) {
                    val window = Window(mc)
                    mc.screen?.init(mc, window.width, window.height)
                }
            }
        }
    }

    private fun choiceState(option: GameOptions.Option): Pair<List<String>, Int>? {
        val o = options
        val state = when (option) {
            GameOptions.Option.GUI_SCALE -> keys("options.guiScale.auto", "options.guiScale.small", "options.guiScale.normal", "options.guiScale.large") to o.guiScale
            GameOptions.Option.PARTICLES -> keys("options.particles.all", "options.particles.decreased", "options.particles.minimal") to o.particles
            GameOptions.Option.AMBIENT_OCCLUSION -> keys("options.ao.off", "options.ao.min", "options.ao.max") to o.ambientOcclusion
            GameOptions.Option.RENDER_CLOUDS -> keys("options.off", "options.graphics.fast", "options.graphics.fancy") to o.cloudRenderMode
            GameOptions.Option.GRAPHICS -> keys("options.graphics.fast", "options.graphics.fancy") to (if (o.fancyGraphics) 1 else 0)
            GameOptions.Option.CHAT_VISIBILITY ->
                PlayerEntity.ChatVisibility.values().sortedBy { it.getIndex() }.map { text(it.getId()) } to o.chatVisibility.getIndex()
            else -> return null
        }
        return state.first to state.second.coerceIn(0, state.first.lastIndex)
    }

    private fun keys(vararg keys: String): List<String> = keys.map { text(it) }

    private fun percentOrOff(value: Float): String = if (value <= 0f) text("options.off") else "${(value * 100f).toInt()}%"

    private fun valueText(option: GameOptions.Option, value: Float): String = when (option) {
        GameOptions.Option.SENSITIVITY -> when {
            value <= 0f -> text("options.sensitivity.min")
            value >= 1f -> text("options.sensitivity.max")
            else -> "${(value * 200f).toInt()}%"
        }
        GameOptions.Option.FOV -> when (value.roundToInt()) {
            70 -> text("options.fov.min")
            110 -> text("options.fov.max")
            else -> value.roundToInt().toString()
        }
        GameOptions.Option.FRAMERATE_LIMIT -> if (value >= option.getMax()) text("options.framerateLimit.max") else "${value.roundToInt()} fps"
        GameOptions.Option.GAMMA -> when {
            value <= 0f -> text("options.gamma.min")
            value >= 1f -> text("options.gamma.max")
            else -> "+${(value * 100f).toInt()}%"
        }
        GameOptions.Option.CHAT_OPACITY -> "${(value * 90f + 10f).toInt()}%"
        GameOptions.Option.CHAT_HEIGHT_FOCUSED, GameOptions.Option.CHAT_HEIGHT_UNFOCUSED -> "${ChatGui.getHeight(value)}px"
        GameOptions.Option.CHAT_WIDTH -> "${ChatGui.getWidth(value)}px"
        GameOptions.Option.RENDER_DISTANCE -> "${value.roundToInt()} chunks"
        GameOptions.Option.MIPMAP_LEVELS -> if (value.roundToInt() == 0) text("options.off") else value.roundToInt().toString()
        else -> percentOrOff(value)
    }
}
*///?}
