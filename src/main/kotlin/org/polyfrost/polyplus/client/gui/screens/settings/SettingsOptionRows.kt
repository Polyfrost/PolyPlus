package org.polyfrost.polyplus.client.gui.screens.settings

//? if >= 26.3 {
import net.minecraft.client.Minecraft
import net.minecraft.client.OptionInstance
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import org.polyfrost.polyplus.mixin.client.access.SettingsOptionInstanceAccessor
import org.polyfrost.polyplus.mixin.client.access.SettingsTooltipAccessor
import java.lang.reflect.Field
import kotlin.math.roundToInt

internal object SettingsOptionRows {
    private val captionField: Field? by lazy {
        runCatching { OptionInstance::class.java.getDeclaredField("caption").apply { isAccessible = true } }.getOrNull()
    }

    private val keyLike = Regex("^[a-z0-9_]+(\\.[A-Za-z0-9_]+)+$")

    @Suppress("UNCHECKED_CAST")
    fun row(instance: OptionInstance<*>, wide: Boolean = false, enabled: Boolean = true, hint: String? = null): SettingRow? {
        val option = instance as OptionInstance<Any>
        val caption = caption(option)
        val kind = kindOf(option, caption) ?: return null
        return SettingRow(keyOf(option), caption, hint ?: hint(option), kind, enabled, wide)
    }

    fun keyOf(option: OptionInstance<*>): String {
        val component = runCatching { captionField?.get(option) as? Component }.getOrNull()
        return (component?.contents as? TranslatableContents)?.key ?: option.toString()
    }

    private fun caption(option: OptionInstance<Any>): String {
        val plain = option.toString()
        val key = keyOf(option)
        val missing = if (key != plain) !Language.getInstance().has(key) else keyLike.matches(plain)
        if (!missing) return plain
        val sample = labelOf(option, option.get())
        val prefix = sample.substringBefore(':', "").trim()
        return prefix.ifEmpty { plain }
    }

    @Suppress("UNCHECKED_CAST")
    private fun kindOf(option: OptionInstance<Any>, caption: String): SettingKind? {
        val values: Any = option.values()
        if (values is OptionInstance.SliderableOrCyclableValueSet<*> && !values.createCycleButton()) {
            return slider(option, values as OptionInstance.SliderableValueSet<Any>, caption)
        }
        if (values is OptionInstance.CycleableValueSet<*>) {
            val cycle = values as OptionInstance.CycleableValueSet<Any>
            val list = cycle.valueListSupplier().selectedList
            val current = option.get()
            if (current is Boolean && list.size == 2 && list.all { it is Boolean }) {
                return SettingKind.Toggle(current) {
                    cycle.valueSetter().set(option, it)
                    save()
                }
            }
            return choice(option, list, caption) { cycle.valueSetter().set(option, it) }
        }
        if (values is OptionInstance.SliderableEnum<*>) return choice(option, values.values() as List<Any>, caption) { option.set(it) }
        if (values is OptionInstance.SliderableValueSet<*>) return slider(option, values as OptionInstance.SliderableValueSet<Any>, caption)
        return null
    }

    private fun choice(option: OptionInstance<Any>, list: List<Any>, caption: String, apply: (Any) -> Unit): SettingKind? {
        if (list.isEmpty()) return null
        val current = option.get()
        val values = if (current in list) list else listOf(current) + list
        return SettingKind.Choice(values.map { valueText(option, it, caption) }, values.indexOf(current)) { index ->
            values.getOrNull(index)?.let {
                apply(it)
                save()
            }
        }
    }

    private fun slider(option: OptionInstance<Any>, values: OptionInstance.SliderableValueSet<Any>, caption: String): SettingKind {
        val live = values.applyValueImmediately()
        val raw: Any = values
        if (raw is OptionInstance.IntRangeBase) {
            val min = raw.minInclusive()
            val max = raw.maxInclusive()
            val current = (option.get() as? Int ?: min).coerceIn(min, max)
            return SettingKind.Range(
                current.toFloat(),
                min.toFloat()..max.toFloat(),
                1f,
                live,
                { valueText(option, it.roundToInt().coerceIn(min, max), caption) },
            ) { option.set(it.roundToInt().coerceIn(min, max)) }
        }
        return SettingKind.Range(
            values.toSliderValue(option.get()).toFloat(),
            0f..1f,
            0f,
            live,
            { valueText(option, values.fromSliderValue(it.toDouble().coerceIn(0.0, 1.0)), caption) },
        ) { option.set(values.fromSliderValue(it.toDouble().coerceIn(0.0, 1.0))) }
    }

    private fun labelComponent(option: OptionInstance<Any>, value: Any): Component? =
        runCatching { (option as Any as SettingsOptionInstanceAccessor).`polyplus$valueLabel`().apply(value) }.getOrNull()

    private fun labelOf(option: OptionInstance<Any>, value: Any): String = labelComponent(option, value)?.string ?: value.toString()

    private fun valueText(option: OptionInstance<Any>, value: Any, caption: String): String {
        val component = labelComponent(option, value) ?: return value.toString()
        captionless(component, option)?.let { return it }
        val full = component.string
        for (prefix in listOf(caption, option.toString())) {
            if (prefix.isNotEmpty() && full.startsWith(prefix)) return trimSeparator(full.substring(prefix.length)).ifEmpty { full }
        }
        return full
    }

    private fun captionless(component: Component, option: OptionInstance<Any>): String? {
        val contents = component.contents as? TranslatableContents ?: return null
        val args = contents.args
        if (args.isEmpty()) return null
        val first = args[0]
        val captionKey = keyOf(option)
        val matches = (first as? Component)?.let { (it.contents as? TranslatableContents)?.key == captionKey || it.string == option.toString() } ?: false
        if (!matches) return null
        val rest = args.copyOfRange(1, args.size)
        val text = Component.translatable(contents.key, "", *rest).string
        return trimSeparator(text).ifEmpty { null }
    }

    private fun trimSeparator(text: String): String = text.trim().trimStart(':', '：', ' ', ' ', ' ').trim()

    private fun hint(option: OptionInstance<Any>): String? = runCatching {
        val tooltip = (option as Any as SettingsOptionInstanceAccessor).`polyplus$tooltip`().apply(option.get()) ?: return null
        (tooltip as Any as SettingsTooltipAccessor).`polyplus$message`().string.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun save() = Minecraft.getInstance().options.save()
}
//?}
