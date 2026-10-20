package com.pilltv.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Elemento tocable y enfocable con el control remoto (D-pad / OK).
 *
 * Con el foco, el elemento crece un poco con una transición suave y se dibuja un borde lavanda
 * con la forma [shape] (usa CircleShape para botones redondos). Sin rebotes ni desenfoque.
 */
@Composable
fun Modifier.pressClick(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "focusScale",
    )
    return this
        .tvEntry(enabled)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
        .drawWithContent {
            drawContent()
            if (focused) {
                // Halo lavanda suave por fuera y un borde blanco grueso por dentro: se ve bien en TV.
                drawFocusOutline(shape, Stroke(width = 16.dp.toPx()), Lavender.copy(alpha = 0.45f))
                drawFocusOutline(shape, Stroke(width = 4.dp.toPx()), Color.White)
            }
        }
}

private fun DrawScope.drawFocusOutline(shape: Shape, stroke: Stroke, color: Color) {
    when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rectangle -> drawRect(
            color = color,
            topLeft = outline.rect.topLeft,
            size = outline.rect.size,
            style = stroke,
        )
        is Outline.Rounded -> drawRoundRect(
            color = color,
            topLeft = Offset(outline.roundRect.left, outline.roundRect.top),
            size = Size(outline.roundRect.width, outline.roundRect.height),
            cornerRadius = CornerRadius(
                outline.roundRect.topLeftCornerRadius.x,
                outline.roundRect.topLeftCornerRadius.y,
            ),
            style = stroke,
        )
        is Outline.Generic -> drawPath(path = outline.path, color = color, style = stroke)
    }
}

/**
 * Indicador de foco bien visible para botones y campos de Material (Button, TextButton,
 * IconButton, TextField) que no usan [pressClick]: halo de color grueso + borde blanco.
 * Ponlo en el `modifier` del componente. En campos de texto usa scale = false.
 */
@Composable
fun Modifier.focusRing(
    shape: Shape = RoundedCornerShape(percent = 50),
    scale: Boolean = true,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(
        targetValue = if (focused && scale) 1.08f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "ringScale",
    )
    return this
        .tvEntry()
        .onFocusChanged { focused = it.hasFocus }
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .drawWithContent {
            drawContent()
            if (focused) {
                drawFocusOutline(shape, Stroke(width = 16.dp.toPx()), Lavender.copy(alpha = 0.55f))
                drawFocusOutline(shape, Stroke(width = 4.dp.toPx()), Color.White)
            }
        }
}
