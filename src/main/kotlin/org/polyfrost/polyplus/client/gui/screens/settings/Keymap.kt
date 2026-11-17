@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ArrowDirection
import org.polyfrost.oneconfig.api.ui.v1.components.ArrowKey
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.KeyCap
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeaderDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.ThemeStyle
import java.util.Locale

//? if >= 26.3 {
private const val MenuKey = "application"
private const val DecimalKey = "keypad.period"
//?} else {
/*private const val MenuKey = "menu"
private const val DecimalKey = "keypad.decimal"
*///?}
private val MaxUnit = 46.dp
private val MinUnit = 26.dp
private val KeyGap = 4.dp
private val CasePadding = 14.dp
private val LegendPad = 4.dp
private const val MAX_WORD_SP = 12f
private const val MIN_WORD_SP = 7f
private const val BLOCK_GAP = 0.6f
private const val KEYBOARD_HEIGHT = 6.25f
private const val MOUSE_WIDTH = 3.4f
private const val MOUSE_GROW = 1.2f
private const val TOOLTIP_ROWS = 5
private const val KEYBOARD_PREFIX = "key.keyboard."
private const val MOUSE_PREFIX = "key.mouse."

internal enum class KeymapLayout(
    val title: String,
    val iso: Boolean,
    val numberRow: String,
    val topRow: String,
    val homeRow: String,
    val bottomRow: String,
) {
    UsQwerty("US QWERTY", false, "`1234567890-=", "QWERTYUIOP[]\\", "ASDFGHJKL;'", "ZXCVBNM,./"),
    UkQwerty("UK QWERTY", true, "`1234567890-=", "QWERTYUIOP[]", "ASDFGHJKL;'#", "\\ZXCVBNM,./"),
    Azerty("French AZERTY", true, "²&é\"'(-è_çà)=", "AZERTYUIOP^$", "QSDFGHJKLMù*", "<WXCVBN,;:!"),
    Qwertz("German QWERTZ", true, "^1234567890ß´", "QWERTZUIOPÜ+", "ASDFGHJKLÖÄ#", "<YXCVBNM,.-"),
    Dvorak("Dvorak", false, "`1234567890[]", "',.PYFGCRL/=\\", "AOEUIDHTNS-", ";QJKXBMWVZ"),
    Colemak("Colemak", false, "`1234567890-=", "QWFPGJLUY;[]\\", "ARSTDHNEIO'", "ZXCVBKM,./"),
}

private object KeymapStore {
    var layout by mutableStateOf<KeymapLayout?>(null)
    val detected: KeymapLayout by lazy { detectLayout() }
}

private fun detectLayout(): KeymapLayout {
    fun printed(position: String): String? = KeyBindsPlatform.keyLabel(KEYBOARD_PREFIX + position)?.trim()?.uppercase()
    when {
        printed("q") == "A" -> return KeymapLayout.Azerty
        printed("y") == "Z" -> return KeymapLayout.Qwertz
        printed("e") == "." -> return KeymapLayout.Dvorak
        printed("s") == "R" -> return KeymapLayout.Colemak
    }
    val locale = Locale.getDefault()
    return when {
        locale.language == "fr" && locale.country != "CA" -> KeymapLayout.Azerty
        locale.language == "de" || locale.country in setOf("DE", "AT", "CH") -> KeymapLayout.Qwertz
        locale.country in setOf("GB", "IE") -> KeymapLayout.UkQwerty
        else -> KeymapLayout.UsQwerty
    }
}

private class KeySpec(
    val name: String,
    val label: String,
    val x: Float,
    val y: Float,
    val w: Float = 1f,
    val h: Float = 1f,
    val arrow: ArrowDirection? = null,
    val printed: Boolean = false,
) {
    val id: String get() = KEYBOARD_PREFIX + name
}

private class KeyBlock(val width: Float, val keys: List<KeySpec>)

private class RowBuilder(private val y: Float, private val keys: MutableList<KeySpec>) {
    private var x = 0f

    fun key(name: String, label: String, w: Float = 1f, h: Float = 1f) {
        keys += KeySpec(name, label, x, y, w, h)
        x += w
    }

    fun printed(names: List<String>, labels: String) {
        names.forEachIndexed { i, name ->
            keys += KeySpec(name, labels.getOrNull(i)?.toString() ?: name, x, y, printed = true)
            x += 1f
        }
    }

    fun gap(w: Float) {
        x += w
    }
}

private val NumberRow = listOf("grave.accent", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "minus", "equal")
private val TopRow = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "left.bracket", "right.bracket")
private val HomeRow = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "semicolon", "apostrophe")
private val BottomRow = listOf("z", "x", "c", "v", "b", "n", "m", "comma", "period", "slash")

private fun block(width: Float, build: (MutableList<KeySpec>) -> Unit): KeyBlock {
    val keys = ArrayList<KeySpec>()
    build(keys)
    return KeyBlock(width, keys)
}

private fun mainBlock(layout: KeymapLayout): KeyBlock = block(15f) { keys ->
    RowBuilder(0f, keys).apply {
        key("escape", "Esc"); gap(1f)
        for (i in 1..4) key("f$i", "F$i"); gap(0.5f)
        for (i in 5..8) key("f$i", "F$i"); gap(0.5f)
        for (i in 9..12) key("f$i", "F$i")
    }
    RowBuilder(1.25f, keys).apply {
        printed(NumberRow, layout.numberRow)
        key("backspace", "Backspace", 2f)
    }
    if (layout.iso) {
        RowBuilder(2.25f, keys).apply {
            key("tab", "Tab", 1.5f)
            printed(TopRow, layout.topRow)
            gap(0.25f)
            key("enter", "Enter", 1.25f, 2f)
        }
        RowBuilder(3.25f, keys).apply {
            key("caps.lock", "Caps Lock", 1.75f)
            printed(HomeRow + "backslash", layout.homeRow)
        }
        RowBuilder(4.25f, keys).apply {
            key("left.shift", "Shift", 1.25f)
            printed(listOf("world.1") + BottomRow, layout.bottomRow)
            key("right.shift", "Shift", 2.75f)
        }
    } else {
        RowBuilder(2.25f, keys).apply {
            key("tab", "Tab", 1.5f)
            printed(TopRow, layout.topRow)
            keys += KeySpec("backslash", layout.topRow.getOrNull(12)?.toString() ?: "\\", 13.5f, 2.25f, 1.5f, printed = true)
        }
        RowBuilder(3.25f, keys).apply {
            key("caps.lock", "Caps Lock", 1.75f)
            printed(HomeRow, layout.homeRow)
            key("enter", "Enter", 2.25f)
        }
        RowBuilder(4.25f, keys).apply {
            key("left.shift", "Shift", 2.25f)
            printed(BottomRow, layout.bottomRow)
            key("right.shift", "Shift", 2.75f)
        }
    }
    RowBuilder(5.25f, keys).apply {
        key("left.control", "Ctrl", 1.25f); key("left.win", "Win", 1.25f); key("left.alt", "Alt", 1.25f)
        key("space", "", 6.25f)
        key("right.alt", "Alt", 1.25f); key("right.win", "Win", 1.25f); key(MenuKey, "Menu", 1.25f); key("right.control", "Ctrl", 1.25f)
    }
}

private val NavBlock = block(3f) { keys ->
    RowBuilder(0f, keys).apply { key("print.screen", "PrtSc"); key("scroll.lock", "ScrLk"); key("pause", "Pause") }
    RowBuilder(1.25f, keys).apply { key("insert", "Ins"); key("home", "Home"); key("page.up", "PgUp") }
    RowBuilder(2.25f, keys).apply { key("delete", "Del"); key("end", "End"); key("page.down", "PgDn") }
    keys += KeySpec("up", "Up", 1f, 4.25f, arrow = ArrowDirection.Up)
    keys += KeySpec("left", "Left", 0f, 5.25f, arrow = ArrowDirection.Left)
    keys += KeySpec("down", "Down", 1f, 5.25f, arrow = ArrowDirection.Down)
    keys += KeySpec("right", "Right", 2f, 5.25f, arrow = ArrowDirection.Right)
}

private val NumpadBlock = block(4f) { keys ->
    RowBuilder(1.25f, keys).apply {
        key("num.lock", "Num"); key("keypad.divide", "/"); key("keypad.multiply", "*"); key("keypad.subtract", "-")
    }
    RowBuilder(2.25f, keys).apply { key("keypad.7", "7"); key("keypad.8", "8"); key("keypad.9", "9") }
    RowBuilder(3.25f, keys).apply { key("keypad.4", "4"); key("keypad.5", "5"); key("keypad.6", "6") }
    RowBuilder(4.25f, keys).apply { key("keypad.1", "1"); key("keypad.2", "2"); key("keypad.3", "3") }
    keys += KeySpec("keypad.add", "+", 3f, 2.25f, h = 2f)
    keys += KeySpec("keypad.enter", "Enter", 3f, 4.25f, h = 2f)
    keys += KeySpec("keypad.0", "0", 0f, 5.25f, w = 2f)
    keys += KeySpec(DecimalKey, ".", 2f, 5.25f)
}

private fun blockSets(layout: KeymapLayout): List<List<KeyBlock>> {
    val main = mainBlock(layout)
    return listOf(listOf(main, NavBlock, NumpadBlock), listOf(main, NavBlock), listOf(main))
}

private fun List<KeyBlock>.totalWidth(): Float = sumOf { it.width.toDouble() }.toFloat() + BLOCK_GAP * (size - 1)

private enum class KeyState { Free, Bound, Conflict }

private class KeyTarget(val id: String, val label: String, val rows: List<KeyBindRow>) {
    val state: KeyState = when {
        rows.any { it.conflicts.isNotEmpty() } -> KeyState.Conflict
        rows.isNotEmpty() -> KeyState.Bound
        else -> KeyState.Free
    }
}

private class KeymapView(
    val index: Map<String, List<KeyBindRow>>,
    val selected: String?,
    val showFree: Boolean,
    val onSelect: (String?) -> Unit,
)

private val LocalKeymapView = staticCompositionLocalOf<KeymapView?> { null }

private class WordFit(start: Float) {
    var sp by mutableFloatStateOf(start)
}

private val LocalWordFit = staticCompositionLocalOf { WordFit(11f) }

internal fun keymapLabel(keyId: String): String {
    val name = keyId.removePrefix(KEYBOARD_PREFIX)
    return when (keyId) {
        MOUSE_PREFIX + "left" -> "Left click"
        MOUSE_PREFIX + "right" -> "Right click"
        MOUSE_PREFIX + "middle" -> "Middle click"
        MOUSE_PREFIX + "4" -> "Back button"
        MOUSE_PREFIX + "5" -> "Forward button"
        else -> KeyBindsPlatform.keyLabel(keyId)?.takeIf { it.isNotBlank() } ?: name
    }
}

@Composable
internal fun Keymap(
    rows: List<KeyBindRow>,
    selected: String?,
    onSelect: (String?) -> Unit,
    showFree: Boolean,
    onShowFree: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = remember(rows) { rows.filter { !it.unbound }.flatMap { row -> row.allIds.map { it to row } }.groupBy({ it.first }, { it.second }) }
    val auto = KeymapStore.layout == null
    val layout = KeymapStore.layout ?: KeymapStore.detected
    val view = KeymapView(index, selected, showFree, onSelect)

    fun target(spec: KeySpec): KeyTarget {
        val label = if (auto && spec.printed) {
            KeyBindsPlatform.keyLabel(spec.id)?.takeIf { it.codePointCount(0, it.length) == 1 } ?: spec.label
        } else {
            spec.label
        }
        return KeyTarget(spec.id, label, index[spec.id].orEmpty())
    }

    val mouseTargets = listOf("left", "right", "middle", "4", "5").map { name ->
        val id = MOUSE_PREFIX + name
        KeyTarget(id, keymapLabel(id), index[id].orEmpty())
    }

    val wordFit = remember { WordFit(MAX_WORD_SP) }
    CompositionLocalProvider(LocalKeymapView provides view, LocalWordFit provides wordFit) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KeymapHeader(showFree, onShowFree)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val available = maxWidth - CasePadding * 4
                val sets = blockSets(layout)
                val unitFor = { blocks: List<KeyBlock> -> available / (blocks.totalWidth() + MOUSE_WIDTH + BLOCK_GAP) }
                val chosen = sets.firstOrNull { unitFor(it) >= MinUnit } ?: sets.last()
                val unit = min(MaxUnit, unitFor(chosen))
                val gap = unit * BLOCK_GAP
                val spare = (available - unit * (chosen.totalWidth() + MOUSE_WIDTH) - gap).coerceAtLeast(0.dp)
                val mouseWidth = unit * MOUSE_WIDTH + min(spare, unit * MOUSE_GROW)
                LaunchedEffect(unit) { wordFit.sp = MAX_WORD_SP }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    Box(
                        Modifier
                            .themedSurface(SurfaceKind.Card)
                            .padding(CasePadding)
                            .size(unit * chosen.totalWidth(), unit * KEYBOARD_HEIGHT - KeyGap),
                    ) {
                        var left = 0f
                        for (block in chosen) {
                            for (spec in block.keys) PlacedKey(spec, target(spec), unit, left)
                            left += block.width + BLOCK_GAP
                        }
                    }
                    Box(
                        Modifier
                            .themedSurface(SurfaceKind.Card)
                            .padding(CasePadding)
                            .size(mouseWidth, unit * KEYBOARD_HEIGHT - KeyGap),
                        contentAlignment = Alignment.Center,
                    ) {
                        MouseBody(mouseTargets, unit)
                    }
                }
            }
            KeymapFooter(showFree)
        }
    }
}

@Composable
private fun KeymapHeader(showFree: Boolean, onShowFree: (Boolean) -> Unit) {
    val auto = "Auto-detect (${KeymapStore.detected.title})"
    SectionHeader(
        "Keyboard and mouse",
        icon = "keyboard",
        contentPadding = PaddingValues(),
        textStyle = SectionHeaderDefaults.labelStyle,
        color = OneConfigTheme.current.textColorSecondary,
        uppercase = false,
        iconSize = 18.dp,
    ) {
        Chip("Show free keys", showFree, { onShowFree(!showFree) }, size = ControlSize.Compact)
        Select(
            selected = KeymapStore.layout,
            options = listOf<KeymapLayout?>(null) + KeymapLayout.entries,
            onSelect = { KeymapStore.layout = it },
            modifier = Modifier.width(220.dp),
            label = { it?.title ?: auto },
            size = ControlSize.Compact,
        )
    }
}

@Composable
private fun freeColor(): Color = OneConfigTheme.current.success

@Composable
private fun KeymapFooter(showFree: Boolean) {
    val theme = OneConfigTheme.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(OneConfigTheme.accent, "In use")
        LegendItem(theme.error, "Clashing")
        LegendItem(if (showFree) freeColor() else lerp(theme.componentBackground, theme.textColor, 0.2f), "Free")
        Spacer(Modifier.weight(1f))
        Text(
            "Hover a key to see what it does. Click it to filter the list.",
            color = theme.textColorSecondary,
            style = theme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    val theme = OneConfigTheme.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(color, theme.shapes.circle))
        Text(label, color = theme.textColorSecondary, style = theme.typography.caption, maxLines = 1)
    }
}

@Composable
private fun stateColor(state: KeyState, showFree: Boolean): Color? = when (state) {
    KeyState.Bound -> OneConfigTheme.accent
    KeyState.Conflict -> OneConfigTheme.current.error
    KeyState.Free -> if (showFree) freeColor() else null
}

@Composable
private fun hoverTint(base: Color?, hovered: Boolean): Color? {
    if (!hovered) return base
    val text = OneConfigTheme.current.textColor
    return if (base == null) text else lerp(base, text, 0.3f)
}

private fun dimmed(state: KeyState, view: KeymapView): Boolean = view.showFree && state != KeyState.Free

@Composable
private fun PlacedKey(spec: KeySpec, target: KeyTarget, unit: Dp, originX: Float) {
    val view = LocalKeymapView.current ?: return
    val state = target.state
    val selected = view.selected == target.id
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val tint = hoverTint(stateColor(state, view.showFree), hovered || selected)
    val width = unit * spec.w - KeyGap
    val height = unit * spec.h - KeyGap
    Box(Modifier.offset(unit * (originX + spec.x), unit * spec.y)) {
        if (selected) SelectionRing(width, height)
        KeyTooltip(target) {
            val click = Modifier
                .alpha(if (dimmed(state, view) && !hovered && !selected) 0.4f else 1f)
                .oneConfigClickable(
                    target.label.ifEmpty { "Space" },
                    { view.onSelect(if (selected) null else target.id) },
                    indication = false,
                    interactionSource = source,
                )
            if (spec.arrow != null) {
                ArrowKey(spec.arrow, click, size = width, tint = tint)
            } else {
                KeyCap(click, width = width, height = height, tint = tint) {
                    KeyLegend(target.label, state)
                }
            }
        }
    }
}

@Composable
private fun SelectionRing(width: Dp, height: Dp) {
    Box(
        Modifier
            .offset((-3).dp, (-3).dp)
            .size(width + 6.dp, height + 6.dp)
            .border(1.5.dp, OneConfigTheme.accent, OneConfigTheme.current.shapes.medium),
    )
}

@Composable
private fun KeyLegend(label: String, state: KeyState) {
    val theme = OneConfigTheme.current
    val word = label.length > 1
    val base = theme.typography.captionMedium
    val fit = LocalWordFit.current
    Box(Modifier.fillMaxSize().padding(horizontal = LegendPad), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = if (state == KeyState.Free) theme.textColor.copy(alpha = 0.7f) else theme.textColor,
            style = if (word) base.copy(fontSize = fit.sp.sp) else base,
            maxLines = 1,
            softWrap = false,
            onTextLayout = if (word) {
                { result -> if (result.didOverflowWidth && fit.sp > MIN_WORD_SP) fit.sp -= 0.5f }
            } else {
                null
            },
        )
    }
}

private fun stateText(target: KeyTarget): String = when (target.state) {
    KeyState.Free -> "Free. Nothing uses this key yet."
    KeyState.Conflict -> "Some of these clash"
    KeyState.Bound -> if (target.rows.size == 1) "Used by 1 key bind" else "Used by ${target.rows.size} key binds"
}

@Composable
private fun KeyTooltip(target: KeyTarget, content: @Composable () -> Unit) {
    TooltipBox(tooltip = { KeyTooltipCard(target) }, maxWidth = 360.dp, content = content)
}

@Composable
private fun KeyTooltipCard(target: KeyTarget) {
    val theme = OneConfigTheme.current
    val view = LocalKeymapView.current
    val state = target.state
    val selected = view?.selected == target.id
    Column(Modifier.widthIn(min = 200.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeyCap(target.label.ifEmpty { "Space" }, tint = stateColor(state, view?.showFree == true))
            Text(stateText(target), color = if (state == KeyState.Conflict) theme.error else theme.textColor, style = theme.typography.smallMedium)
        }
        if (target.rows.isNotEmpty()) {
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(theme.borderColor.copy(alpha = 0.6f)))
            target.rows.take(TOOLTIP_ROWS).forEach { row ->
                val conflict = row.conflicts.isNotEmpty()
                Column {
                    Text(row.name, color = theme.textColor, style = theme.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    Text(
                        if (conflict) "${row.category}, clashes" else row.category,
                        color = if (conflict) theme.error else theme.textColorSecondary,
                        style = theme.typography.caption,
                        maxLines = 1,
                    )
                }
            }
            if (target.rows.size > TOOLTIP_ROWS) {
                Text("and ${target.rows.size - TOOLTIP_ROWS} more", color = theme.textColorSecondary, style = theme.typography.caption)
            }
        }
        Text(
            when {
                selected -> "Click to show every key bind again"
                target.rows.isEmpty() -> "Click to filter the list to this key"
                else -> "Click to show only these key binds"
            },
            color = theme.textColorSecondary,
            style = theme.typography.caption,
        )
    }
}

@Composable
private fun MouseBody(targets: List<KeyTarget>, unit: Dp) {
    val theme = OneConfigTheme.current
    val pixel = theme.style == ThemeStyle.Pixel
    val bodyW = unit * 2.4f
    val bodyH = unit * 4.2f
    val face = lerp(theme.componentBackground, theme.textColor, 0.06f).copy(alpha = 1f)
    val rim = lerp(theme.componentBackground, theme.textColor, 0.2f).copy(alpha = 1f)
    val (left, right, middle, back, forward) = targets
    val split = bodyH * 0.42f
    val corner = if (pixel) 0.dp else bodyW / 2
    val groove = 1.5.dp
    val sideW = unit * 0.26f
    val sideH = unit * 0.62f
    val leftShape = remember(corner) { RoundedCornerShape(topStart = corner) }
    val rightShape = remember(corner) { RoundedCornerShape(topEnd = corner) }
    Box(Modifier.size(bodyW + sideW, bodyH)) {
        Box(Modifier.offset(sideW / 2, 0.dp).size(bodyW, bodyH)) {
            Canvas(Modifier.size(bodyW, bodyH)) {
                val r = if (pixel) 0f else size.width / 2f
                drawRoundRect(rim, Offset.Zero, size, CornerRadius(r))
                val inset = 1.dp.toPx()
                drawRoundRect(
                    face,
                    Offset(inset, inset),
                    Size(size.width - inset * 2, size.height - inset * 3),
                    CornerRadius((r - inset).coerceAtLeast(0f)),
                )
            }
            MouseButton(left, Offset.Zero, bodyW / 2 - groove / 2, split, leftShape)
            MouseButton(right, Offset((bodyW / 2 + groove / 2).value, 0f), bodyW / 2 - groove / 2, split, rightShape)
            MouseButton(middle, Offset((bodyW / 2 - unit * 0.15f).value, (split * 0.2f).value), unit * 0.3f, split * 0.42f, theme.shapes.pill)
        }
        MouseButton(forward, Offset(0f, (bodyH * 0.5f).value), sideW, sideH, theme.shapes.pill)
        MouseButton(back, Offset(0f, (bodyH * 0.5f + sideH + 3.dp).value), sideW, sideH, theme.shapes.pill)
    }
}

@Composable
private fun MouseButton(target: KeyTarget, at: Offset, width: Dp, height: Dp, shape: Shape) {
    val view = LocalKeymapView.current ?: return
    val theme = OneConfigTheme.current
    val state = target.state
    val selected = view.selected == target.id
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val tint = hoverTint(stateColor(state, view.showFree), hovered || selected)
    val baseFace = lerp(theme.componentBackground, theme.textColor, 0.06f).copy(alpha = 1f)
    val baseRim = lerp(theme.componentBackground, theme.textColor, 0.2f).copy(alpha = 1f)
    val face = tint?.let { lerp(baseFace, it, 0.16f) } ?: baseFace
    val rim = tint?.let { lerp(baseRim, it, 0.55f) } ?: baseRim
    Box(Modifier.offset(at.x.dp, at.y.dp)) {
        KeyTooltip(target) {
            Box(
                Modifier
                    .size(width, height)
                    .alpha(if (dimmed(state, view) && !hovered && !selected) 0.4f else 1f)
                    .background(face, shape)
                    .border(if (selected) 1.5.dp else 1.dp, if (selected) OneConfigTheme.accent else rim, shape)
                    .oneConfigClickable(
                        target.label,
                        { view.onSelect(if (selected) null else target.id) },
                        shape = shape,
                        indication = false,
                        interactionSource = source,
                    ),
            )
        }
    }
}
