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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
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
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline = outline, color = Lavender, style = Stroke(width = 3.dp.toPx()))
            }
        }
}
