package com.aesthetic.gym.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.ui.theme.SurfaceVariant

enum class BodySide { FRONT, BACK }

private data class Region(val muscle: MuscleGroup, val rect: Rect)

/**
 * Stylised human body chart for a single side (front or back).
 * Each muscle group is a tappable region painted with [colorFor].
 */
@Composable
fun BodyMap(
    side: BodySide,
    colorFor: (MuscleGroup) -> Color,
    selected: MuscleGroup?,
    onSelect: (MuscleGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(side) {
            detectTapGestures { offset ->
                val regions = regionsFor(Size(size.width.toFloat(), size.height.toFloat()), side)
                regions.firstOrNull { it.rect.contains(offset) }?.let { onSelect(it.muscle) }
            }
        }
    ) {
        drawBody(size.width, size.height)

        regionsFor(size, side).forEach { region ->
            val r = region.rect
            drawOval(color = colorFor(region.muscle), topLeft = r.topLeft, size = r.size)
            if (region.muscle == selected) {
                drawOval(color = Color.White, topLeft = r.topLeft, size = r.size, style = Stroke(width = 3f))
            }
        }
    }
}

/** Smooth human silhouette filling the whole canvas. */
private fun DrawScope.drawBody(w: Float, h: Float) {
    val color = SurfaceVariant
    val cx = w / 2f

    // Legs
    capsule(color, cx - 0.085f * w, 0.49f * h, 0.13f * w, 0.47f * h)
    capsule(color, cx + 0.085f * w, 0.49f * h, 0.13f * w, 0.47f * h)
    // Arms
    capsule(color, cx - 0.225f * w, 0.175f * h, 0.085f * w, 0.30f * h)
    capsule(color, cx + 0.225f * w, 0.175f * h, 0.085f * w, 0.30f * h)

    // Torso: shoulders -> waist -> hips
    val sx = 0.20f * w
    val wx = 0.115f * w
    val hx = 0.17f * w
    val torso = Path().apply {
        moveTo(cx - sx, 0.17f * h)
        cubicTo(cx - sx, 0.27f * h, cx - wx, 0.32f * h, cx - wx, 0.40f * h)
        cubicTo(cx - wx, 0.45f * h, cx - hx, 0.47f * h, cx - hx, 0.52f * h)
        lineTo(cx + hx, 0.52f * h)
        cubicTo(cx + hx, 0.47f * h, cx + wx, 0.45f * h, cx + wx, 0.40f * h)
        cubicTo(cx + wx, 0.32f * h, cx + sx, 0.27f * h, cx + sx, 0.17f * h)
        close()
    }
    drawPath(torso, color)

    // Deltoids, neck and head
    drawCircle(color, radius = 0.07f * w, center = Offset(cx - 0.18f * w, 0.185f * h))
    drawCircle(color, radius = 0.07f * w, center = Offset(cx + 0.18f * w, 0.185f * h))
    capsule(color, cx, 0.115f * h, 0.075f * w, 0.07f * h)
    drawCircle(color, radius = 0.078f * w, center = Offset(cx, 0.075f * h))
}

private fun DrawScope.capsule(color: Color, centerX: Float, top: Float, width: Float, height: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(centerX - width / 2f, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(width / 2f, width / 2f)
    )
}

private fun cOval(cx: Float, cy: Float, w: Float, h: Float, boxW: Float, boxH: Float): Rect {
    val centerX = cx * boxW
    val centerY = cy * boxH
    val width = w * boxW
    val height = h * boxH
    return Rect(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2)
}

private fun regionsFor(size: Size, side: BodySide): List<Region> {
    val w = size.width
    val h = size.height
    val r = mutableListOf<Region>()

    if (side == BodySide.FRONT) {
        r += Region(MuscleGroup.SHOULDERS, cOval(0.31f, 0.185f, 0.15f, 0.09f, w, h))
        r += Region(MuscleGroup.SHOULDERS, cOval(0.69f, 0.185f, 0.15f, 0.09f, w, h))
        r += Region(MuscleGroup.CHEST, cOval(0.41f, 0.245f, 0.18f, 0.10f, w, h))
        r += Region(MuscleGroup.CHEST, cOval(0.59f, 0.245f, 0.18f, 0.10f, w, h))
        r += Region(MuscleGroup.BICEPS, cOval(0.265f, 0.30f, 0.10f, 0.13f, w, h))
        r += Region(MuscleGroup.BICEPS, cOval(0.735f, 0.30f, 0.10f, 0.13f, w, h))
        r += Region(MuscleGroup.ABS, cOval(0.50f, 0.37f, 0.17f, 0.15f, w, h))
        r += Region(MuscleGroup.FOREARMS, cOval(0.245f, 0.44f, 0.085f, 0.13f, w, h))
        r += Region(MuscleGroup.FOREARMS, cOval(0.755f, 0.44f, 0.085f, 0.13f, w, h))
        r += Region(MuscleGroup.QUADS, cOval(0.415f, 0.66f, 0.13f, 0.22f, w, h))
        r += Region(MuscleGroup.QUADS, cOval(0.585f, 0.66f, 0.13f, 0.22f, w, h))
    } else {
        r += Region(MuscleGroup.TRAPS, cOval(0.50f, 0.20f, 0.24f, 0.09f, w, h))
        r += Region(MuscleGroup.BACK, cOval(0.50f, 0.31f, 0.34f, 0.17f, w, h))
        r += Region(MuscleGroup.TRICEPS, cOval(0.265f, 0.30f, 0.10f, 0.13f, w, h))
        r += Region(MuscleGroup.TRICEPS, cOval(0.735f, 0.30f, 0.10f, 0.13f, w, h))
        r += Region(MuscleGroup.GLUTES, cOval(0.41f, 0.50f, 0.16f, 0.11f, w, h))
        r += Region(MuscleGroup.GLUTES, cOval(0.59f, 0.50f, 0.16f, 0.11f, w, h))
        r += Region(MuscleGroup.HAMSTRINGS, cOval(0.415f, 0.66f, 0.13f, 0.18f, w, h))
        r += Region(MuscleGroup.HAMSTRINGS, cOval(0.585f, 0.66f, 0.13f, 0.18f, w, h))
        r += Region(MuscleGroup.CALVES, cOval(0.415f, 0.85f, 0.10f, 0.12f, w, h))
        r += Region(MuscleGroup.CALVES, cOval(0.585f, 0.85f, 0.10f, 0.12f, w, h))
    }
    return r
}
