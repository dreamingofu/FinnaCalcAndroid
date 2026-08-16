//
// DesignSystemGallery.kt
//
// Port of iOS DesignSystemGallery.swift — a single screen exercising every FC*
// component in both color schemes, for eyeballing design-system parity against
// finnacalc.com. Not shipped in any feature flow — previews and manual QA.
//

package com.finnacalc.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun DesignSystemGallery(modifier: Modifier = Modifier) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        GallerySection("Buttons") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FCButton("Default") {}
                    FCButton("Secondary", variant = FCButtonVariant.Secondary) {}
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FCButton("Destructive", variant = FCButtonVariant.Destructive) {}
                    FCButton("Outline", variant = FCButtonVariant.Outline) {}
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FCButton("Ghost", variant = FCButtonVariant.Ghost) {}
                    FCButton("Link", variant = FCButtonVariant.Link) {}
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FCButton("sm", size = FCButtonSize.Sm) {}
                    FCButton("lg", size = FCButtonSize.Lg) {}
                    FCButton(onClick = {}, size = FCButtonSize.Icon) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
                FCButton("Disabled", enabled = false) {}
            }
        }

        GallerySection("Badges") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FCBadge("Default")
                FCBadge("Secondary", variant = FCBadgeVariant.Secondary)
                FCBadge("Destructive", variant = FCBadgeVariant.Destructive)
                FCBadge("Outline", variant = FCBadgeVariant.Outline)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FCBadge("+1.8%", variant = FCBadgeVariant.Positive, dot = true)
                FCBadge("-0.4%", variant = FCBadgeVariant.Negative, dot = true)
                FCBadge("Pending", variant = FCBadgeVariant.Caution)
            }
        }

        GallerySection("Text fields") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FCTextField("you@example.com", email, { email = it }, showsPlaceholder = true)
                FCTextField("Password", password, { password = it }, isSecure = true, showsPlaceholder = true)
                FCTextField("Disabled", "", {}, enabled = false)
            }
        }

        GallerySection("Card") {
            FCCard {
                FCCardHeader {
                    FCCardTitle("Emergency Fund")
                    FCCardDescription("How many months of expenses you have saved.")
                }
                FCCardContent {
                    Text(
                        "3.5 months",
                        style = Theme.sans(Theme.FontSize.base, FontWeight.Medium),
                        color = Theme.colors.foreground,
                    )
                }
                FCCardFooter {
                    FCButton("Recalculate") {}
                    FCBadge("On track", variant = FCBadgeVariant.Secondary)
                }
            }
        }

        GallerySection("Stats & rows") {
            FCCard {
                FCCardContent {
                    Column(
                        Modifier.padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        FCStat(
                            "Payment per period", "$1,204.55",
                            tone = FCResultTone.Positive, size = FCStatSize.Large,
                        )
                        FCResultRow("Total interest cost", "$12,273.00", tone = FCResultTone.Negative)
                        FCResultRow("Principal financed", "$50,000.00", emphasized = true)
                    }
                }
            }
            FCCard {
                FCListRow(
                    Icons.AutoMirrored.Filled.ShowChart, "AAPL",
                    iconTone = FCResultTone.Positive, subtitle = "Apple Inc.",
                ) {
                    FCBadge("+1.8%", variant = FCBadgeVariant.Positive, dot = true)
                }
            }
        }

        GallerySection("Wordmark") {
            FCWordmark()
            FCWordmark(size = 34)
        }
    }
}

@Composable
private fun GallerySection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
            color = Theme.colors.mutedForeground,
        )
        content()
    }
}

@Preview(name = "Design System — Light", showBackground = true)
@Composable
private fun GalleryLightPreview() {
    FinnaTheme(darkTheme = false) { DesignSystemGallery() }
}

@Preview(name = "Design System — Dark", showBackground = true)
@Composable
private fun GalleryDarkPreview() {
    FinnaTheme(darkTheme = true) { DesignSystemGallery() }
}
