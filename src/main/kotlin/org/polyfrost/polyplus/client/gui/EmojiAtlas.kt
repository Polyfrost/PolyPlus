@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.emoji.EmojiRegistry
import kotlin.math.roundToInt

internal object EmojiAtlas {
    const val CELL = 48
    private const val COLUMNS = 32
    private const val PATH = "/assets/polyplus/textures/emoji/emoji_0.png"

    val bitmap: ImageBitmap? by lazy {
        runCatching {
            val bytes = EmojiAtlas::class.java.getResourceAsStream(PATH)!!.use { it.readBytes() }
            PolyBitmap.decode(bytes)!!.asComposeImageBitmap()
        }.getOrNull()
    }

    fun cellOf(glyph: String): IntOffset? {
        val image = bitmap ?: return null
        val index = EmojiRegistry.atlasIndex(glyph)
        if (index < 0) return null
        val x = (index % COLUMNS) * CELL
        val y = (index / COLUMNS) * CELL
        if (y + CELL > image.height || x + CELL > image.width) return null
        return IntOffset(x, y)
    }
}

@Composable
internal fun EmojiGlyph(glyph: String, modifier: Modifier) {
    val image = EmojiAtlas.bitmap ?: return
    val cell = EmojiAtlas.cellOf(glyph) ?: return
    Canvas(modifier) {
        drawImage(
            image = image,
            srcOffset = cell,
            srcSize = IntSize(EmojiAtlas.CELL, EmojiAtlas.CELL),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.High,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SocialEmojiText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = SocialTextPrimary,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
) {
    val segments = remember(text) {
        if (EmojiRegistry.enabled()) EmojiRegistry.segments(text) else listOf(EmojiRegistry.Segment.Text(text))
    }
    if (segments.none { it is EmojiRegistry.Segment.Emoji } || EmojiAtlas.bitmap == null) {
        SocialText(text, fontSize, modifier, color, fontWeight, maxLines = maxLines)
        return
    }

    val glyphSize = with(LocalDensity.current) { (fontSize * GLYPH_SCALE).toDp() }
    if (maxLines == 1) {
        Row(modifier.clipToBounds(), verticalAlignment = Alignment.CenterVertically) {
            segments.forEach { segment ->
                when (segment) {
                    is EmojiRegistry.Segment.Text -> SocialText(segment.text, fontSize, color = color, fontWeight = fontWeight, maxLines = 1)
                    is EmojiRegistry.Segment.Emoji -> EmojiGlyph(segment.glyph, Modifier.size(glyphSize))
                }
            }
        }
        return
    }

    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth
        val measurer = rememberTextMeasurer()
        val fontFamily = OneConfigTheme.current.fontFamily
        val pieces = remember(segments, widthPx, fontSize, fontWeight, fontFamily) {
            val style = TextStyle(fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily)
            segments.flatMap { segment ->
                when (segment) {
                    is EmojiRegistry.Segment.Text -> WORD.findAll(segment.text)
                        .flatMap { fitToWidth(it.value, measurer, style, widthPx) }
                        .map { EmojiRegistry.Segment.Text(it) }
                        .toList()
                    is EmojiRegistry.Segment.Emoji -> listOf(segment)
                }
            }
        }
        FlowRow(maxLines = maxLines) {
            pieces.forEach { piece ->
                when (piece) {
                    is EmojiRegistry.Segment.Text -> SocialText(piece.text, fontSize, Modifier.align(Alignment.CenterVertically), color, fontWeight, maxLines = 1)
                    is EmojiRegistry.Segment.Emoji -> EmojiGlyph(piece.glyph, Modifier.align(Alignment.CenterVertically).size(glyphSize))
                }
            }
        }
    }
}

private fun fitToWidth(word: String, measurer: TextMeasurer, style: TextStyle, maxWidth: Int): List<String> {
    if (maxWidth == Constraints.Infinity || word.isBlank()) return listOf(word)
    val layout = measurer.measure(word, style, constraints = Constraints(maxWidth = maxWidth))
    if (layout.lineCount <= 1) return listOf(word)
    return List(layout.lineCount) { word.substring(layout.getLineStart(it), layout.getLineEnd(it)) }
}

private const val GLYPH_SCALE = 1.25f
private val WORD = Regex("\\S+\\s*|\\s+")
