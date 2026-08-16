//
// Shadow.kt
//
// Design-system elevation shadows, ported from iOS Theme.Elevation +
// View.fcShadow. Compose's Modifier.shadow() renders via elevation rather than
// (blur, y-offset) pairs, so the iOS triples map to the closest dp elevation
// with an ink-tinted ambient/spot color — visually equivalent at card scale.
//

package com.finnacalc.android.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

private val ink = Color(0xFF020817)

/** Apply a design-system elevation shadow (clip stays with the caller). */
fun Modifier.fcShadow(elevation: Theme.Elevation, shape: Shape): Modifier = composed {
    val tint = if (elevation == Theme.Elevation.Brand) Theme.colors.primary else ink
    shadow(
        elevation = elevation.y + (elevation.radius / 2),
        shape = shape,
        clip = false,
        ambientColor = tint.copy(alpha = elevation.alpha),
        spotColor = tint.copy(alpha = elevation.alpha),
    )
}
