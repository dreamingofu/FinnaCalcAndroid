//
// FCListRow.kt
//
// Port of iOS FCListRow.swift — ListRow / IconChip, the standard navigation
// row (calculator hub, "Research & Learn", education topics). A brand-tinted
// IconChip fronts a title + subtitle, with a trailing chevron by default.
//
// Deviation from iOS: SF Symbols become Material icons (closest match chosen
// per call site).
//

package com.finnacalc.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A rounded, tinted square holding an icon — the brand-blue icon chip. */
@Composable
fun FCIconChip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: FCResultTone = FCResultTone.Neutral,
    size: Dp = 40.dp,
) {
    val foreground = when (tone) {
        FCResultTone.Neutral -> Theme.colors.primary
        FCResultTone.Positive -> Theme.colors.positive
        FCResultTone.Negative -> Theme.colors.negative
    }
    val background = when (tone) {
        FCResultTone.Neutral -> Theme.colors.brandTint
        FCResultTone.Positive -> Theme.colors.positive.copy(alpha = 0.12f)
        FCResultTone.Negative -> Theme.colors.negative.copy(alpha = 0.12f)
    }
    Row(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(Theme.Radius.md))
            .background(background),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** A navigation row: icon chip + title/subtitle + trailing accessory. */
@Composable
fun FCListRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    iconTone: FCResultTone = FCResultTone.Neutral,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {
        // Default trailing chevron (the common "navigates somewhere" row).
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Theme.colors.mutedForeground,
        )
    },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FCIconChip(icon, tone = iconTone)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
                color = Theme.colors.foreground,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = Theme.sans(Theme.FontSize.sm),
                    color = Theme.colors.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}
