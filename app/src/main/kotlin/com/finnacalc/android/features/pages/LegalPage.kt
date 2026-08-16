//
// LegalPage.kt
//
// The shared renderer behind the Privacy and Terms screens, whose iOS
// counterparts (PrivacyView / TermsView) are each a stack of titled cards
// holding paragraphs, subheadings, bulleted lists, lead-bulleted lists, and
// two highlighted callouts.
//
// Deviation from iOS: the two screens there each re-declare the same section /
// paragraph / bulletList / callout helpers. Here the structure is data and the
// rendering is one place, so the two documents can't drift apart visually.
// The copy itself is ported verbatim.
//

package com.finnacalc.android.features.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.designsystem.FCCard
import com.finnacalc.android.core.designsystem.FCCardContent
import com.finnacalc.android.core.designsystem.FCCardHeader
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable

/** One rendered block inside a legal section. */
sealed class LegalBlock {
    data class Paragraph(val text: String) : LegalBlock()
    data class Subheading(val text: String) : LegalBlock()
    data class Bullets(val items: List<String>) : LegalBlock()

    /** Bullets whose first clause is bold, e.g. "**Access:** Request …". */
    data class LeadBullets(val items: List<Pair<String, String>>) : LegalBlock()

    /** The highlighted "Important" (blue) / "Note" (yellow) callouts. */
    data class Callout(
        val lead: String,
        val body: String,
        val tone: CalloutTone,
    ) : LegalBlock()

    /** A labelled email address. */
    data class Contact(val label: String, val email: String) : LegalBlock()
}

enum class CalloutTone { Info, Caution }

data class LegalSection(
    val title: String,
    val icon: ImageVector? = null,
    val tint: Color? = null,
    val blocks: List<LegalBlock>,
)

@Composable
fun LegalPage(title: String, effectiveDate: String?, sections: List<LegalSection>, onEmail: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.muted.copy(alpha = 0.4f))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                title,
                style = Theme.sans(36, FontWeight.Bold),
                color = Theme.colors.foreground,
                textAlign = TextAlign.Center,
            )
            if (effectiveDate != null) {
                Text(
                    effectiveDate,
                    style = Theme.sans(Theme.FontSize.sm),
                    color = Theme.colors.mutedForeground,
                )
            }
        }

        sections.forEach { section ->
            FCCard {
                FCCardHeader {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (section.icon != null) {
                            Icon(
                                section.icon,
                                contentDescription = null,
                                tint = section.tint ?: Theme.colors.foreground,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            section.title,
                            style = Theme.sans(Theme.FontSize.xl2, FontWeight.SemiBold),
                            color = Theme.colors.cardForeground,
                        )
                    }
                }
                FCCardContent {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        section.blocks.forEach { BlockView(it, onEmail) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockView(block: LegalBlock, onEmail: (String) -> Unit) {
    val body = Theme.sans(Theme.FontSize.base).copy(lineHeight = 24.sp)
    when (block) {
        is LegalBlock.Paragraph -> Text(block.text, style = body, color = Theme.colors.mutedForeground)

        is LegalBlock.Subheading -> Text(
            block.text,
            style = Theme.sans(Theme.FontSize.lg, FontWeight.SemiBold),
            color = Theme.colors.foreground,
            modifier = Modifier.padding(top = 4.dp),
        )

        is LegalBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.items.forEach { BulletRow { Text(it, style = body, color = Theme.colors.mutedForeground) } }
        }

        is LegalBlock.LeadBullets -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.items.forEach { (lead, rest) ->
                BulletRow {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Theme.colors.foreground)) {
                                append(lead)
                            }
                            append(rest)
                        },
                        style = body,
                        color = Theme.colors.mutedForeground,
                    )
                }
            }
        }

        is LegalBlock.Callout -> {
            val tint = when (block.tone) {
                CalloutTone.Info -> Theme.colors.brandBlue
                CalloutTone.Caution -> Theme.colors.caution
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(block.lead) }
                    append(block.body)
                },
                style = Theme.sans(Theme.FontSize.sm).copy(lineHeight = 21.sp),
                color = Theme.colors.foreground,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Theme.Radius.md))
                    .background(tint.copy(alpha = 0.10f))
                    .padding(12.dp),
            )
        }

        is LegalBlock.Contact -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                block.label,
                style = Theme.sans(Theme.FontSize.base, FontWeight.Bold),
                color = Theme.colors.foreground,
            )
            Text(
                block.email,
                style = body,
                color = Theme.colors.primary,
                modifier = Modifier.fcPressable { onEmail(block.email) },
            )
        }
    }
}

@Composable
private fun BulletRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text("•", style = Theme.sans(Theme.FontSize.base), color = Theme.colors.primary)
        content()
    }
}
