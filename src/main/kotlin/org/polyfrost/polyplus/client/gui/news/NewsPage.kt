@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.news

import androidx.compose.foundation.lazy.rememberLazyListState
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.PanelHeader
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.PageGrid
import org.polyfrost.polyplus.client.gui.lobbyshell.PageScrim
import org.polyfrost.polyplus.client.gui.lobbyshell.pageTitleStyle
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.relativeDayText
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.Text

private val StatusHeight = 220.dp
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
    PanelHeader(
        "News",
        Modifier.height(54.dp).panel(16.dp).padding(horizontal = 12.dp),
        titleStyle = pageTitleStyle(),
        leading = { BackButton(onBack) },
        actions = { TextButton(text = "Open blog", icon = LINK_ICON, onClick = { NewsFeed.openBlog() }, style = ControlStyle.Secondary, size = ControlSize.Large) },
        spacing = 8.dp,
    )
}

@Composable
private fun NewsStatus(modifier: Modifier) {
    when {
        !NewsFeed.enabled -> EmptyState("News is off", modifier, "Turn on online features to see posts from the Polyfrost blog.")
        NewsFeed.loading || !NewsFeed.loaded -> EmptyState("Loading news", modifier)
        NewsFeed.failed -> EmptyState("Couldn't load news", modifier, "Check your connection and try again.") {
            TextButton(text = "Try again", onClick = { NewsFeed.refresh(force = true) }, style = ControlStyle.Primary, size = ControlSize.Large)
        }
        else -> EmptyState("No news yet", modifier, "New posts from the Polyfrost blog show up here.")
    }
}

@Composable
private fun FeaturePanel(post: NewsPost, modifier: Modifier) {
    Column(modifier.panel()) {
        NewsCover(post, Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, top = 12.dp, end = 12.dp).clip(OneConfigTheme.shape(14.dp)), FeatureCoverHeight)
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NewsKicker(listOfNotNull(if (post === NewsFeed.posts.firstOrNull()) "LATEST" else null, relativeDayText(post.published)?.uppercase()).joinToString("  ·  "), false)
            Text(post.title, style = OneConfigTheme.current.typography.body.copy(fontSize = 28.sp, fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (post.summary.isNotEmpty()) Text(post.summary, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.heading.copy(fontWeight = FontWeight.Normal), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Box(Modifier.padding(top = 6.dp)) {
                TextButton(text = "Read on the blog", icon = LINK_ICON, onClick = { NewsFeed.open(post) }, style = ControlStyle.Primary, size = ControlSize.Large)
            }
        }
    }
}

@Composable
private fun PostList(posts: List<NewsPost>, selected: NewsPost?, onSelect: (NewsPost) -> Unit, modifier: Modifier) {
    Column(modifier.panel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelTitle("All posts", Modifier.padding(start = 4.dp, top = 4.dp))
        val list = rememberLazyListState()
        ScrollContainer(list, Modifier.fillMaxWidth().weight(1f), scrollbar = false) {
            LazyColumn(Modifier.fillMaxSize(), list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(posts, key = { it.link }) { post ->
                    PostRow(post, post === selected) { onSelect(post) }
                }
            }
        }
    }
}

@Composable
private fun PostRow(post: NewsPost, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectableRow(selected, onClick, post.title, OneConfigTheme.shape(12.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NewsCover(post, Modifier.size(ThumbWidth, ThumbHeight).clip(OneConfigTheme.shape(8.dp)), ThumbHeight)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(post.title, color = SelectionDefaults.itemContentColor(selected), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
            relativeDayText(post.published)?.let { Text(it, color = SelectionDefaults.itemSecondaryColor(selected, rest = OneConfigTheme.current.mutedText), style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
        }
    }
}
