@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.news

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.a11y.a11y
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.rightColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.rowFrame
import org.polyfrost.polyplus.client.gui.lobbyshell.rowInk
import org.polyfrost.polyplus.client.gui.lobbyshell.rowMuted
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.playButtonClickSound
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.PageGrid
import org.polyfrost.polyplus.client.gui.lobbyshell.PageScrim
import org.polyfrost.polyplus.client.gui.lobbyshell.PageTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.relativeDayText
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.themedIcon

private val StatusHeight = 220.dp
private val ButtonHeight = 38.dp
private val ThumbWidth = 72.dp
private val ThumbHeight = 44.dp
private val FeatureCoverHeight = 320.dp
private const val LINK_ICON = SOCIAL_ASSETS + "link-external-01.svg"

@Composable
internal fun BoxScope.NewsPage(onBack: () -> Unit) {
    LaunchedEffect(Unit) { NewsFeed.refresh() }
    LaunchedEffect(NewsFeed.posts) { NewsFeed.markSeen() }
    var selectedLink by remember { mutableStateOf<String?>(null) }
    val posts = if (NewsFeed.enabled) NewsFeed.posts else emptyList()
    val selected = posts.firstOrNull { it.link == selectedLink } ?: posts.firstOrNull()
    PageScrim()
    Column(
        Modifier
            .padding(start = LobbyLayout.Margin, end = LobbyLayout.BarEnd, top = LobbyLayout.Top, bottom = LobbyLayout.PageBottom)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PageGrid.Gap),
    ) {
        NewsHeader(onBack)
        if (selected != null) {
            FeaturePanel(selected, Modifier.fillMaxWidth().weight(1f))
        } else {
            NewsStatus(Modifier.fillMaxWidth().height(StatusHeight).panel())
        }
    }
    if (posts.isNotEmpty()) PostList(posts, selected, { selectedLink = it.link }, rightColumn())
}

@Composable
private fun NewsHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).panel(16.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackButton(onBack)
        PageTitle("News")
        Spacer(Modifier.weight(1f))
        TextButton("Open blog", themedIcon(LINK_ICON), Modifier.height(ButtonHeight), ButtonStyle.Secondary, true) { NewsFeed.openBlog() }
    }
}

@Composable
private fun NewsStatus(modifier: Modifier) {
    when {
        !NewsFeed.enabled -> EmptyState("News is off", modifier, "Turn on online features to see posts from the Polyfrost blog.")
        NewsFeed.loading || !NewsFeed.loaded -> EmptyState("Loading news", modifier)
        NewsFeed.failed -> EmptyState("Couldn't load news", modifier, "Check your connection and try again.") {
            TextButton("Try again", null, Modifier.height(ButtonHeight), ButtonStyle.Primary, true) { NewsFeed.refresh(force = true) }
        }
        else -> EmptyState("No news yet", modifier, "New posts from the Polyfrost blog show up here.")
    }
}

@Composable
private fun FeaturePanel(post: NewsPost, modifier: Modifier) {
    Column(modifier.panel()) {
        NewsCover(post, Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, top = 12.dp, end = 12.dp).clip(ppShape(14.dp)), FeatureCoverHeight)
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NewsKicker(listOfNotNull(if (post === NewsFeed.posts.firstOrNull()) "LATEST" else null, relativeDayText(post.published)?.uppercase()).joinToString("  ·  "), false)
            SocialText(post.title, 28.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (post.summary.isNotEmpty()) SocialText(post.summary, 15.sp, color = MutedText, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Box(Modifier.padding(top = 6.dp)) {
                TextButton("Read on the blog", themedIcon(LINK_ICON), Modifier.height(ButtonHeight), ButtonStyle.Primary, true) { NewsFeed.open(post) }
            }
        }
    }
}

@Composable
private fun PostList(posts: List<NewsPost>, selected: NewsPost?, onSelect: (NewsPost) -> Unit, modifier: Modifier) {
    Column(modifier.panel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelTitle("All posts", Modifier.padding(start = 4.dp, top = 4.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(posts, key = { it.link }) { post ->
                PostRow(post, post === selected) { onSelect(post) }
            }
        }
    }
}

@Composable
private fun PostRow(post: NewsPost, selected: Boolean, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    Row(
        Modifier
            .fillMaxWidth()
            .rowFrame(selected, hovered)
            .hoverable(interaction)
            .a11y(post.title, A11yRole.Option, selected, null, ppShape(12.dp), true)
            .clickable(interaction, null, role = Role.Button) {
                playButtonClickSound()
                onClick()
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NewsCover(post, Modifier.size(ThumbWidth, ThumbHeight).clip(ppShape(8.dp)), ThumbHeight)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SocialText(post.title, 14.sp, color = rowInk(selected), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            relativeDayText(post.published)?.let { Label(it, 12.sp, color = rowMuted(selected)) }
        }
    }
}
