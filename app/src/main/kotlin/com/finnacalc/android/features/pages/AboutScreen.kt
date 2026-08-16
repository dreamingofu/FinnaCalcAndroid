//
// AboutScreen.kt
//
// Port of iOS Features/Pages/AboutView.swift — a hero statement, Mission &
// Vision cards, "What We Offer", "Our Core Values", "Why Choose FinnaCalc?",
// credits, and a "Get in Touch" contact block. Copy ported verbatim.
//
// The web lays this out as a responsive 1/2/3/4-column grid; on a phone
// everything stacks vertically, which is the natural single-column collapse
// of those grids and what iOS does too.
//

package com.finnacalc.android.features.pages

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.FCCard
import com.finnacalc.android.core.designsystem.FCCardContent
import com.finnacalc.android.core.designsystem.FCCardHeader
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.market.LogoDev

/**
 * The lucide brand-icon hues the web uses, per-icon. Fixed by design here
 * (they are the reference's own palette, not theme roles) and identical in
 * both schemes, as on iOS.
 */
internal object IconColor {
    val blue = Color(0xFF2563EB)
    val red = Color(0xFFDC2626)
    val green = Color(0xFF16A34A)
    val purple = Color(0xFF9333EA)
    val orange = Color(0xFFEA580C)
}

private data class PageItem(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val body: String,
)

private val offerItems = listOf(
    PageItem(
        Icons.Filled.PieChart, IconColor.green, "Budgeting",
        "Budgets you build by hand or connect to your bank, with goals, bill reminders, " +
            "spending analysis, and history across every month.",
    ),
    PageItem(
        Icons.AutoMirrored.Outlined.TrendingUp, IconColor.blue, "Investing",
        "Connect a brokerage to see your live portfolio, place orders where your brokerage " +
            "allows it, follow markets and news, and dig into ten years of company financials.",
    ),
    PageItem(
        Icons.Filled.Description, IconColor.purple, "Taxes",
        "A federal tax estimator built on the real 1040 math and updated for the current tax " +
            "year, plus planning calculators. It is an estimate to check against your filing " +
            "service, not a filing.",
    ),
    PageItem(
        Icons.AutoMirrored.Filled.MenuBook, IconColor.orange, "Education",
        "Short lessons on money, investing, and taxes, so the numbers in FinnaCalc always come " +
            "with a way to understand them.",
    ),
    PageItem(
        Icons.Filled.Forum, IconColor.red, "FinnaBot and analysis",
        "An AI helper that reads the numbers you choose to share and answers in everyday words, " +
            "from budget checkups to portfolio questions.",
    ),
    PageItem(
        Icons.Filled.Functions, IconColor.green, "Calculators",
        "The full set that started FinnaCalc: loans, savings, retirement, startup costs, " +
            "break-even, ROI, and more, free to use.",
    ),
)

private val valueItems = listOf(
    PageItem(
        Icons.Filled.Shield, IconColor.blue, "Accuracy",
        "Every calculation is thoroughly tested and based on current financial standards and " +
            "regulations.",
    ),
    PageItem(
        Icons.Filled.Favorite, IconColor.green, "Accessibility",
        "Financial planning tools should be available to everyone, regardless of their economic " +
            "background.",
    ),
    PageItem(
        Icons.Filled.Group, IconColor.purple, "Simplicity",
        "Complex financial concepts made simple and understandable for users of all experience " +
            "levels.",
    ),
    PageItem(
        Icons.Filled.MilitaryTech, IconColor.orange, "Excellence",
        "Continuous improvement and innovation to provide the best possible user experience.",
    ),
)

private val reasonItems = listOf(
    "Free at the Core" to
        "Budgets, goals, calculators, lessons, market research, and the tax estimator are free. " +
        "Paid plans add automation and deeper analysis on top.",
    "Honest Numbers" to
        "Every figure comes from your own data or a live source. When something is not known " +
        "yet, you see a dash and a reason, not a filler number.",
    "Explained, Not Just Shown" to
        "Every stat, chart, and tax line comes with a short explanation of what it means and why " +
        "it matters.",
    "Your Judgment Matters" to
        "FinnaCalc is a tool, not an advisor. We can get things wrong, markets move, and no two " +
        "tax situations match, so check what matters before you act on it.",
)

@Composable
fun AboutScreen() {
    val context = LocalContext.current

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.muted.copy(alpha = 0.4f))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        // Hero
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Empowering Smart Financial Decisions",
                style = Theme.sans(32, FontWeight.Bold),
                color = Theme.colors.foreground,
                textAlign = TextAlign.Center,
            )
            Text(
                "One app for your whole financial life: budgeting with your bank connected, " +
                    "investing with your brokerage connected, a real tax estimator, lessons that " +
                    "explain it, and the calculators FinnaCalc started with.",
                style = Theme.sans(20),
                color = Theme.colors.mutedForeground,
                textAlign = TextAlign.Center,
            )
        }

        // Mission & Vision
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            StatementCard(
                Icons.Filled.TrackChanges, IconColor.blue, "Our Mission",
                "To put honest money tools in everyone's pocket. Budgeting, investing, taxes, and " +
                    "lessons in one app, showing real numbers and never inventing one, whatever " +
                    "your background or experience.",
            )
            StatementCard(
                Icons.Filled.Favorite, IconColor.red, "Our Vision",
                "To be the most trusted place to see your whole financial life in one honest " +
                    "picture, and to help millions of people make informed decisions with it.",
            )
        }

        // What We Offer
        Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "What We Offer",
                    style = Theme.sans(28, FontWeight.Bold),
                    color = Theme.colors.foreground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Comprehensive financial tools designed for real-world applications",
                    style = Theme.sans(18),
                    color = Theme.colors.mutedForeground,
                    textAlign = TextAlign.Center,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                offerItems.forEach { StatementCard(it.icon, it.tint, it.title, it.body) }
            }
        }

        // Our Core Values
        Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Text(
                "Our Core Values",
                style = Theme.sans(28, FontWeight.Bold),
                color = Theme.colors.foreground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                valueItems.forEach { item ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(item.tint.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = item.tint,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Text(
                            item.title,
                            style = Theme.sans(Theme.FontSize.lg, FontWeight.SemiBold),
                            color = Theme.colors.foreground,
                        )
                        Text(
                            item.body,
                            style = Theme.sans(Theme.FontSize.sm),
                            color = Theme.colors.mutedForeground,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // Why Choose FinnaCalc?
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Theme.Radius.lg))
                .background(Theme.colors.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Why Choose FinnaCalc?",
                    style = Theme.sans(28, FontWeight.Bold),
                    color = Theme.colors.foreground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "We're committed to providing the most reliable and user-friendly financial " +
                        "tools available",
                    style = Theme.sans(Theme.FontSize.base),
                    color = Theme.colors.mutedForeground,
                    textAlign = TextAlign.Center,
                )
            }
            reasonItems.forEach { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        title,
                        style = Theme.sans(Theme.FontSize.lg, FontWeight.SemiBold),
                        color = Theme.colors.foreground,
                    )
                    Text(body, style = Theme.sans(Theme.FontSize.base), color = Theme.colors.mutedForeground)
                }
            }
        }

        // Get in Touch
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Get in Touch",
                style = Theme.sans(28, FontWeight.Bold),
                color = Theme.colors.foreground,
                textAlign = TextAlign.Center,
            )
            Text(
                "Have questions, suggestions, or feedback? We'd love to hear from you. Our team " +
                    "is committed to continuously improving FinnaCalc based on user needs and " +
                    "feedback.",
                style = Theme.sans(Theme.FontSize.base),
                color = Theme.colors.mutedForeground,
                textAlign = TextAlign.Center,
            )
            ContactRow("Help:", "helpfinnacalc@gmail.com") { open("mailto:$it") }
            ContactRow("Business Inquiries:", "finnacalc@gmail.com") { open("mailto:$it") }
        }

        // Credits — Logo.dev's free tier requires a visible link back, so this
        // is a licensing obligation, not a courtesy. The Wikimedia line covers
        // the Trade Tracker portraits, whose CC licences ask for credit too.
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Credits",
                style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                color = Theme.colors.foreground,
            )
            Text(
                LogoDev.ATTRIBUTION_TEXT,
                style = Theme.sans(Theme.FontSize.xs),
                color = Theme.colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fcPressable { open(LogoDev.ATTRIBUTION_URL) },
            )
            Text(
                "Portraits via Wikipedia / Wikimedia Commons. Market data by Alpaca.",
                style = Theme.sans(Theme.FontSize.xs),
                color = Theme.colors.mutedForeground,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatementCard(icon: ImageVector, tint: Color, title: String, body: String) {
    FCCard {
        FCCardHeader {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Text(
                    title,
                    style = Theme.sans(Theme.FontSize.xl2, FontWeight.SemiBold),
                    color = Theme.colors.cardForeground,
                )
            }
        }
        FCCardContent {
            Text(body, style = Theme.sans(Theme.FontSize.base), color = Theme.colors.mutedForeground)
        }
    }
}

@Composable
private fun ContactRow(label: String, email: String, onOpen: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = Theme.sans(Theme.FontSize.base, FontWeight.Bold),
            color = Theme.colors.foreground,
        )
        Text(
            email,
            style = Theme.sans(Theme.FontSize.base),
            color = Theme.colors.primary,
            modifier = Modifier.fcPressable { onOpen(email) },
        )
    }
}
