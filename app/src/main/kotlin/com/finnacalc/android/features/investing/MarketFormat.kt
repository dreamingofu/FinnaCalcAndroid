//
// MarketFormat.kt
//
// Shared formatting + small building blocks for the investing screens: the
// change pill every quote row wears, market-cap and volume abbreviations, and
// the "—" convention the iOS app holds to (never a fabricated figure; a dash
// while loading or when a value is genuinely unknown).
//

package com.finnacalc.android.features.investing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.features.calculators.CalcFormat
import kotlin.math.abs

object MarketFormat {
    /** "$12.34" — prices keep their cents. */
    fun price(value: Double): String = "$" + CalcFormat.fixed(value, 2)

    /** "+1.84%" / "−0.42%", with the sign carried by the glyph. */
    fun percent(value: Double): String = CalcFormat.fixed(abs(value), 2) + "%"

    /** "$1.2T", "$845.3B", "$12.4M" — market caps and volumes at a glance. */
    fun abbreviated(value: Double): String {
        val v = abs(value)
        val (scaled, suffix) = when {
            v >= 1e12 -> v / 1e12 to "T"
            v >= 1e9 -> v / 1e9 to "B"
            v >= 1e6 -> v / 1e6 to "M"
            v >= 1e3 -> v / 1e3 to "K"
            else -> v to ""
        }
        val body = if (suffix.isEmpty()) CalcFormat.int(scaled) else CalcFormat.fixed(scaled, 1)
        return (if (value < 0) "−" else "") + body + suffix
    }

    fun abbreviatedMoney(value: Double): String = "$" + abbreviated(value)

    /**
     * Parses the string-typed quote fields (the routes carry Alpha
     * Vantage-shaped strings). Empty/garbage becomes null, never zero — a
     * zero price would render as a real figure.
     */
    fun parse(raw: String): Double? =
        raw.replace("%", "").replace("$", "").replace(",", "").trim().toDoubleOrNull()
}

/** The coloured %-change pill every quote row wears. */
@Composable
fun ChangePill(
    changePct: Double,
    modifier: Modifier = Modifier,
    fontSize: Int = Theme.FontSize.xs,
) {
    val isUp = changePct >= 0
    val tint = if (isUp) Theme.colors.positive else Theme.colors.negative
    Row(
        modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (isUp) "↗" else "↘", style = Theme.sans(fontSize - 1, FontWeight.Bold), color = tint)
        Text(MarketFormat.percent(changePct), style = Theme.figure(fontSize, FontWeight.Bold), color = tint)
    }
}
