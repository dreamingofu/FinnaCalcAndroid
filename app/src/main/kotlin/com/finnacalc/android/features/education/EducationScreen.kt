//
// EducationScreen.kt
//
// Port of iOS Features/Education/EducationView.swift — the Education tab:
// header → search → popular videos rail → topic grid → quick reads, with a
// topic page and a lesson page behind it.
//
// Videos and articles are external links (YouTube, Khan Academy,
// Investopedia), opened in the browser exactly as iOS opens them, so nothing
// here pretends to host content it doesn't.
//
// Deviation from iOS: navigation is local state rather than NavigationStack
// destinations, matching how the other Android tabs already push subpages, and
// the system back gesture pops them.
//

package com.finnacalc.android.features.education

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.finnacalc.android.core.designsystem.Paper
import com.finnacalc.android.core.designsystem.PaperSectionHeader
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.designsystem.paperCard
import com.finnacalc.android.core.designsystem.staggeredAppear

// MARK: - Topic metadata

private data class TopicMeta(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val blurb: String,
)

/**
 * A plain 2-column grid. Taxes used to run full width as the odd one out;
 * Business squares the grid off so every topic is the same size.
 */
private val topicMeta = listOf(
    TopicMeta("credit", "Credit & Debt", Icons.Filled.CreditCard, "Credit scores, borrowing, and paying off debt"),
    TopicMeta("investing", "Investing", Icons.AutoMirrored.Outlined.TrendingUp, "Stocks, bonds, funds, and managing risk"),
    TopicMeta("budgeting", "Budgeting", Icons.Filled.PieChart, "Budgets, tracking spending, and saving"),
    TopicMeta("retirement", "Retirement", Icons.Filled.Shield, "401(k)s, IRAs, and long-term growth"),
    TopicMeta("taxes", "Taxes", Icons.Filled.Description, "Brackets, deductions, credits, and forms"),
    TopicMeta("business", "Business", Icons.Filled.Work, "Running the numbers on a business you own"),
)

/** The video chip's warm tint, kept distinct from the article chip's neutral. */
private val videoChipBG: Color
    @Composable get() = Theme.colors.negative.copy(alpha = if (Theme.colors.isDark) 0.18f else 0.10f)

// MARK: - Tab root

@Composable
fun EducationScreen() {
    var topic by remember { mutableStateOf<String?>(null) }
    var lesson by remember { mutableStateOf<Pair<String, EduItem>?>(null) }

    val currentLesson = lesson
    val currentTopic = topic

    when {
        currentLesson != null -> {
            BackHandler { lesson = null }
            LessonPage(currentLesson.first, currentLesson.second) { lesson = null }
        }

        currentTopic != null -> {
            BackHandler { topic = null }
            TopicPage(
                topicId = currentTopic,
                onBack = { topic = null },
                onOpenLesson = { lesson = currentTopic to it },
            )
        }

        else -> EducationHub { topic = it }
    }
}

/** Exactly 4 curated picks, one per distinct topic, per the design spec. */
private val popularPicks = listOf(
    "investing" to "Index Funds vs. Mutual Funds vs. ETFs",
    "credit" to "What Is a Credit Score?",
    "budgeting" to "How to Manage Your Money (The 50/30/20 Rule)",
    "retirement" to "What Is a 401(k)?",
)

/** Four real catalog articles, one from each of four different topics. */
private val quickReadPicks = listOf(
    "budgeting" to "What Is a Budget?",
    "credit" to "How to Raise Your Credit Score",
    "investing" to "How and Where to Start Investing",
    "retirement" to "How to Invest for Retirement",
)

@Composable
private fun EducationHub(onOpenTopic: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val results = remember(query) { EducationContent.search(query) }
    val context = LocalContext.current

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }

    // Looked up by (topic, title) against the live catalog so a future catalog
    // edit can never desync a rail from real, tappable content.
    val popularVideos = remember {
        popularPicks.mapNotNull { (id, title) ->
            EducationContent.videoLessons[id]?.firstOrNull { it.title == title }?.let { id to it }
        }
    }
    val quickReads = remember {
        quickReadPicks.mapNotNull { (id, title) ->
            EducationContent.readingResources[id]?.firstOrNull { it.title == title }?.let { id to it }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper.page)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("Learn money", style = Theme.sans(28, FontWeight.Bold), color = Paper.ink)
            Text(
                "Short lessons on money, in plain language. Watch or read, whichever suits you.",
                style = Theme.sans(Theme.FontSize.sm),
                color = Paper.muted,
            )
        }

        SearchBar(query, { query = it }) { query = "" }

        when {
            trimmed.isEmpty() -> {
                // Popular videos — content bleeds off the trailing edge to
                // signal scrollability, so the rail cancels the page padding
                // and re-adds it as a leading inset.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(horizontal = 22.dp)) {
                        PaperSectionHeader("POPULAR VIDEOS")
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(popularVideos.size) { i ->
                            val (topicId, item) = popularVideos[i]
                            VideoRailCard(topicId, item) { open(item.url) }
                        }
                    }
                }

                // Topics, 2 across.
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PaperSectionHeader("TOPICS")
                    topicMeta.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            row.forEach { meta ->
                                TopicGridCard(meta, Modifier.weight(1f)) { onOpenTopic(meta.id) }
                            }
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }

                // Quick reads.
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PaperSectionHeader("QUICK READS")
                    Column(Modifier.paperCard(18.dp)) {
                        quickReads.forEachIndexed { index, (topicId, item) ->
                            QuickReadRow(topicId, item) { open(item.url) }
                            if (index < quickReads.size - 1) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Paper.divider)
                                )
                            }
                        }
                    }
                }
            }

            results.isEmpty() -> EmptyResults(trimmed) { query = "" }

            else -> Column(
                modifier = Modifier.padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${results.size} result${if (results.size != 1) "s" else ""} for “$trimmed”",
                    style = Theme.sans(Theme.FontSize.sm),
                    color = Paper.muted,
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    results.forEach { doc -> ResultRow(doc) { open(doc.url) } }
                }
            }
        }

        Box(Modifier.height(8.dp))
    }
}

// MARK: - Search

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 22.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Paper.card)
            .border(1.dp, Paper.border, CircleShape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Paper.muted, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            textStyle = Theme.sans(Theme.FontSize.sm).copy(color = Paper.ink),
            cursorBrush = SolidColor(Paper.cobalt),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Search lessons", style = Theme.sans(Theme.FontSize.sm), color = Paper.muted)
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = "Clear",
                tint = Paper.muted,
                modifier = Modifier
                    .size(18.dp)
                    .fcPressable(onClear),
            )
        }
    }
}

@Composable
private fun EmptyResults(query: String, onBrowse: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Paper.muted, modifier = Modifier.size(32.dp))
        Text(
            "We couldn’t find anything for “$query”",
            style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
            color = Paper.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            "Try different words, or browse the topics below. We may not have a lesson on that yet.",
            style = Theme.sans(Theme.FontSize.sm),
            color = Paper.muted,
            textAlign = TextAlign.Center,
        )
        Text(
            "Browse all topics",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
            color = Paper.cobalt,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(CircleShape)
                .border(1.5.dp, Paper.cobalt, CircleShape)
                .fcPressable(onBrowse)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

// MARK: - Cards

@Composable
private fun VideoRailCard(topicId: String, item: EduItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(232.dp)
            .clip(shape)
            .background(Paper.card)
            .border(1.dp, Paper.border, shape)
            .fcPressable(onClick),
    ) {
        VideoThumbnail(item.url, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                item.title,
                style = Theme.sans(13, FontWeight.SemiBold),
                color = Paper.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                EducationContent.topicName(topicId),
                style = Theme.sans(11),
                color = Paper.muted,
            )
        }
    }
}

@Composable
private fun VideoThumbnail(url: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val thumb = EducationContent.thumbnailUrl(url)
        if (thumb != null) {
            SubcomposeAsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { GradientPlaceholder() },
                error = { GradientPlaceholder() },
            )
        } else {
            GradientPlaceholder()
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun GradientPlaceholder() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Paper.cobalt.copy(alpha = 0.35f), Paper.cobalt.copy(alpha = 0.12f))
                )
            )
    )
}

@Composable
private fun TopicGridCard(meta: TopicMeta, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val videos = EducationContent.videoLessons[meta.id]?.size ?: 0
    val articles = EducationContent.readingResources[meta.id]?.size ?: 0
    Column(
        modifier = modifier
            .paperCard(16.dp)
            .fcPressable(onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Paper.cobaltSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(meta.icon, contentDescription = null, tint = Paper.cobalt, modifier = Modifier.size(17.dp))
        }
        Text(meta.title, style = Theme.sans(14, FontWeight.SemiBold), color = Paper.ink)
        Text(
            "$videos video${if (videos == 1) "" else "s"} · $articles article${if (articles == 1) "" else "s"}",
            style = Theme.sans(11),
            color = Paper.muted,
        )
    }
}

@Composable
private fun QuickReadRow(topicId: String, item: EduItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fcPressable(onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChipIcon(Icons.AutoMirrored.Filled.MenuBook, Paper.chipFill, Paper.ink)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                item.title,
                style = Theme.sans(13, FontWeight.SemiBold),
                color = Paper.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Article · ${EducationContent.topicName(topicId)}",
                style = Theme.sans(11),
                color = Paper.muted,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.CallMade,
            contentDescription = null,
            tint = Paper.muted,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun ResultRow(doc: EduSearchDoc, onClick: () -> Unit) {
    val isVideo = doc.type == EduSearchDoc.Kind.Video
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .paperCard(16.dp)
            .fcPressable(onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ChipIcon(
            if (isVideo) Icons.Filled.PlayArrow else Icons.AutoMirrored.Filled.MenuBook,
            if (isVideo) videoChipBG else Paper.chipFill,
            if (isVideo) Theme.colors.negative else Paper.ink,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(doc.title, style = Theme.sans(13, FontWeight.SemiBold), color = Paper.ink)
            Text(
                "${if (isVideo) "Video lesson" else "Article"} · ${doc.topicName}",
                style = Theme.sans(11),
                color = Paper.muted,
            )
        }
    }
}

@Composable
private fun ChipIcon(icon: ImageVector, fill: Color, tint: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

// MARK: - Topic page

@Composable
private fun TopicPage(topicId: String, onBack: () -> Unit, onOpenLesson: (EduItem) -> Unit) {
    val meta = topicMeta.firstOrNull { it.id == topicId }
    val videos = EducationContent.videoLessons[topicId].orEmpty()
    val articleCount = EducationContent.readingResources[topicId]?.size ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper.page)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SubpageBar(meta?.title ?: EducationContent.topicName(topicId), onBack)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Paper.cobaltSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    meta?.icon ?: Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = Paper.cobalt,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                meta?.title ?: EducationContent.topicName(topicId),
                style = Theme.sans(26, FontWeight.Bold),
                color = Paper.ink,
            )
            meta?.blurb?.let {
                Text(it, style = Theme.sans(Theme.FontSize.sm), color = Paper.muted)
            }
        }

        if (videos.isEmpty()) {
            // A topic can exist before its lessons do. Say so, rather than
            // leaving the page as a bare heading that reads broken.
            Text(
                "Lessons for this topic are on the way.",
                style = Theme.sans(Theme.FontSize.sm),
                color = Paper.muted,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperSectionHeader("LESSONS")
                videos.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredAppear(index)
                            .paperCard(16.dp)
                            .fcPressable { onOpenLesson(item) }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChipIcon(Icons.Filled.PlayArrow, videoChipBG, Theme.colors.negative)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.title, style = Theme.sans(13, FontWeight.SemiBold), color = Paper.ink)
                            Text(
                                if (articleCount > 0) {
                                    "Video · $articleCount related read${if (articleCount == 1) "" else "s"}"
                                } else {
                                    "Video lesson"
                                },
                                style = Theme.sans(11),
                                color = Paper.muted,
                            )
                        }
                        // A lesson row pushes an internal screen, so it uses a
                        // chevron rather than the external-link glyph.
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Paper.chevron,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Lesson page

/**
 * The video hero (opens YouTube), then every reading resource for the topic as
 * "Related reading" — the catalog defines no per-item video↔article
 * correspondence, so this bundles the video with all of its topic's reads
 * rather than inventing a pairing.
 */
@Composable
private fun LessonPage(topicId: String, video: EduItem, onBack: () -> Unit) {
    val articles = EducationContent.readingResources[topicId].orEmpty()
    val context = LocalContext.current

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper.page)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SubpageBar(EducationContent.topicName(topicId), onBack)

        val shape = RoundedCornerShape(18.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Paper.card)
                .border(1.dp, Paper.border, shape)
                .fcPressable { open(video.url) },
        ) {
            VideoThumbnail(video.url, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(video.title, style = Theme.sans(15, FontWeight.SemiBold), color = Paper.ink)
                    Text("Watch on YouTube", style = Theme.sans(11), color = Paper.muted)
                }
                Icon(
                    Icons.AutoMirrored.Outlined.CallMade,
                    contentDescription = null,
                    tint = Paper.muted,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        if (articles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperSectionHeader("RELATED READING")
                articles.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .paperCard(16.dp)
                            .fcPressable { open(item.url) }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChipIcon(Icons.AutoMirrored.Filled.MenuBook, Paper.chipFill, Paper.ink)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.title, style = Theme.sans(13, FontWeight.SemiBold), color = Paper.ink)
                            Text("Read the article", style = Theme.sans(11), color = Paper.muted)
                        }
                        Icon(
                            Icons.AutoMirrored.Outlined.CallMade,
                            contentDescription = null,
                            tint = Paper.muted,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubpageBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Back",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
            color = Paper.cobalt,
            modifier = Modifier.fcPressable(onBack),
        )
        Text(
            title,
            style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold).copy(letterSpacing = 0.6.sp),
            color = Paper.muted,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}
