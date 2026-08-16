//
// TaxIcons.kt
//
// The lucide icon names the interview data carries (Section.icon,
// LifeSituationOption.icon) resolved to Material icons — the Android analogue
// of the iOS `sfSymbol(forLucide:)` map. Same fallback as the web and iOS: an
// unknown name renders a check rather than nothing.
//

package com.finnacalc.android.features.taxes.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

object TaxIcons {
    fun forLucide(name: String): ImageVector = when (name) {
        "User" -> Icons.Filled.Person
        "Users" -> Icons.Filled.People
        "Briefcase" -> Icons.Filled.Work
        "Store" -> Icons.Filled.Storefront
        "TrendingUp" -> Icons.AutoMirrored.Outlined.TrendingUp
        "PiggyBank" -> Icons.Filled.Savings
        "Coins" -> Icons.Filled.AttachMoney
        "Sliders" -> Icons.Filled.Tune
        "Receipt" -> Icons.Filled.Receipt
        "Gift" -> Icons.Filled.CardGiftcard
        "Wallet" -> Icons.Filled.AccountBalanceWallet
        "Home" -> Icons.Filled.Home
        "GraduationCap" -> Icons.Filled.School
        "Baby" -> Icons.Filled.ChildFriendly
        "Landmark" -> Icons.Filled.AccountBalance
        "Zap" -> Icons.Filled.Bolt
        "Doc" -> Icons.Filled.Description
        else -> Icons.Filled.Check
    }
}
