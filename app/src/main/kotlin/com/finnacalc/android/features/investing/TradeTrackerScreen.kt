//
// TradeTrackerScreen.kt
//
// Port of the directory and person pages from iOS
// Features/Investing/TradeTrackerView.swift + TrackerPersonView.swift.
//
// Following is the same bookmark gesture as the stock watchlist, kept in its
// own store so following a person never pollutes the symbol watchlist. The
// bell subscribes to that person's trade alerts.
//
// The page shows WHO, never a figure: no trade feed exists yet, and the
// person page says so plainly instead of rendering an empty feed as though
// they never trade.
//

package com.finnacalc.android.features.investing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.designsystem.staggeredAppear

@Composable
fun TradeTrackerScreen(onOpenSymbol: (String) -> Unit = {}) {
    var person by remember { mutableStateOf<String?>(null) }
    val current = person

    if (current != null) {
        BackHandler { person = null }
        TrackerPersonScreen(current, onBack = { person = null }, onOpenSymbol = onOpenSymbol)
        return
    }

    var filter by remember { mutableStateOf<TrackerCategory?>(null) }
    val following by TrackerFollowStore.ids.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Trade Tracker",
                style = Theme.sans(26, FontWeight.Bold),
                color = Theme.colors.foreground,
            )
            Text(
                "Follow the investors, insiders, and politicians whose trades get watched. " +
                    "Their filings arrive here as we can read them.",
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )
        }

        // Category chips: All plus the three groups.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("all") { FilterChip("All", filter == null) { filter = null } }
            items(TrackerCategory.entries.size) { i ->
                val category = TrackerCategory.entries[i]
                FilterChip(category.title, filter == category) { filter = category }
            }
        }

        // Following first, when there is one — the group the user built.
        val followed = TrackerCatalog.all.filter { following.contains(it.id) }
        if (followed.isNotEmpty() && filter == null) {
            PersonGroup("FOLLOWING", followed) { person = it }
        }

        val groups = if (filter != null) listOf(filter!!) else TrackerCategory.entries
        groups.forEach { category ->
            PersonGroup(
                category.title.uppercase(),
                TrackerCatalog.inCategory(category),
            ) { person = it }
        }
    }
}

@Composable
private fun FilterChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        title,
        style = Theme.sans(Theme.FontSize.sm, if (selected) FontWeight.Bold else FontWeight.SemiBold),
        color = if (selected) Color.White else Theme.colors.foreground,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Theme.colors.primary else Theme.colors.secondary)
            .fcPressable(onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    )
}

@Composable
private fun PersonGroup(title: String, people: List<TrackedPerson>, onOpen: (String) -> Unit) {
    if (people.isEmpty()) return
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            style = Theme.sans(11, FontWeight.Bold).copy(letterSpacing = 1.2.sp),
            color = Theme.colors.mutedForeground,
        )
        people.forEachIndexed { index, p ->
            PersonRow(p, Modifier.staggeredAppear(index)) { onOpen(p.id) }
        }
    }
}

@Composable
private fun PersonRow(person: TrackedPerson, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val following by TrackerFollowStore.ids.collectAsState()
    val isFollowing = following.contains(person.id)
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .fcPressable(onOpen)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackerAvatar(person, 46.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                person.name,
                style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                color = Theme.colors.foreground,
            )
            Text(
                person.org,
                style = Theme.sans(11),
                color = Theme.colors.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            if (isFollowing) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            contentDescription = if (isFollowing) "Unfollow ${person.name}" else "Follow ${person.name}",
            tint = if (isFollowing) Theme.colors.primary else Theme.colors.borderStrong,
            modifier = Modifier
                .size(22.dp)
                .fcPressable { TrackerFollowStore.toggle(person.id) },
        )
    }
}

/**
 * The person's freely licensed portrait in a circle, with the org's logo
 * badged on the corner. No free portrait falls back to a tinted monogram, as
 * on iOS.
 */
@Composable
fun TrackerAvatar(person: TrackedPerson, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Theme.colors.brandTint),
            contentAlignment = Alignment.Center,
        ) {
            if (person.imageUrl.isNotEmpty()) {
                SubcomposeAsyncImage(
                    model = person.imageUrl,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { Monogram(person, size) },
                    error = { Monogram(person, size) },
                )
            } else {
                Monogram(person, size)
            }
        }

        val badgeSize = size * 0.38f
        if (person.emojiBadge.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .offset(x = 2.dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(Theme.colors.card),
                contentAlignment = Alignment.Center,
            ) {
                Text(person.emojiBadge, style = Theme.sans((badgeSize.value * 0.62f).toInt()))
            }
        } else if (person.logoSymbol.isNotEmpty() || person.logoDomain.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .offset(x = 2.dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(Theme.colors.card)
                    .padding(1.dp),
            ) {
                CompanyLogo(
                    symbol = person.logoSymbol.ifEmpty { person.logoDomain },
                    size = badgeSize,
                )
            }
        }
    }
}

@Composable
private fun Monogram(person: TrackedPerson, size: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            person.monogram,
            style = Theme.sans((size.value * 0.34f).toInt(), FontWeight.Bold),
            color = Theme.colors.primary,
        )
    }
}

// MARK: - Person page

@Composable
private fun TrackerPersonScreen(
    personId: String,
    onBack: () -> Unit,
    onOpenSymbol: (String) -> Unit,
) {
    val person = TrackerCatalog.person(personId) ?: return
    val following by TrackerFollowStore.ids.collectAsState()
    val alerts by TrackerAlertStore.ids.collectAsState()
    val isFollowing = following.contains(person.id)
    val alerting = alerts.contains(person.id)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "Back",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
            color = Theme.colors.primary,
            modifier = Modifier.fcPressable(onBack),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TrackerAvatar(person, 96.dp)
            Text(
                person.name,
                style = Theme.sans(24, FontWeight.Bold),
                color = Theme.colors.foreground,
                textAlign = TextAlign.Center,
            )
            Text(
                person.org,
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionPill(
                    if (isFollowing) "Following" else "Follow",
                    if (isFollowing) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    isFollowing,
                ) { TrackerFollowStore.toggle(person.id) }
                ActionPill(
                    if (alerting) "Alerts on" else "Alerts",
                    if (alerting) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                    alerting,
                ) { TrackerAlertStore.toggle(person.id) }
            }
        }

        Text(
            person.blurb,
            style = Theme.sans(Theme.FontSize.sm).copy(lineHeight = 21.sp),
            color = Theme.colors.textBody,
        )

        if (person.logoSymbol.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Theme.colors.card)
                    .border(1.dp, Theme.colors.border, RoundedCornerShape(16.dp))
                    .fcPressable { onOpenSymbol(person.logoSymbol) }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompanyLogo(person.logoSymbol, size = 34.dp)
                Text(
                    "Open ${person.logoSymbol}",
                    style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                    color = Theme.colors.foreground,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // What we can and can't show, said plainly rather than rendering an
        // empty feed as though this person never trades.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.colors.secondary)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Trades aren't here yet",
                style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                color = Theme.colors.foreground,
            )
            Text(
                if (person.cik.isNotEmpty()) {
                    "We have a verified SEC filer for ${person.name}, so their filings will appear " +
                        "here once the feed is wired up. Follow now and the alerts will find you."
                } else {
                    "We don't have a verified filer for ${person.name}, so there is nothing to " +
                        "read yet. Following still works, and this page fills in if that changes."
                },
                style = Theme.sans(Theme.FontSize.xs).copy(lineHeight = 17.sp),
                color = Theme.colors.mutedForeground,
            )
        }
    }
}

@Composable
private fun ActionPill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) Theme.colors.primary else Theme.colors.secondary)
            .fcPressable(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) Color.White else Theme.colors.foreground,
            modifier = Modifier.size(15.dp),
        )
        Text(
            title,
            style = Theme.sans(12, FontWeight.SemiBold),
            color = if (active) Color.White else Theme.colors.foreground,
        )
    }
}
