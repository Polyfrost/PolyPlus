@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.news

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.rememberRemoteImage

private const val BRAND_MARK = "assets/polyplus/brand/oneclient.svg"
private const val FALLBACK_TOP_ALPHA = 0.38f
private const val FALLBACK_BOTTOM_ALPHA = 0.12f
private const val MARK_ALPHA = 0.5f
private const val MARK_SCALE = 0.34f

@Composable
internal fun NewsCover(post: NewsPost, modifier: Modifier, height: Dp) {
    val image = rememberRemoteImage(post.image)
    val accent = OneConfigTheme.accent
    Box(
        modifier.background(Brush.verticalGradient(listOf(accent.copy(alpha = FALLBACK_TOP_ALPHA), accent.copy(alpha = FALLBACK_BOTTOM_ALPHA)))),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
        } else {
            ThemedIcon(BRAND_MARK, SocialTextPrimary.copy(alpha = MARK_ALPHA), height * MARK_SCALE)
        }
    }
}

@Composable
internal fun NewsKicker(text: String, unread: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (unread) Box(Modifier.size(7.dp).clip(OneConfigTheme.current.circleShape).background(OneConfigTheme.accent))
        SocialText(text, 11.sp, color = if (unread) SocialTextPrimary else MutedText, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp, maxLines = 1)
    }
}
