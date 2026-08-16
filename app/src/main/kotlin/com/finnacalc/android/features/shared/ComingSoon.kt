//
// ComingSoon.kt
//
// Port of iOS Features/Shared/ComingSoonView.swift — shared placeholder for
// the feature tabs whose real content lands in later phases. Built on the
// Phase 1 design system so the shell already reads like FinnaCalc.
//

package com.finnacalc.android.features.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.FCBadge
import com.finnacalc.android.core.designsystem.FCBadgeVariant
import com.finnacalc.android.core.designsystem.FCCard
import com.finnacalc.android.core.designsystem.FCCardContent
import com.finnacalc.android.core.designsystem.FCCardDescription
import com.finnacalc.android.core.designsystem.FCCardHeader
import com.finnacalc.android.core.designsystem.FCCardTitle
import com.finnacalc.android.core.designsystem.Theme

@Composable
fun ComingSoonView(
    icon: ImageVector,
    title: String,
    message: String,
    phase: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        FCCard {
            FCCardHeader {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = Theme.colors.primary, modifier = Modifier.size(32.dp))
                    FCCardTitle(title)
                }
                FCCardDescription(message)
            }
            FCCardContent {
                FCBadge(phase, variant = FCBadgeVariant.Secondary)
            }
        }
    }
}
