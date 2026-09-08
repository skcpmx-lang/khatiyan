package com.shohan.khatiyan.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One spacing rhythm for the whole app (premium-calm scale):
 * horizontal gutters, section gaps and card padding all come from here —
 * screens never invent ad-hoc numbers.
 */
object KhatiyanSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** Screen content gutter — every major screen aligns to this grid. */
    val screenH: Dp = 20.dp

    /** Space between sections inside a screen. */
    val section: Dp = 20.dp

    /** Extra room below scrollable content so the FAB never covers the last row. */
    val fabClearance: Dp = 96.dp

    /** Inside every AppCard. */
    val cardPadding = PaddingValues(18.dp)
}
