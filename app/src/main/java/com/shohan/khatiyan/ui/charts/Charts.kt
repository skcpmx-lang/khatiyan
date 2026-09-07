package com.shohan.khatiyan.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import kotlin.math.min
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Hand-drawn Canvas charts (Phase 22). Deliberately dependency-free — every
 * value passed in comes from real Room queries; the components themselves hold
 * no numbers. Animations are short ease-ins, nothing decorative.
 */

data class ChartSlice(val label: String, val valuePaisa: Long, val color: Color)

@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = 24.dp,
    centerTitle: String = "",
    centerSubtitle: String = "",
) {
    val total = slices.fold(0L) { a, s -> a + s.valuePaisa.coerceAtLeast(0) }
    val progress = animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 650),
        label = "donut",
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val d = min(size.width, size.height) - stroke
            val topLeftX = (size.width - d) / 2f
            val topLeftY = (size.height - d) / 2f
            if (total <= 0L) {
                drawArc(
                    color = Color(0xFFE3E0D6),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke),
                    size = Size(d, d),
                    topLeft = androidx.compose.ui.geometry.Offset(topLeftX, topLeftY),
                )
            } else {
                var start = -90f
                for (s in slices) {
                    if (s.valuePaisa <= 0L) continue
                    val sweep = (s.valuePaisa.toFloat() / total.toFloat()) * 360f * progress.value
                    drawArc(
                        color = s.color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = stroke),
                        size = Size(d, d),
                        topLeft = androidx.compose.ui.geometry.Offset(topLeftX, topLeftY),
                    )
                    start += sweep
                }
            }
        }
        if (centerTitle.isNotEmpty() || centerSubtitle.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (centerTitle.isNotEmpty()) {
                    Text(
                        centerTitle,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (centerSubtitle.isNotEmpty()) {
                    Text(
                        centerSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun ChartLegend(rows: List<Pair<String, String>>, colors: List<Color>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { idx, (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) { drawRect(colors.getOrElse(idx) { Color.Gray }) }
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(value, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Grouped vertical bars with a shared scale; used for monthly income/expense. */
@Composable
fun GroupedBarChart(
    groups: List<BarGroup>,
    seriesColors: List<Color>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 150.dp,
) {
    val progress = animateFloatAsState(if (groups.isEmpty()) 0f else 1f, tween(500), label = "bars")
    val maxValue = groups.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Column(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (groups.isEmpty()) return@Canvas
            val n = groups.size
            val series = groups.first().values.size
            val groupWidth = size.width / n
            val barGap = groupWidth * 0.16f
            val barWidth = ((groupWidth - barGap) / max(1, series)) * 0.9f
            for (g in groups.indices) {
                val group = groups[g]
                for (s in group.values.indices) {
                    val v = group.values[s]
                    if (v <= 0L) continue
                    val h = (v.toFloat() / maxValue.toFloat()) * (size.height - 6.dp.toPx()) * progress.value
                    val left = g * groupWidth + barGap / 2f + s * (barWidth + barGap / series)
                    drawRoundRect(
                        color = seriesColors.getOrElse(s) { Color.Gray },
                        topLeft = androidx.compose.ui.geometry.Offset(left, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            groups.forEach { g ->
                Text(
                    g.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

data class BarGroup(val label: String, val values: List<Long>)

/** Smooth sparkline for cash-flow trend; value scaling is computed here, never hardcoded. */
@Composable
fun LineChart(
    values: List<Long>,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 90.dp,
    fillBelow: Boolean = true,
) {
    val progress = animateFloatAsState(1f, tween(600), label = "line")
    val minV = (values.minOrNull() ?: 0L).coerceAtMost(0L)
    val maxV = (values.maxOrNull() ?: 1L).coerceAtLeast(minV + 1)
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) return@Canvas
        val stepX = size.width / (values.size - 1)
        fun yFor(v: Long): Float {
            val t = (v - minV).toFloat() / (maxV - minV).toFloat()
            return size.height - 6.dp.toPx() - t * (size.height - 12.dp.toPx())
        }
        val path = Path()
        val fillPath = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = yFor(v)
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo((values.size - 1) * stepX, size.height)
        fillPath.close()
        if (fillBelow) {
            drawPath(fillPath, color.copy(alpha = 0.10f * progress.value))
        }
        val clipped = Path()
        clipped.addPath(path)
        drawPath(
            clipped,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            alpha = progress.value,
        )
        // zero baseline
        drawLine(
            color = Color(0xFFE3E0D6),
            start = androidx.compose.ui.geometry.Offset(0f, yFor(0)),
            end = androidx.compose.ui.geometry.Offset(size.width, yFor(0)),
            strokeWidth = 1f,
        )
    }
}

/** Progress ring for "how much of the debt is already paid". */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = Color(0xFFE7E5DC),
    strokeWidth: androidx.compose.ui.unit.Dp = 10.dp,
    centerText: String = "",
) {
    val animated = animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val d = min(size.width, size.height) - stroke
            val off = (size.width - d) / 2f
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke),
                size = Size(d, d),
                topLeft = androidx.compose.ui.geometry.Offset(off, off),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated.value,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(d, d),
                topLeft = androidx.compose.ui.geometry.Offset(off, off),
            )
        }
        if (centerText.isNotEmpty()) {
            Text(centerText, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Horizontal category bars (expense breakdown etc.) — label + bar + amount. */
@Composable
fun CategoryBars(
    items: List<Pair<String, Long>>,
    color: Color,
    formatter: (Long) -> String,
    modifier: Modifier = Modifier,
    maxRows: Int = 7,
) {
    val visible = items.take(maxRows)
    val maxV = visible.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((name, value) in visible) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(86.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp),
                ) {
                    val frac = (value.toFloat() / maxV.toFloat()).coerceIn(0.02f, 1f)
                    val animFrac = animateFloatAsState(frac, tween(500), label = "catbar")
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = color.copy(alpha = 0.15f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                        )
                        drawRoundRect(
                            color = color,
                            size = Size(size.width * animFrac.value, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(formatter(value), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
