@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.pets.PetAssets

internal sealed class TryAction(val key: String, val label: String) {
    class Hold(val signal: Int, label: String) : TryAction("hold:$signal", label)
    class Pulse(val signal: Int, label: String) : TryAction("pulse:$signal", label)
    object Jump : TryAction("jump", "Jump")
    object WearAgain : TryAction("equip", "Wear again")
    class Effect(val name: String, label: String) : TryAction("effect:$name", label)
}

internal class TryInfo(val actions: List<TryAction>, val lines: List<String>, val targets: Set<Int> = emptySet()) {
    val isEmpty: Boolean get() = actions.isEmpty() && lines.isEmpty()

    fun withoutEffects(): TryInfo {
        val kept = actions.filter { it !is TryAction.Effect }
        return if (kept.size == actions.size) this else TryInfo(kept, lines, targets)
    }

    companion object {
        val EMPTY = TryInfo(emptyList(), emptyList())
    }
}

@Composable
internal fun rememberTryOn(key: Any?): PreviewTryOn {
    val tryOn = remember(key) { PreviewTryOn() }
    DisposableEffect(tryOn) {
        onDispose { tryOn.reset() }
    }
    return tryOn
}

@Composable
internal fun rememberTryInfo(ids: List<Int>?): TryInfo {
    val installs = CosmeticAssetCache.installs
    return remember(ids, installs) { ids?.let(::tryInfo) ?: TryInfo.EMPTY }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TryOnBar(info: TryInfo, tryOn: PreviewTryOn, modifier: Modifier = Modifier) {
    if (info.actions.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth()
            .themedSurface(SurfaceKind.Field, OneConfigTheme.shape(12.dp), glass = false)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Try it", color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
        val scroll = rememberScrollState()
        ScrollContainer(scroll, Modifier.fillMaxWidth().heightIn(max = MAX_ACTIONS_HEIGHT), scrollbar = false) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (action in info.actions) {
                    val held = action is TryAction.Hold && tryOn.isHeld(action.signal)
                    Chip(
                        label = action.label,
                        selected = held,
                        onClick = { run(action, info, tryOn) },
                        size = ControlSize.Compact,
                        sound = null,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TryOnLines(info: TryInfo) {
    if (info.lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (line in info.lines) {
            Text(line, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
        }
    }
}

private fun run(action: TryAction, info: TryInfo, tryOn: PreviewTryOn) {
    when (action) {
        is TryAction.Hold -> tryOn.toggle(action.signal)
        is TryAction.Pulse -> tryOn.pulse(action.signal)
        TryAction.Jump -> tryOn.jump()
        TryAction.WearAgain -> tryOn.wearAgain(info.targets)
        is TryAction.Effect -> tryOn.fire(action.name)
    }
}

internal fun tryInfo(ids: List<Int>): TryInfo {
    val actions = LinkedHashMap<String, TryAction>()
    val lines = LinkedHashSet<String>()
    for (id in ids) {
        val meta = metaOf(id)
            ?: metaOf(CosmeticCatalog.resolveVariantForSkin(id, true))
            ?: metaOf(CosmeticCatalog.resolveVariantForSkin(id, false))
            ?: continue
        read(meta, actions, lines)
    }
    if (actions.isEmpty() && lines.isEmpty()) return TryInfo.EMPTY
    val targets = ids.flatMapTo(HashSet()) { id ->
        listOf(id, CosmeticCatalog.resolveVariantForSkin(id, true), CosmeticCatalog.resolveVariantForSkin(id, false))
    }
    return TryInfo(actions.values.toList(), lines.toList(), targets)
}

private fun metaOf(id: Int): JsonObject? {
    val json = runCatching {
        CosmeticAssetCache.getAttachedCosmetic(id)?.scene?.asset?.metaJson()
            ?: PetAssets.get(id)?.cosmetic?.metaJson()
    }.getOrNull() ?: return null
    return runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
}

private fun read(meta: JsonObject, actions: MutableMap<String, TryAction>, lines: MutableSet<String>) {
    for (reaction in objects(meta.array("reactions"))) {
        val signal = reaction.string("signal")?.let { PlayerSignals.SIGNAL_NAMES.indexOf(it) } ?: -1
        val on = reaction.string("on")
        if (signal < 0) {
            if (on == "equip") {
                add(actions, TryAction.WearAgain)
                lines += "Plays when you put it on"
            } else {
                lines += "Plays when it comes into view"
            }
            continue
        }
        add(actions, actionFor(signal, on == "while"))
        val phrase = phrase(signal, on) ?: continue
        lines += when {
            reaction.string("effect") != null -> "Plays an effect $phrase"
            reaction.string("colorway") != null -> "Changes color $phrase"
            else -> "Reacts $phrase"
        }
    }
    val springs = (meta.get("spring_bones")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0) > 0
    val controlled = (meta.array("controllers")?.size() ?: 0) > 0
    val pet = meta.get("pet")?.isJsonObject == true
    if (springs) lines += "Swings as you move"
    if (pet) {
        add(actions, TryAction.Pulse(PlayerSignals.IS_HURT, "Get hurt"))
        for (signal in PET_SIGNALS) add(actions, actionFor(signal, true))
    } else if (springs || controlled) {
        add(actions, TryAction.Jump)
        for (signal in GENERAL_SIGNALS) add(actions, actionFor(signal, true))
        add(actions, TryAction.Pulse(PlayerSignals.IS_HURT, "Get hurt"))
    }
    val effects = meta.get("effects")?.takeIf { it.isJsonObject }?.asJsonObject
    for (name in strings(effects?.array("named"))) {
        add(actions, TryAction.Effect(name, effectLabel(name)))
        momentLine(name)?.let(lines::add)
    }
}

private fun add(actions: MutableMap<String, TryAction>, action: TryAction) {
    if (action is TryAction.Pulse && actions.containsKey("hold:${action.signal}")) return
    if (action is TryAction.Hold) actions.remove("pulse:${action.signal}")
    actions.putIfAbsent(action.key, action)
}

private fun actionFor(signal: Int, hold: Boolean): TryAction = when {
    hold && signal == PlayerSignals.IS_ON_GROUND -> TryAction.Hold(signal, "In the air")
    hold && signal == PlayerSignals.IS_HURT -> TryAction.Hold(signal, "Hurt")
    signal == PlayerSignals.IS_ON_GROUND -> TryAction.Jump
    signal == PlayerSignals.IS_HURT -> TryAction.Pulse(signal, "Get hurt")
    hold -> TryAction.Hold(signal, LABELS[signal] ?: signalWords(signal))
    else -> TryAction.Pulse(signal, LABELS[signal] ?: signalWords(signal))
}

private fun signalWords(signal: Int): String =
    PlayerSignals.SIGNAL_NAMES.getOrNull(signal)?.removePrefix("is_")?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Try"

private fun phrase(signal: Int, on: String?): String? {
    if (signal == PlayerSignals.IS_ON_GROUND) {
        return when (on) {
            "start" -> "when you land"
            "stop" -> "when you jump"
            else -> null
        }
    }
    return PHRASES[signal]
}

private fun effectLabel(name: String): String = when (name) {
    "join" -> "Join"
    "respawn" -> "Respawn"
    "death" -> "Death"
    "kill" -> "Kill"
    else -> name.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }
}

private fun momentLine(name: String): String? = when (name) {
    "join" -> "Plays an effect when you join a server"
    "respawn" -> "Plays an effect when you respawn"
    "death" -> "Plays an effect where you die"
    "kill" -> KILL_LINE
    else -> null
}

private const val KILL_LINE = "Plays an effect when you get a kill, only on servers that support it"

private fun JsonObject.array(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.string(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

private fun objects(array: JsonArray?): List<JsonObject> =
    array?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.orEmpty()

private fun strings(array: JsonArray?): List<String> =
    array?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }.orEmpty()

private val MAX_ACTIONS_HEIGHT = 96.dp

private val GENERAL_SIGNALS = intArrayOf(PlayerSignals.IS_MOVING, PlayerSignals.IS_SPRINTING, PlayerSignals.IS_SNEAKING)

private val PET_SIGNALS = intArrayOf(
    PlayerSignals.IS_IN_COMBAT,
    PlayerSignals.IS_IN_RAIN,
    PlayerSignals.IS_SLEEPING,
    PlayerSignals.IS_EMOTING,
    PlayerSignals.IS_GLIDING,
)

private val LABELS: Map<Int, String> = mapOf(
    PlayerSignals.IS_SNEAKING to "Sneak",
    PlayerSignals.IS_SPRINTING to "Sprint",
    PlayerSignals.IS_SWIMMING to "Swim",
    PlayerSignals.IS_IN_WATER to "In water",
    PlayerSignals.IS_IN_RAIN to "Rain",
    PlayerSignals.IS_IN_SNOW to "Snow",
    PlayerSignals.IS_ON_FIRE to "On fire",
    PlayerSignals.IS_GLIDING to "Glide",
    PlayerSignals.IS_FLYING to "Fly",
    PlayerSignals.IS_FALLING to "Fall",
    PlayerSignals.IS_RIDING to "Ride",
    PlayerSignals.IS_SLEEPING to "Sleep",
    PlayerSignals.IS_EMOTING to "Emote",
    PlayerSignals.IS_IN_COMBAT to "Fight",
    PlayerSignals.IS_MOVING to "Walk",
    PlayerSignals.IS_IN_OCEAN to "Ocean",
)

private val PHRASES: Map<Int, String> = mapOf(
    PlayerSignals.IS_SNEAKING to "when you sneak",
    PlayerSignals.IS_SPRINTING to "when you sprint",
    PlayerSignals.IS_SWIMMING to "when you swim",
    PlayerSignals.IS_IN_WATER to "in water",
    PlayerSignals.IS_IN_RAIN to "in the rain",
    PlayerSignals.IS_IN_SNOW to "in the snow",
    PlayerSignals.IS_ON_FIRE to "when you're on fire",
    PlayerSignals.IS_HURT to "when you get hurt",
    PlayerSignals.IS_GLIDING to "when you glide",
    PlayerSignals.IS_FLYING to "when you fly",
    PlayerSignals.IS_FALLING to "when you fall",
    PlayerSignals.IS_RIDING to "when you ride",
    PlayerSignals.IS_SLEEPING to "when you sleep",
    PlayerSignals.IS_EMOTING to "when you emote",
    PlayerSignals.IS_IN_COMBAT to "in a fight",
    PlayerSignals.IS_MOVING to "when you move",
    PlayerSignals.IS_IN_OCEAN to "in the ocean",
)
