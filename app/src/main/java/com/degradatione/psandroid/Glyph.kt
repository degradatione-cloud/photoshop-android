package com.degradatione.psandroid

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Векторные иконки инструментов — рисуются путями, а не буквами:
 * на маленьком экране так читаемее и масштабируется без пикселей.
 */
@Composable
fun ToolGlyph(tool: PsTool, tint: Color, size: Dp = 20.dp, filled: Boolean = false) {
    Canvas(Modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val w = 1.9f * u
        val st = Stroke(width = w, cap = StrokeCap.Round)
        val c = tint
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(c, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), strokeWidth = w, cap = StrokeCap.Round)

        when (tool) {
            PsTool.MOVE -> {
                line(12f, 3f, 12f, 21f)
                line(3f, 12f, 21f, 12f)
                arrow(12f, 2.5f, 0f, -1f, c, u)
                arrow(12f, 21.5f, 0f, 1f, c, u)
                arrow(2.5f, 12f, -1f, 0f, c, u)
                arrow(21.5f, 12f, 1f, 0f, c, u)
            }
            PsTool.BRUSH -> {
                line(18.5f, 5.5f, 11f, 13f)
                val p = Path().apply {
                    moveTo(11f * u, 12f * u)
                    lineTo(7.5f * u, 19.5f * u)
                    lineTo(13f * u, 15.5f * u)
                    close()
                }
                drawPath(p, c, style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else st)
            }
            PsTool.ERASER -> {
                val p = Path().apply {
                    moveTo(6f * u, 15f * u)
                    lineTo(12f * u, 7f * u)
                    lineTo(19f * u, 12.5f * u)
                    lineTo(13f * u, 20.5f * u)
                    close()
                }
                drawPath(p, c, style = st)
                line(9f, 21f, 20f, 21f)
            }
            PsTool.FILL -> {
                val p = Path().apply {
                    moveTo(5f * u, 12f * u)
                    lineTo(12f * u, 5f * u)
                    lineTo(19f * u, 12f * u)
                    lineTo(12f * u, 19f * u)
                    close()
                }
                drawPath(p, c, style = st)
                line(9f, 8f, 9f, 5f)
                drawCircle(c, radius = 2.2f * u, center = Offset(20f * u, 18f * u))
            }
            PsTool.EYEDROPPER -> {
                line(5f, 19f, 13f, 11f)
                line(12f, 7f, 17f, 12f)
                drawCircle(c, radius = 2.4f * u, center = Offset(17.5f * u, 6.5f * u), style = st)
                line(4f, 20f, 7f, 17f)
            }
            PsTool.CROP -> {
                line(8f, 3f, 8f, 16f)
                line(8f, 16f, 21f, 16f)
                line(3f, 8f, 16f, 8f)
                line(16f, 8f, 16f, 21f)
            }
            PsTool.TEXT -> {
                line(5f, 6f, 19f, 6f)
                line(12f, 6f, 12f, 19f)
                line(9f, 19f, 15f, 19f)
            }
            PsTool.ZOOM -> {
                drawCircle(c, radius = 6f * u, center = Offset(10f * u, 10f * u), style = st)
                line(14.5f, 14.5f, 20f, 20f)
                line(7.5f, 10f, 12.5f, 10f)
                line(10f, 7.5f, 10f, 12.5f)
            }
        }
    }
}

private fun DrawScope.arrow(x: Float, y: Float, dx: Float, dy: Float, c: Color, u: Float) {
    val p = Path().apply {
        if (dy != 0f) {
            moveTo(x * u, y * u)
            lineTo((x - 2.4f) * u, (y - dy * 2.6f) * u)
            lineTo((x + 2.4f) * u, (y - dy * 2.6f) * u)
        } else {
            moveTo(x * u, y * u)
            lineTo((x - dx * 2.6f) * u, (y - 2.4f) * u)
            lineTo((x - dx * 2.6f) * u, (y + 2.4f) * u)
        }
        close()
    }
    drawPath(p, c)
}

/** Пружинный «тап» как в FlClash: элемент слегка проседает под пальцем. */
@Composable
fun TapBox(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scaleTo: Float = 0.92f,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleTo else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tapScale",
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Всплывающая плашка статуса (вместо тостов, которые на телефоне не читают). */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    val accent = if (error) Ps.danger else Ps.blue
    Row(
        modifier
            .background(Ps.panel.copy(alpha = 0.96f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(accent, RoundedCornerShape(3.dp)))
        androidx.compose.foundation.layout.Spacer(Modifier.size(7.dp))
        Text(text, color = Ps.text, fontSize = 11.sp, maxLines = 2)
    }
}
