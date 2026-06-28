package com.aesthetic.gym.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.ui.theme.SurfaceVariant

private data class Region(val muscle: MuscleGroup, val rect: Rect)

/**
 * Stylised front/back body chart. Each muscle group is a tappable region painted with [colorFor].
 */
@androidx.compose.runtime.Composable
fun BodyMap(
    colorFor: (MuscleGroup) -> Color,
    selected: MuscleGroup?,
    onSelect: (MuscleGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val regions = regionsFor(Size(size.width.toFloat(), size.height.toFloat()))
                regions.firstOrNull { it.rect.contains(offset) }?.let { onSelect(it.muscle) }
            }
        }
    ) {
        val halfW = size.width / 2f
        drawSilhouette(0f, halfW, size.height)
        drawSilhouette(halfW, halfW, size.height)

        val regions = regionsFor(size)
        regions.forEach { region ->
            val isSel = region.muscle == selected
            drawRoundRect(
                color = colorFor(region.muscle),
                topLeft = region.rect.topLeft,
                size = region.rect.size,
                cornerRadius = CornerRadius(10f, 10f)
            )
            if (isSel) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = region.rect.topLeft,
                    size = region.rect.size,
                    cornerRadius = CornerRadius(10f, 10f),
                    style = Stroke(width = 4f)
                )
            }
        }
    }
}

private fun DrawScope.drawSilhouette(originX: Float, boxW: Float, boxH: Float) {
    val color = SurfaceVariant
    // head
    drawCircle(color, radius = boxW * 0.075f, center = Offset(originX + boxW * 0.5f, boxH * 0.07f))
    // torso
    drawRoundRect(
        color,
        topLeft = Offset(originX + boxW * 0.29f, boxH * 0.13f),
        size = Size(boxW * 0.42f, boxH * 0.42f),
        cornerRadius = CornerRadius(boxW * 0.08f, boxW * 0.08f)
    )
    // arms
    drawRoundRect(
        color,
        topLeft = Offset(originX + boxW * 0.12f, boxH * 0.15f),
        size = Size(boxW * 0.12f, boxH * 0.34f),
        cornerRadius = CornerRadius(boxW * 0.06f, boxW * 0.06f)
    )
    drawRoundRect(
        color,
        topLeft = Offset(originX + boxW * 0.76f, boxH * 0.15f),
        size = Size(boxW * 0.12f, boxH * 0.34f),
        cornerRadius = CornerRadius(boxW * 0.06f, boxW * 0.06f)
    )
    // legs
    drawRoundRect(
        color,
        topLeft = Offset(originX + boxW * 0.32f, boxH * 0.52f),
        size = Size(boxW * 0.16f, boxH * 0.44f),
        cornerRadius = CornerRadius(boxW * 0.06f, boxW * 0.06f)
    )
    drawRoundRect(
        color,
        topLeft = Offset(originX + boxW * 0.52f, boxH * 0.52f),
        size = Size(boxW * 0.16f, boxH * 0.44f),
        cornerRadius = CornerRadius(boxW * 0.06f, boxW * 0.06f)
    )
}

/** Centered rect helper using fractions of a figure box. */
private fun cRect(
    cx: Float, cy: Float, w: Float, h: Float,
    originX: Float, boxW: Float, boxH: Float
): Rect {
    val centerX = originX + cx * boxW
    val centerY = cy * boxH
    val width = w * boxW
    val height = h * boxH
    return Rect(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2)
}

private fun regionsFor(size: Size): List<Region> {
    val half = size.width / 2f
    val h = size.height
    val front = 0f
    val back = half
    val r = mutableListOf<Region>()

    // ---- FRONT figure ----
    r += Region(MuscleGroup.SHOULDERS, cRect(0.31f, 0.17f, 0.16f, 0.07f, front, half, h))
    r += Region(MuscleGroup.SHOULDERS, cRect(0.69f, 0.17f, 0.16f, 0.07f, front, half, h))
    r += Region(MuscleGroup.CHEST, cRect(0.50f, 0.23f, 0.40f, 0.10f, front, half, h))
    r += Region(MuscleGroup.BICEPS, cRect(0.21f, 0.28f, 0.11f, 0.12f, front, half, h))
    r += Region(MuscleGroup.BICEPS, cRect(0.79f, 0.28f, 0.11f, 0.12f, front, half, h))
    r += Region(MuscleGroup.ABS, cRect(0.50f, 0.37f, 0.22f, 0.14f, front, half, h))
    r += Region(MuscleGroup.FOREARMS, cRect(0.17f, 0.42f, 0.10f, 0.12f, front, half, h))
    r += Region(MuscleGroup.FOREARMS, cRect(0.83f, 0.42f, 0.10f, 0.12f, front, half, h))
    r += Region(MuscleGroup.QUADS, cRect(0.40f, 0.64f, 0.14f, 0.22f, front, half, h))
    r += Region(MuscleGroup.QUADS, cRect(0.60f, 0.64f, 0.14f, 0.22f, front, half, h))

    // ---- BACK figure ----
    r += Region(MuscleGroup.TRAPS, cRect(0.50f, 0.16f, 0.26f, 0.07f, back, half, h))
    r += Region(MuscleGroup.BACK, cRect(0.50f, 0.29f, 0.40f, 0.18f, back, half, h))
    r += Region(MuscleGroup.TRICEPS, cRect(0.21f, 0.28f, 0.11f, 0.12f, back, half, h))
    r += Region(MuscleGroup.TRICEPS, cRect(0.79f, 0.28f, 0.11f, 0.12f, back, half, h))
    r += Region(MuscleGroup.GLUTES, cRect(0.50f, 0.47f, 0.30f, 0.10f, back, half, h))
    r += Region(MuscleGroup.HAMSTRINGS, cRect(0.40f, 0.62f, 0.14f, 0.18f, back, half, h))
    r += Region(MuscleGroup.HAMSTRINGS, cRect(0.60f, 0.62f, 0.14f, 0.18f, back, half, h))
    r += Region(MuscleGroup.CALVES, cRect(0.40f, 0.82f, 0.12f, 0.12f, back, half, h))
    r += Region(MuscleGroup.CALVES, cRect(0.60f, 0.82f, 0.12f, 0.12f, back, half, h))
    return r
}
