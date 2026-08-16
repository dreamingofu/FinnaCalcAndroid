//
// SectorCatalog.kt
//
// Port of iOS Features/Investing/SectorCatalog.swift — static metadata for
// the Cash App-style category tiles and sector pages: the 7 sectors the
// /api/market-overview route tracks, each with a vibrant tile colour, an
// icon, and a one-line description. `name` matches MarketQuote.sector so the
// category page can filter the market-overview stocks list.
//

package com.finnacalc.android.features.investing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class SectorMeta(
    val id: String,
    val name: String,        // matches MarketQuote.sector
    val color: Color,
    val icon: ImageVector,
    val blurb: String,
)

object SectorCatalog {
    val all: List<SectorMeta> = listOf(
        SectorMeta(
            "technology", "Technology", Color(0xFF3B5BDB), Icons.Default.Memory,
            "Hardware, software, and the companies building the future.",
        ),
        SectorMeta(
            "healthcare", "Healthcare", Color(0xFF0CA678), Icons.Default.MedicalServices,
            "Drugmakers, insurers, and medical-device companies.",
        ),
        SectorMeta(
            "financials", "Financials", Color(0xFFE8590C), Icons.Default.Business,
            "Businesses that are in the business of money.",
        ),
        SectorMeta(
            "consumer", "Consumer", Color(0xFFE64980), Icons.Default.ShoppingCart,
            "Retail, autos, and the brands people buy every day.",
        ),
        SectorMeta(
            "energy", "Energy", Color(0xFFF08C00), Icons.Default.Bolt,
            "Oil, gas, and the companies that power the world.",
        ),
        SectorMeta(
            "communication", "Communication", Color(0xFF7048E8), Icons.Default.SettingsInputAntenna,
            "Media, telecom, and streaming companies.",
        ),
        SectorMeta(
            "industrials", "Industrials", Color(0xFF1098AD), Icons.Default.Settings,
            "Aerospace, machinery, and logistics companies.",
        ),
    )

    fun meta(name: String): SectorMeta? =
        all.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
