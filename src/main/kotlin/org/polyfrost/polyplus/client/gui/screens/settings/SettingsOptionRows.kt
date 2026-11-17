package org.polyfrost.polyplus.client.gui.screens.settings

//? if > 1.8.9 {
import net.minecraft.client.Minecraft
import net.minecraft.client.OptionInstance
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import org.polyfrost.polyplus.mixin.client.access.SettingsOptionInstanceAccessor
import org.polyfrost.polyplus.mixin.client.access.SettingsTooltipAccessor
import kotlin.math.roundToInt
//? if < 26.2 {
/*import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.components.CycleButton
import java.lang.reflect.Method
import java.lang.reflect.Modifier
*///?}

internal object SettingsOptionRows {
    private val keyLike = Regex("^[a-z0-9_]+(\\.[A-Za-z0-9_]+)+$")

    @Suppress("UNCHECKED_CAST")
    fun row(instance: OptionInstance<*>, wide: Boolean = false, enabled: Boolean = true, hint: String? = null): SettingRow? {
        val option = instance as OptionInstance<Any>
        val caption = caption(option)
        val kind = kindOf(option, caption) ?: return null
        return SettingRow(keyOf(option), caption, hint ?: hint(option), kind, enabled, wide)
    }

    fun keyOf(option: OptionInstance<*>): String {
        val component = runCatching { (option as Any as SettingsOptionInstanceAccessor).`polyplus$caption`() }.getOrNull()
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

    //? if >= 26.2 {
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
    //?} else {
    /*@Suppress("UNCHECKED_CAST")
    private fun kindOf(option: OptionInstance<Any>, caption: String): SettingKind? {
        val values: Any = LegacyValues.valuesOf(option) ?: return null
        if (LegacyValues.sliderOrCycle(values) && LegacyValues.cycleButton(values) == false) return slider(option, values, caption)
        if (LegacyValues.cycleable(values)) {
            val list = LegacyValues.selectedList(values) ?: return null
            val current = option.get()
            val setter: (Any) -> Unit = { LegacyValues.set(values, option, it) }
            if (current is Boolean && list.size == 2 && list.all { it is Boolean }) {
                return SettingKind.Toggle(current) {
                    setter(it)
                    save()
                }
            }
            return choice(option, list, caption, setter)
        }
        LegacyValues.enumValues(values)?.let { list -> return choice(option, list, caption) { option.set(it) } }
        if (LegacyValues.sliderable(values)) return slider(option, values, caption)
        return null
    }
    *///?}

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

    //? if >= 26.2 {
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
    //?} else {
    /*private fun slider(option: OptionInstance<Any>, values: Any, caption: String): SettingKind? {
        val live = LegacyValues.applyImmediately(values)
        val range = LegacyValues.intRange(values)
        if (range != null) {
            val (min, max) = range
            val current = (option.get() as? Int ?: min).coerceIn(min, max)
            return SettingKind.Range(
                current.toFloat(),
                min.toFloat()..max.toFloat(),
                1f,
                live,
                { valueText(option, it.roundToInt().coerceIn(min, max), caption) },
            ) { option.set(it.roundToInt().coerceIn(min, max)) }
        }
        val start = LegacyValues.toSlider(values, option.get()) ?: return null
        return SettingKind.Range(
            start.toFloat(),
            0f..1f,
            0f,
            live,
            { LegacyValues.fromSlider(values, it.toDouble().coerceIn(0.0, 1.0))?.let { value -> valueText(option, value, caption) }.orEmpty() },
        ) { LegacyValues.fromSlider(values, it.toDouble().coerceIn(0.0, 1.0))?.let(option::set) }
    }
    *///?}

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

//? if > 1.8.9 && < 26.2 {
/*private object LegacyValues {
    private const val OPTION = "net.minecraft.class_7172"
    private const val SLIDERABLE = "net.minecraft.class_7172\$class_7176"
    private const val CYCLEABLE = "net.minecraft.class_7172\$class_7305"
    private const val SLIDER_OR_CYCLE = "net.minecraft.class_7172\$class_7306"
    private const val INT_RANGE = "net.minecraft.class_7172\$class_7275"
    private const val SLIDERABLE_ENUM = "net.minecraft.class_7172\$class_12139"
    private const val SETTER = "net.minecraft.class_7172\$class_7305\$class_7274"

    private val sliderableType = type(SLIDERABLE, "net.minecraft.client.OptionInstance\$SliderableValueSet")
    private val cycleableType = type(CYCLEABLE, "net.minecraft.client.OptionInstance\$CycleableValueSet")
    private val sliderOrCycleType = type(SLIDER_OR_CYCLE, "net.minecraft.client.OptionInstance\$SliderableOrCyclableValueSet")
    private val intRangeType = type(INT_RANGE, "net.minecraft.client.OptionInstance\$IntRangeBase")
    private val sliderableEnumType = type(SLIDERABLE_ENUM, "net.minecraft.client.OptionInstance\$SliderableEnum")
    private val setterType = type(SETTER, "net.minecraft.client.OptionInstance\$CycleableValueSet\$ValueSetter")

    private val valuesMethod = method(OptionInstance::class.java, OPTION, "method_41754", "values", "()Lnet/minecraft/class_7172\$class_7178;")
    private val toSliderMethod = method(sliderableType, SLIDERABLE, "method_41765", "toSliderValue", "(Ljava/lang/Object;)D", Any::class.java)
    private val fromSliderMethod = method(sliderableType, SLIDERABLE, "method_41763", "fromSliderValue", "(D)Ljava/lang/Object;", Double::class.javaPrimitiveType!!)
    private val applyImmediatelyMethod = method(sliderableType, SLIDERABLE, "comp_2661", "applyValueImmediately", "()Z")
    private val listSupplierMethod = method(cycleableType, CYCLEABLE, "method_42721", "valueListSupplier", "()Lnet/minecraft/class_5676\$class_5680;")
    private val valueSetterMethod = method(cycleableType, CYCLEABLE, "comp_674", "valueSetter", "()Lnet/minecraft/class_7172\$class_7305\$class_7274;")
    private val createCycleButtonMethod = method(sliderOrCycleType, SLIDER_OR_CYCLE, "method_42722", "createCycleButton", "()Z")
    private val minInclusiveMethod = method(intRangeType, INT_RANGE, "comp_593", "minInclusive", "()I")
    private val maxInclusiveMethod = method(intRangeType, INT_RANGE, "comp_594", "maxInclusive", "()I")
    private val enumValuesMethod = method(sliderableEnumType, SLIDERABLE_ENUM, "comp_5052", "values", "()Ljava/util/List;")
    private val setterCall: Method? = setterType?.declaredMethods?.firstOrNull { Modifier.isAbstract(it.modifiers) }?.apply { isAccessible = true }

    fun valuesOf(option: OptionInstance<*>): Any? = call(valuesMethod, option)

    fun sliderable(values: Any): Boolean = sliderableType?.isInstance(values) == true

    fun cycleable(values: Any): Boolean = cycleableType?.isInstance(values) == true

    fun sliderOrCycle(values: Any): Boolean = sliderOrCycleType?.isInstance(values) == true

    fun cycleButton(values: Any): Boolean? = call(createCycleButtonMethod, values) as? Boolean

    fun applyImmediately(values: Any): Boolean = call(applyImmediatelyMethod, values) as? Boolean ?: false

    @Suppress("UNCHECKED_CAST")
    fun selectedList(values: Any): List<Any>? = (call(listSupplierMethod, values) as? CycleButton.ValueListSupplier<Any>)?.selectedList

    fun set(values: Any, option: OptionInstance<Any>, value: Any) {
        val setter = call(valueSetterMethod, values)
        val call = setterCall
        if (setter == null || call == null || runCatching { call.invoke(setter, option, value) }.isFailure) option.set(value)
    }

    @Suppress("UNCHECKED_CAST")
    fun enumValues(values: Any): List<Any>? =
        if (sliderableEnumType?.isInstance(values) == true) call(enumValuesMethod, values) as? List<Any> else null

    fun intRange(values: Any): Pair<Int, Int>? {
        if (intRangeType?.isInstance(values) != true) return null
        val min = call(minInclusiveMethod, values) as? Int ?: return null
        val max = call(maxInclusiveMethod, values) as? Int ?: return null
        return min to max
    }

    fun toSlider(values: Any, value: Any): Double? = call(toSliderMethod, values, value) as? Double

    fun fromSlider(values: Any, slider: Double): Any? = call(fromSliderMethod, values, slider)

    private fun call(method: Method?, target: Any?, vararg args: Any?): Any? =
        if (method == null || target == null) null else runCatching { method.invoke(target, *args) }.getOrNull()

    private fun type(intermediary: String, named: String): Class<*>? =
        runCatching { Class.forName(named) }.getOrNull()
            ?: runCatching { Class.forName(FabricLoader.getInstance().mappingResolver.mapClassName("intermediary", intermediary)) }.getOrNull()

    private fun method(owner: Class<*>?, intermediaryOwner: String, intermediary: String, named: String, descriptor: String, vararg params: Class<*>): Method? {
        if (owner == null) return null
        val mapped = runCatching { FabricLoader.getInstance().mappingResolver.mapMethodName("intermediary", intermediaryOwner, intermediary, descriptor) }.getOrNull()
        return listOfNotNull(named, mapped).distinct().firstNotNullOfOrNull { name ->
            runCatching { owner.getDeclaredMethod(name, *params).apply { isAccessible = true } }.getOrNull()
        }
    }
}
*///?}
