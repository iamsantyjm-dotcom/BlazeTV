package com.pilltv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Superficie de cristal para fondos oscuros: velo translúcido que se aclara hacia arriba
 * y un borde con brillo en la parte superior. Sin desenfoque (Haze), para que las listas
 * largas sigan fluidas. Si se pasa [tint], el cristal se tiñe con ese color (por ejemplo, el lavanda del canal activo).
 */
fun Modifier.glassCard(
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color? = null,
): Modifier {
    val base = tint ?: Color.White
    val fill = Brush.verticalGradient(
        listOf(
            base.copy(alpha = if (tint != null) 0.26f else 0.12f),
            base.copy(alpha = if (tint != null) 0.10f else 0.035f),
        )
    )
    val edge = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.28f),
            Color.White.copy(alpha = 0.06f),
        )
    )
    return this
        .clip(shape)
        .background(fill)
        .border(1.dp, edge, shape)
}

/** Tarjeta de cristal con contenido propio. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color? = null,
    padding: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.glassCard(shape, tint).padding(padding)) {
        content()
    }
}
