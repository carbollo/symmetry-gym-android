package com.aesthetic.gym.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aesthetic.gym.ui.theme.Accent

/** Minimal dependency-free line chart for progress trends. */
@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Accent
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        if (values.size < 2) return@Canvas
        val maxV = values.max()
        val minV = values.min()
        val range = (maxV - minV).takeIf { it > 0f } ?: 1f
        val padX = 12f
        val padY = 18f
        val w = size.width - padX * 2
        val h = size.height - padY * 2
        val stepX = w / (values.size - 1)

        val points = values.mapIndexed { i, v ->
            val x = padX + i * stepX
            val y = padY + h - ((v - minV) / range) * h
            Offset(x, y)
        }

        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(points.last().x, padY + h)
            lineTo(points.first().x, padY + h)
            close()
        }
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.25f), Color.Transparent)
            )
        )
        drawPath(linePath, color = lineColor, style = Stroke(width = 4f))
        points.forEach { drawCircle(lineColor, radius = 5f, center = it) }
    }
}
