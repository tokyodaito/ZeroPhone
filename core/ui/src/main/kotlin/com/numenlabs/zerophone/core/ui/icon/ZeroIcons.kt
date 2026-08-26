package com.numenlabs.zerophone.core.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * ZeroPhone icon set — hand-built 24dp fill vectors in the Material glyph
 * tradition, drawn to stay quiet and geometric at small sizes. No external
 * icon dependency: everything the launcher shows lives here. Icons render
 * with `Icon(...)` tinting, so all fills are plain black.
 */
object ZeroIcons {

    /** Phone receiver — the "Позвонить" quick action. */
    val Call: ImageVector by lazy {
        zeroIcon("Call") {
            moveTo(6.62f, 10.79f)
            curveToRelative(1.44f, 2.83f, 3.76f, 5.14f, 6.59f, 6.59f)
            lineToRelative(2.2f, -2.21f)
            curveToRelative(0.27f, -0.27f, 0.67f, -0.36f, 1.02f, -0.24f)
            curveToRelative(1.12f, 0.37f, 2.33f, 0.57f, 3.57f, 0.57f)
            curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
            verticalLineToRelative(3.5f)
            curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
            curveToRelative(-9.39f, 0f, -17f, -7.61f, -17f, -17f)
            curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
            horizontalLineToRelative(3.5f)
            curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
            curveToRelative(0f, 1.25f, 0.2f, 2.45f, 0.57f, 3.57f)
            curveToRelative(0.11f, 0.35f, 0.03f, 0.74f, -0.25f, 1.02f)
            lineToRelative(-2.2f, 2.2f)
            close()
        }
    }

    /** Speech bubble — the "Написать" quick action. */
    val Message: ImageVector by lazy {
        zeroIcon("Message") {
            moveTo(20f, 2f)
            lineTo(4f, 2f)
            curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
            verticalLineTo(22f)
            lineToRelative(4f, -4f)
            lineTo(20f, 18f)
            curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
            verticalLineTo(4f)
            curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
            close()
        }
    }

    /** Compass needle — the "Доехать" quick action. */
    val Navigate: ImageVector by lazy {
        zeroIcon("Navigate") {
            moveTo(12f, 2f)
            lineTo(4.5f, 20.29f)
            lineToRelative(0.71f, 0.71f)
            lineTo(12f, 18f)
            lineToRelative(6.79f, 3f)
            lineToRelative(0.71f, -0.71f)
            close()
        }
    }

    /** Payment card with magstripe — the "Заплатить" quick action. */
    val Pay: ImageVector by lazy {
        zeroIcon("Pay", {
            // Body (clockwise).
            moveTo(4f, 4f)
            lineTo(20f, 4f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            lineToRelative(0f, 12f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            lineTo(4f, 20f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            lineTo(2f, 6f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
        }, {
            // Magstripe hole (counter-clockwise).
            moveTo(4f, 13f)
            lineTo(4f, 16f)
            lineTo(20f, 16f)
            lineTo(20f, 13f)
            close()
        })
    }

    /** Camera — the "Камера" quick action. */
    val Camera: ImageVector by lazy {
        zeroIcon("Camera", {
            // Body with viewfinder hump.
            moveTo(9f, 2f)
            lineTo(7.17f, 4f)
            lineTo(4f, 4f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(12f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(6f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineToRelative(-3.17f)
            lineTo(15f, 2f)
            close()
        }, {
            // Lens ring: outer circle clockwise…
            moveTo(12f, 6.8f)
            arcToRelative(5.2f, 5.2f, 0f, false, true, 0f, 10.4f)
            arcToRelative(5.2f, 5.2f, 0f, false, true, 0f, -10.4f)
            close()
            // …inner circle counter-clockwise (punches the aperture).
            moveTo(12f, 9.1f)
            arcToRelative(2.9f, 2.9f, 0f, false, false, 0f, 5.8f)
            arcToRelative(2.9f, 2.9f, 0f, false, false, 0f, -5.8f)
            close()
        })
    }

    /** Warning triangle with exclamation — Device Owner alerts. */
    val Warning: ImageVector by lazy {
        zeroIcon("Warning", {
            moveTo(1f, 21f)
            lineTo(12f, 2f)
            lineTo(23f, 21f)
            close()
            // Exclamation bar and dot as holes (clockwise).
            moveTo(11f, 9.5f)
            lineTo(13f, 9.5f)
            lineTo(13f, 16f)
            lineTo(11f, 16f)
            close()
            moveTo(12f, 17.25f)
            arcToRelative(1.25f, 1.25f, 0f, false, true, 0f, 2.5f)
            arcToRelative(1.25f, 1.25f, 0f, false, true, 0f, -2.5f)
            close()
        })
    }

    /** Shield with a check — Device Owner active. */
    val ShieldCheck: ImageVector by lazy {
        zeroIcon("ShieldCheck") {
            moveTo(12f, 1f)
            lineTo(3f, 5f)
            verticalLineToRelative(6f)
            curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
            curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f)
            verticalLineTo(5f)
            lineToRelative(-9f, -4f)
            close()
            moveToRelative(-2f, 16f)
            lineToRelative(-4f, -4f)
            lineToRelative(1.41f, -1.41f)
            lineTo(10f, 14.17f)
            lineToRelative(6.59f, -6.59f)
            lineTo(18f, 9f)
            lineToRelative(-9f, 8f)
            close()
        }
    }

    /** Calendar with a marked day — next event. */
    val Event: ImageVector by lazy {
        zeroIcon("Event") {
            moveTo(17f, 12f)
            horizontalLineToRelative(-5f)
            verticalLineToRelative(5f)
            horizontalLineToRelative(5f)
            verticalLineToRelative(-5f)
            close()
            moveTo(16f, 1f)
            verticalLineToRelative(2f)
            horizontalLineTo(8f)
            verticalLineTo(1f)
            horizontalLineTo(6f)
            verticalLineToRelative(2f)
            horizontalLineTo(5f)
            curveToRelative(-1.11f, 0f, -1.99f, 0.9f, -1.99f, 2f)
            lineTo(3f, 19f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(5f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineToRelative(-1f)
            verticalLineTo(1f)
            horizontalLineToRelative(-2f)
            close()
            moveTo(19f, 19f)
            horizontalLineTo(5f)
            verticalLineTo(8f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(11f)
            close()
        }
    }

    /** Bell — important unread notifications. */
    val Notifications: ImageVector by lazy {
        zeroIcon("Notifications") {
            moveTo(12f, 22f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            horizontalLineToRelative(-4f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            close()
            moveTo(18f, 16f)
            verticalLineToRelative(-5f)
            curveToRelative(0f, -3.07f, -1.64f, -5.64f, -4.5f, -6.32f)
            lineTo(13.5f, 4f)
            curveToRelative(0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
            reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
            verticalLineToRelative(0.68f)
            curveToRelative(-2.87f, 0.68f, -4.5f, 3.24f, -4.5f, 6.32f)
            verticalLineToRelative(5f)
            lineToRelative(-2f, 2f)
            verticalLineToRelative(1f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(-1f)
            lineToRelative(-2f, -2f)
            close()
        }
    }

    /** Circle with a check — tasks. */
    val TaskAlt: ImageVector by lazy {
        zeroIcon("TaskAlt") {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
            reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
            close()
            moveTo(10f, 17f)
            lineToRelative(-5f, -5f)
            lineToRelative(1.41f, -1.41f)
            lineTo(10f, 14.17f)
            lineToRelative(7.59f, -7.59f)
            lineTo(19f, 8f)
            lineToRelative(-9f, 9f)
            close()
        }
    }

    /** Plus — adding a task. */
    val Add: ImageVector by lazy {
        zeroIcon("Add") {
            moveTo(19f, 13f)
            horizontalLineToRelative(-6f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-6f)
            horizontalLineToRelative(-6f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(-6f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(6f)
            close()
        }
    }

    /** Stopwatch — the emergency unlock window. */
    val Timer: ImageVector by lazy {
        zeroIcon("Timer", {
            moveTo(15f, 1f)
            horizontalLineTo(9f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(6f)
            verticalLineTo(1f)
            close()
            moveTo(11f, 14f)
            horizontalLineToRelative(2f)
            verticalLineTo(8f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(6f)
            close()
        }, {
            moveTo(19.03f, 7.39f)
            lineToRelative(1.42f, -1.42f)
            curveToRelative(-0.43f, -0.51f, -0.9f, -0.99f, -1.41f, -1.41f)
            lineToRelative(-1.42f, 1.42f)
            curveToRelative(-1.55f, -1.24f, -3.5f, -1.98f, -5.62f, -1.98f)
            curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
            reflectiveCurveToRelative(4.02f, 9f, 9f, 9f)
            reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
            curveToRelative(0f, -2.12f, -0.74f, -4.07f, -1.97f, -5.61f)
            close()
            moveTo(12f, 20f)
            curveToRelative(-3.87f, 0f, -7f, -3.13f, -7f, -7f)
            reflectiveCurveToRelative(3.13f, -7f, 7f, -7f)
            reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
            reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
            close()
        })
    }

    /** Magnifier — allowlist search. */
    val Search: ImageVector by lazy {
        zeroIcon("Search") {
            moveTo(15.5f, 14f)
            horizontalLineToRelative(-0.79f)
            lineToRelative(-0.28f, -0.27f)
            curveToRelative(0.98f, -1.14f, 1.57f, -2.62f, 1.57f, -4.23f)
            curveToRelative(0f, -3.59f, -2.91f, -6.5f, -6.5f, -6.5f)
            reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
            reflectiveCurveToRelative(2.91f, 6.5f, 6.5f, 6.5f)
            curveToRelative(1.61f, 0f, 3.09f, -0.59f, 4.23f, -1.57f)
            lineToRelative(0.27f, 0.28f)
            verticalLineToRelative(0.79f)
            lineToRelative(5f, 4.99f)
            lineTo(20.49f, 19f)
            lineToRelative(-4.99f, -5f)
            close()
            moveTo(9.5f, 14f)
            curveToRelative(-2.49f, 0f, -4.5f, -2.01f, -4.5f, -4.5f)
            reflectiveCurveTo(7.01f, 5f, 9.5f, 5f)
            reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
            reflectiveCurveTo(11.99f, 14f, 9.5f, 14f)
            close()
        }
    }

    /** Back arrow — allowlist navigation. */
    val ArrowBack: ImageVector by lazy {
        zeroIcon("ArrowBack") {
            moveTo(20f, 11f)
            horizontalLineTo(7.83f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12f, 4f)
            lineToRelative(-8f, 8f)
            lineToRelative(8f, 8f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13f)
            horizontalLineTo(20f)
            verticalLineToRelative(-2f)
            close()
        }
    }

    /** App grid — the allowlist app set. */
    val AppsGrid: ImageVector by lazy {
        zeroIcon("AppsGrid") {
            addSquare(4f, 4f)
            addSquare(10f, 4f)
            addSquare(16f, 4f)
            addSquare(4f, 10f)
            addSquare(10f, 10f)
            addSquare(16f, 10f)
            addSquare(4f, 16f)
            addSquare(10f, 16f)
            addSquare(16f, 16f)
        }
    }
}

private fun zeroIcon(
    name: String,
    vararg paths: PathBuilder.() -> Unit
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        paths.forEach { path(fill = SolidColor(Color.Black), pathBuilder = it) }
    }.build()

private fun zeroIcon(
    name: String,
    path: PathBuilder.() -> Unit
): ImageVector = zeroIcon(name, *arrayOf(path))

private fun PathBuilder.addSquare(x: Float, y: Float) {
    moveTo(x, y)
    horizontalLineToRelative(4f)
    verticalLineToRelative(4f)
    horizontalLineToRelative(-4f)
    verticalLineToRelative(-4f)
    close()
}
