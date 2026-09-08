package com.shohan.khatiyan.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * খতিয়ান brand palette. Deep emerald (trust, ledger green) + old gold
 * (value, settlement) on warm paper. Light mode only — there is deliberately
 * no dark variant (Phase 5).
 */
object KhatiyanBrand {
    val Deep = Color(0xFF084C38)
    val Primary = Color(0xFF0B5C43)
    val PrimaryDark = Color(0xFF07422F)
    val Gold = Color(0xFFB8860B)
    val GoldBright = Color(0xFFD9A62E)
    val Paper = Color(0xFFF6F4EE)
    val PaperCard = Color(0xFFFFFEFC)
    val Ink = Color(0xFF191D1A)
    val InkMuted = Color(0xFF464D47)
    val Hairline = Color(0xFFE3E0D6)

    // Semantic status (also mirrored in text so status never depends on color alone)
    val Success = Color(0xFF1B7F3B)
    val SuccessBg = Color(0xFFE3F2E6)
    val Warning = Color(0xFF9C5C00)
    val WarningBg = Color(0xFFFBEBD5)
    val Danger = Color(0xFFB3261E)
    val DangerBg = Color(0xFFF9E3E1)
    val Info = Color(0xFF16607A)
    val InfoBg = Color(0xFFE0EFF5)
}

/** Categorical palette for charts (donut/breakdown). Order matters — high contrast first. */
val ChartPalette = listOf(
    Color(0xFF0B5C43),
    Color(0xFFD9A62E),
    Color(0xFF16607A),
    Color(0xFFC05746),
    Color(0xFF4C8C2B),
    Color(0xFF566A77),
)
