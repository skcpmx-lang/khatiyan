package com.shohan.khatiyan.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Three icons the app needs that the (now-deprecated) compose material icons artifacts
 * do not reliably expose in the versions the compose BOM resolves. Drawn with the official
 * 24dp Material path data so they look native; zero extra dependencies.
 */
object KhatiyanIcons {

    /** Magnifier — "search". */
    val Search: ImageVector by lazy {
        build("search") {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                // circle ring
                moveTo(15.5f, 14f)
                lineToRelative(0f, -0.79f)
                lineToRelative(-0.28f, -0.28f)
                curveToRelative(1.12f, -1.19f, 1.78f, -2.75f, 1.78f, -4.43f)
                curveToRelative(0f, -3.65f, -2.95f, -6.5f, -6.5f, -6.5f)
                curveToRelative(-3.53f, 0f, -6.5f, 2.85f, -6.5f, 6.5f)
                curveToRelative(0f, 3.53f, 2.97f, 6.5f, 6.5f, 6.5f)
                curveToRelative(1.61f, 0f, 3.09f, -0.59f, 4.23f, -1.57f)
                lineToRelative(0.27f, 0.28f)
                lineToRelative(0f, 0.79f)
                lineToRelative(5f, 4.99f)
                lineToRelative(1.49f, -1.5f)
                lineToRelative(-4.99f, -5f)
                close()
                moveTo(9.5f, 14f)
                curveToRelative(-2.48f, 0f, -4.5f, -2.02f, -4.5f, -4.5f)
                curveToRelative(0f, -2.48f, 2.02f, -4.5f, 4.5f, -4.5f)
                curveToRelative(2.48f, 0f, 4.5f, 2.02f, 4.5f, 4.5f)
                curveTo(14f, 11.98f, 11.98f, 14f, 9.5f, 14f)
                close()
            }
        }
    }

    /** Rising arrow — net cash flow positive. */
    val TrendingUp: ImageVector by lazy {
        build("trending_up") {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(16f, 6f)
                lineToRelative(2.29f, 2.29f)
                lineToRelative(-4.88f, 4.88f)
                lineToRelative(-4f, -4f)
                lineTo(2f, 16.59f)
                lineTo(3.41f, 18f)
                lineToRelative(6f, -6f)
                lineToRelative(4f, 4f)
                lineToRelative(6.3f, -6.29f)
                lineTo(22f, 12f)
                lineTo(22f, 6f)
                close()
            }
        }
    }

    /** Falling arrow — net cash flow negative. */
    val TrendingDown: ImageVector by lazy {
        build("trending_down") {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(16f, 18f)
                lineToRelative(2.29f, -2.29f)
                lineToRelative(-4.88f, -4.88f)
                lineToRelative(-4f, 4f)
                lineTo(2f, 7.41f)
                lineTo(3.41f, 6f)
                lineToRelative(6f, 6f)
                lineToRelative(4f, -4f)
                lineToRelative(6.3f, 6.29f)
                lineTo(22f, 12f)
                lineToRelative(0f, 6f)
                close()
            }
        }
    }

    private inline fun build(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()
}
