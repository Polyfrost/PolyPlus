package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import org.jetbrains.skia.Image as SkiaImage
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.polyplus.client.emoji.EmojiRegistry
import kotlin.math.roundToInt

internal object EmojiAtlas {
    const val CELL = 48
    private const val COLUMNS = 32
    private const val PATH = "/assets/polyplus/textures/emoji/emoji_0.png"

    val bitmap: ImageBitmap? by lazy {
        runCatching {
            val bytes = EmojiAtlas::class.java.getResourceAsStream(PATH)!!.use { it.readBytes() }
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
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
    val glyphs = remember(segments) {
        segments.filterIsInstance<EmojiRegistry.Segment.Emoji>().map { it.glyph }.distinct()
    }
    if (glyphs.isEmpty() || EmojiAtlas.bitmap == null) {
        SocialText(text, fontSize, modifier, color, fontWeight, maxLines)
        return
    }

    val annotated = remember(segments) {
        buildAnnotatedString {
            segments.forEach { segment ->
                when (segment) {
                    is EmojiRegistry.Segment.Text -> append(segment.text)
                    is EmojiRegistry.Segment.Emoji -> appendInlineContent(segment.glyph, PLACEHOLDER)
                }
            }
        }
    }
    val glyphSize = fontSize * 1.25f
    val inlineContent = remember(glyphs, glyphSize) {
        glyphs.associateWith { glyph ->
            InlineTextContent(Placeholder(glyphSize, glyphSize, PlaceholderVerticalAlign.Center)) {
                EmojiGlyph(glyph, Modifier.fillMaxSize())
            }
        }
    }

    BasicText(
        text = annotated,
        modifier = modifier,
        maxLines = maxLines,
        softWrap = maxLines != 1,
        inlineContent = inlineContent,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = LocalTheme.current.typography.family,
            textAlign = TextAlign.Start,
        ),
    )
}

private const val PLACEHOLDER = "�"
