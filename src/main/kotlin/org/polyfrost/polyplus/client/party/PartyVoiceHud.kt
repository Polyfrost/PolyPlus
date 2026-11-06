package org.polyfrost.polyplus.client.party

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.compose.backend.PolyImageBitmap
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyColumn
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyRow
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.clip
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.render.FilterQuality
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.polyplus.client.gui.MenuHeadCache
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.lobbysocial.seedOf
import org.polyfrost.polyplus.client.network.http.responses.LocationKind
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform

private const val UPDATE_NANOS = 100_000_000L
private const val DEFAULT_PADDING = 4f
private const val ALIGN_LEFT = 0
private const val ALIGN_RIGHT = 2
private const val LAYOUT_HORIZONTAL = 1
private const val MEMBERS_SPEAKING = 1
private const val CASE_UPPER = 1
private const val CASE_LOWER = 2
private const val ENTRY_GAP = 3f
private const val HORIZONTAL_EXTRA_GAP = 4f
private const val FONT_SIZE = 8f
private const val HEAD_RADIUS_RATIO = 0.16f
private const val ICON_SIZE = 9f
private const val ICON_STROKE_RATIO = 0.09f
private const val ICON_VIEWBOX = 27f
private const val CURVE_STEPS = 6
private const val SAMPLE_MEMBER = "Party Member"

private val PLACEHOLDER = PolyColor(0x40FFFFFF)

private val SVG_PATH = Regex(" d=\"([^\"]+)\"")
private val SVG_TOKEN = Regex("[MLHVCZ]|-?[0-9]*\\.?[0-9]+")

private data class VoiceRow(
    val id: String,
    val name: String,
    val self: Boolean,
    val face: PolyBitmap?,
    val speaking: Boolean,
    val muted: Boolean,
    val deafened: Boolean,
)

private val micOff by lazy { iconStrokes(SOCIAL_ASSETS + "microphone-off-01.svg") }
private val headphonesOff by lazy { iconStrokes(SOCIAL_ASSETS + "headphones-off-02.svg") }

class PartyVoiceHud : Hud(id = "party_voice.json", title = "Party Voice", category = Category.PLAYER) {
    override val description: String
        get() = "Shows who is in your party voice, who is talking, and who is muted."

    override val searchTags: List<String>
        get() = listOf("voice", "party", "microphone", "mute", "deafen", "speaking")

    @RadioButton(title = "Layout", options = ["Vertical", "Horizontal"], subcategory = "Layout")
    var layoutMode = 0

    @Slider(title = "Spacing", min = 0F, max = 10F, step = 1F, subcategory = "Layout")
    var spacing = 2f

    @Slider(title = "Head Size", min = 6F, max = 32F, step = 1F, subcategory = "Layout")
    var headSize = 12f

    @RadioButton(title = "Members", options = ["Everyone", "Only Speaking"], subcategory = "Members")
    var membersMode = 0

    @Switch(title = "Show Yourself", subcategory = "Members")
    var showSelf = true

    @Switch(title = "Hide When Alone", description = "Hide this HUD when nobody else is in voice.", subcategory = "Members")
    var hideWhenAlone = false

    @Switch(title = "Show Heads", subcategory = "Display")
    var showHeads = true

    @Switch(title = "Show Names", subcategory = "Display")
    var showNames = true

    @Switch(title = "Show Muted Icon", subcategory = "Display")
    var showMutedIcon = true

    @Switch(title = "Show Deafened Icon", subcategory = "Display")
    var showDeafenedIcon = true

    @Color(title = "Speaking Ring Color", subcategory = "Display")
    var speakingColor = PolyColor(0xFF239A60.toInt())

    @Color(title = "Icon Color", subcategory = "Display")
    var iconColor = PolyColor(0xFFFF5A5A.toInt())

    @Include
    private var styleMigrated = false

    private var rows = mutableStateOf<List<VoiceRow>>(emptyList())

    private var rev = mutableStateOf(0)

    init {
        padLeft = DEFAULT_PADDING
        padRight = DEFAULT_PADDING
        padTop = DEFAULT_PADDING
        padBottom = DEFAULT_PADDING
        textAlign = ALIGN_LEFT
        alignment = PolyAlign.TopLeft
    }

    override fun setup() {
        super.setup()
        if (!isReal) return
        if (!styleMigrated) {
            styleMigrated = true
            showBackground = true
            padLeft = DEFAULT_PADDING
            padRight = DEFAULT_PADDING
            padTop = DEFAULT_PADDING
            padBottom = DEFAULT_PADDING
            textAlign = ALIGN_LEFT
            alignment = PolyAlign.TopLeft
        }
        for (option in OPTIONS) addCallback(option) { rev.value++ }
        update()
        reseedStaticSizeIfNeeded()
        captureStaticSizeDefaults()
    }

    override fun updateFrequency(): Long = UPDATE_NANOS

    override fun update(): Boolean {
        val live = visible(liveRows())
        val next = if (!isReal || (live.isEmpty() && HudManager.isEditing)) visible(sampleRows()) else live
        if (next == rows.value) return false
        rows.value = next
        return true
    }

    private fun visible(all: List<VoiceRow>): List<VoiceRow> {
        if (hideWhenAlone && all.none { !it.self }) return emptyList()
        return all.filter { (showSelf || !it.self) && (membersMode != MEMBERS_SPEAKING || it.speaking) }
    }

    override fun shouldShow(): Boolean = rows.value.isNotEmpty()

    override fun showByDefault(): Boolean = true

    override fun multipleInstancesAllowed(): Boolean = false

    override fun canMergeBackground(): Boolean = true

    override fun minimumSize(): Pair<Float, Float> = (padLeft + padRight + 2f) to (padTop + padBottom + 2f)

    override fun clone(): Hud = (super.clone() as PartyVoiceHud).also {
        it.rows = mutableStateOf(emptyList())
        it.rev = mutableStateOf(0)
    }

    @Composable
    override fun Content() {
        rev.value
        val list = rows.value
        if (list.isEmpty()) return
        val scale = textScale.coerceAtLeast(0.01f)
        val fixed = staticWidth && staticW > 0f && staticH > 0f
        val padded = if (fixed) hudBackground().size(staticW, staticH).clip() else hudBackground()
        val horizontal = layoutMode == LAYOUT_HORIZONTAL
        val content = if (fixed) PolyModifier.align(alignment) else PolyModifier
        PolyBox(modifier = padded.padding(padLeft, padTop, padRight, padBottom)) {
            if (horizontal) {
                PolyRow((spacing + HORIZONTAL_EXTRA_GAP) * scale, content) {
                    for (row in list) key(row.id) { Entry(row, scale, PolyAlign.Center) }
                }
            } else {
                val rowAlign = when (textAlign) {
                    ALIGN_LEFT -> PolyAlign.Left
                    ALIGN_RIGHT -> PolyAlign.Right
                    else -> PolyAlign.Center
                }
                PolyColumn(spacing * scale, content) {
                    for (row in list) key(row.id) { Entry(row, scale, rowAlign) }
                }
            }
        }
    }

    @Composable
    private fun Entry(row: VoiceRow, scale: Float, align: PolyAlign) {
        rev.value
        val centered = PolyModifier.align(PolyAlign.Center)
        val names = showNames || !showHeads
        PolyRow(ENTRY_GAP * scale, PolyModifier.align(align)) {
            if (showHeads) {
                val side = headSize * scale
                PolyCanvas(centered.size(side, side)) { x, y, width, _ -> drawHead(row, x, y, width, scale) }
            }
            if (names) Label(row.name, PolyColor(textColor, textChroma, textChromaSpeed), scale)
            if (showMutedIcon && row.muted) Icon(micOff, scale)
            if (showDeafenedIcon && row.deafened) Icon(headphonesOff, scale)
        }
    }

    @Composable
    private fun Label(text: String, color: PolyColor, scale: Float) {
        val modifier = PolyModifier.align(PolyAlign.Center)
        val cased = when (caseType) {
            CASE_UPPER -> text.uppercase()
            CASE_LOWER -> text.lowercase()
            else -> text
        }
        if (font == Font.Minecraft) {
            val style = buildString {
                if (textBold) append("§l")
                if (textItalic) append("§o")
                if (textUnderline) append("§n")
            }
            PolyMcText(
                text = if (style.isEmpty()) cased else "$style$cased§r",
                outlineColor = if (showShadow && fullShadow) PolyColor(shadowColor, shadowChroma, shadowChromaSpeed) else null,
                color = color,
                shadow = showShadow && !fullShadow,
                scale = scale,
                modifier = modifier,
            )
        } else {
            PolyText(
                cased,
                color = color,
                fontSize = FONT_SIZE * scale,
                shadow = showShadow,
                shadowColor = PolyColor(shadowColor, shadowChroma, shadowChromaSpeed),
                shadowOffset = shadowOffsetX,
                font = getPoppinsFontName(),
                modifier = modifier,
            )
        }
    }

    @Composable
    private fun Icon(strokes: List<FloatArray>, scale: Float) {
        val side = ICON_SIZE * scale
        PolyCanvas(PolyModifier.align(PolyAlign.Center).size(side, side)) { x, y, width, _ -> drawStrokes(strokes, x, y, width) }
    }

    private fun RenderContext.drawHead(row: VoiceRow, x: Float, y: Float, side: Float, scale: Float) {
        val inset = scale.coerceAtLeast(1f)
        val inner = side - inset * 2f
        val radius = side * HEAD_RADIUS_RATIO
        val face = row.face
        if (face != null) {
            save()
            clipRRect(x + inset, y + inset, inner, inner, radius)
            image(face, x + inset, y + inset, inner, inner, FilterQuality.None)
            restore()
        } else {
            rect(x + inset, y + inset, inner, inner, PLACEHOLDER, radius)
        }
        if (row.speaking) rectStroke(x, y, side, side, speakingColor, inset, radius)
    }

    private fun RenderContext.drawStrokes(strokes: List<FloatArray>, x: Float, y: Float, side: Float) {
        val scale = side / ICON_VIEWBOX
        val stroke = side * ICON_STROKE_RATIO
        for (points in strokes) {
            var i = 2
            while (i < points.size) {
                line(x + points[i - 2] * scale, y + points[i - 1] * scale, x + points[i] * scale, y + points[i + 1] * scale, iconColor, stroke)
                i += 2
            }
        }
    }

    private companion object {
        val OPTIONS = listOf(
            "layoutMode", "spacing", "headSize", "membersMode", "showSelf", "hideWhenAlone",
            "showHeads", "showNames", "showMutedIcon", "showDeafenedIcon", "speakingColor", "iconColor",
        )
    }
}

private fun sampleRows(): List<VoiceRow> {
    val face = faceOf(PartyRepository.localId())
    return listOf(
        VoiceRow("sample-self", ClientPlatform.localPlayerName(), true, face, speaking = true, muted = false, deafened = false),
        VoiceRow("sample-speaking", SAMPLE_MEMBER, false, face, speaking = true, muted = false, deafened = false),
        VoiceRow("sample-muted", SAMPLE_MEMBER, false, face, speaking = false, muted = true, deafened = false),
        VoiceRow("sample-deafened", SAMPLE_MEMBER, false, face, speaking = false, muted = true, deafened = true),
    )
}

private fun liveRows(): List<VoiceRow> {
    val ui = PartyVoice.ui.value
    val party = PartyRepository.party.value
    if (party == null || ui.transport == null || PartyPresence.local.location.kind == LocationKind.Menu) return emptyList()
    val self = PartyRepository.self(party)
    val selfId = self?.player ?: PartyRepository.localId()
    val own = VoiceRow(
        selfId,
        ClientPlatform.localPlayerName(),
        true,
        faceOf(selfId),
        ui.talking,
        ui.muted || ui.micBlocked || self?.voiceHardMuted == true,
        ui.deafened,
    )
    val members = PartyRepository.others(party).filter { it.online && it.state?.voice?.inVoice == true }.map { member ->
        val voice = member.state?.voice
        VoiceRow(
            member.player,
            PlayerNamesRepository.nameOr(member.player),
            false,
            faceOf(member.player),
            member.player in ui.speaking,
            voice?.muted == true || voice?.micBlocked == true || member.voiceHardMuted,
            voice?.deafened == true,
        )
    }
    return listOf(own) + members
}

private fun faceOf(player: String): PolyBitmap? = (MenuHeadCache.get(seedOf(player)) as? PolyImageBitmap)?.bitmap

private fun iconStrokes(resource: String): List<FloatArray> = runCatching {
    val svg = VoiceRow::class.java.classLoader.getResourceAsStream(resource)!!.use { it.readBytes().decodeToString() }
    SVG_PATH.findAll(svg).flatMap { parsePath(it.groupValues[1]) }.toList()
}.getOrDefault(emptyList())

private fun parsePath(path: String): List<FloatArray> {
    val tokens = SVG_TOKEN.findAll(path).map { it.value }.toList()
    val lines = ArrayList<FloatArray>()
    var points = ArrayList<Float>()
    var index = 0
    var command = 'M'
    var x = 0f
    var y = 0f
    var startX = 0f
    var startY = 0f
    fun next(): Float = tokens[index++].toFloat()
    fun flush() {
        if (points.size >= 4) lines += points.toFloatArray()
        points = ArrayList()
    }
    fun point() {
        points += x
        points += y
    }
    while (index < tokens.size) {
        if (tokens[index][0].isLetter()) command = tokens[index++][0]
        when (command) {
            'M' -> {
                flush()
                x = next()
                y = next()
                startX = x
                startY = y
                point()
            }
            'L' -> {
                x = next()
                y = next()
                point()
            }
            'H' -> {
                x = next()
                point()
            }
            'V' -> {
                y = next()
                point()
            }
            'C' -> {
                val fromX = x
                val fromY = y
                val x1 = next()
                val y1 = next()
                val x2 = next()
                val y2 = next()
                x = next()
                y = next()
                for (step in 1..CURVE_STEPS) {
                    val t = step / CURVE_STEPS.toFloat()
                    val u = 1f - t
                    points += u * u * u * fromX + 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t * x
                    points += u * u * u * fromY + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * y
                }
            }
            'Z' -> {
                x = startX
                y = startY
                point()
            }
        }
    }
    flush()
    return lines
}
