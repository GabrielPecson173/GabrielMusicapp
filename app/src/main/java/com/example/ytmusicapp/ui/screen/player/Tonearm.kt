package com.example.ytmusicapp.ui.screen.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.sqrt

// Rotation angles (degrees) around the pivot point.
// Base geometry at 0° points the tip toward disc center.
// RESTING_ANGLE rotates arm clockwise so needle parks outside the disc edge.
// PLAYING_ANGLE rotates arm counter-clockwise onto the groove (~60% radius).
private const val PLAYING_ANGLE = 0f
private const val RESTING_ANGLE = 32f

@Composable
internal fun Tonearm(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val angle = remember { Animatable(if (isPlaying) PLAYING_ANGLE else RESTING_ANGLE) }

    LaunchedEffect(isPlaying) {
        val target = if (isPlaying) PLAYING_ANGLE else RESTING_ANGLE
        angle.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Pivot is fixed top-right, slightly outside disc edge
        val pivotX = w * 0.82f
        val pivotY = h * 0.06f

        withTransform(
            transformBlock = {
                rotate(
                    degrees = angle.value,
                    pivot = Offset(pivotX, pivotY)
                )
            }
        ) {
            drawTonearm(pivotX = pivotX, pivotY = pivotY, w = w, h = h)
        }
    }
}

private fun DrawScope.drawTonearm(pivotX: Float, pivotY: Float, w: Float, h: Float) {
    // In rest (0°) position the arm points inward but parked off the right edge of the disc.
    // When rotated to PLAYING_ANGLE the tip lands on the groove area.
    val tipX   = w * 0.55f
    val tipY   = h * 0.40f
    val elbowX = (pivotX + tipX) / 2f + w * 0.025f
    val elbowY = (pivotY + tipY) / 2f - h * 0.008f

    val silverLight    = Color(0xFFD8D8D8)
    val silverDark     = Color(0xFF6E6E6E)
    val headshellColor = Color(0xFF929292)

    // Drop shadow
    drawLine(
        color = Color(0x55000000),
        start = Offset(pivotX + 2f, pivotY + 2f),
        end   = Offset(elbowX + 2f, elbowY + 2f),
        strokeWidth = 10f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0x55000000),
        start = Offset(elbowX + 2f, elbowY + 2f),
        end   = Offset(tipX + 2f,   tipY + 2f),
        strokeWidth = 7f,
        cap = StrokeCap.Round
    )

    // Upper arm — dark base + light highlight
    drawLine(silverDark,  Offset(pivotX,      pivotY),      Offset(elbowX,      elbowY),      strokeWidth = 11f, cap = StrokeCap.Round)
    drawLine(silverLight, Offset(pivotX - 1f, pivotY - 1f), Offset(elbowX - 1f, elbowY - 1f), strokeWidth = 5f,  cap = StrokeCap.Round)

    // Lower arm
    drawLine(silverDark,  Offset(elbowX,      elbowY),      Offset(tipX,      tipY),      strokeWidth = 8f, cap = StrokeCap.Round)
    drawLine(silverLight, Offset(elbowX - 1f, elbowY - 1f), Offset(tipX - 1f, tipY - 1f), strokeWidth = 4f, cap = StrokeCap.Round)

    // Headshell — extends in the direction of the lower arm
    val dX   = tipX - elbowX
    val dY   = tipY - elbowY
    val dist = sqrt(dX * dX + dY * dY)
    val normX    = dX / dist
    val normY    = dY / dist
    val headEndX = tipX + normX * w * 0.06f
    val headEndY = tipY + normY * w * 0.06f
    drawLine(headshellColor, Offset(tipX, tipY), Offset(headEndX, headEndY), strokeWidth = 10f, cap = StrokeCap.Round)

    // Needle
    drawLine(
        color = Color(0xFF888888),
        start = Offset(headEndX, headEndY),
        end   = Offset(headEndX + 2f, headEndY + h * 0.025f),
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )

    // Pivot base (three circles for depth)
    drawCircle(silverDark,           radius = w * 0.046f, center = Offset(pivotX,      pivotY))
    drawCircle(silverLight,          radius = w * 0.026f, center = Offset(pivotX - 1f, pivotY - 1f))
    drawCircle(Color(0xFF2A2A2A),    radius = w * 0.012f, center = Offset(pivotX,      pivotY))
}