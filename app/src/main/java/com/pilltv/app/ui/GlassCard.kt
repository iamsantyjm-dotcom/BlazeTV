package com.pilltv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Superficies mate (sólidas, sin transparencia). */
val MatteSurface = Color(0xFF1C1C1F)
val MatteLine = Color(0xFF2C2C31)

/**
 * Superficie mate: color sólido y un borde fino. Si se pasa [tint], la superficie se tiñe
 * levemente con ese color (por ejemplo, el marfil del canal activo) y el borde se aclara.
 * El nombre se conserva para no tocar todas las pantallas.
 */
fun Modifier.glassCard(
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color? = null,
): Modifier {
    val fill = if (tint != null) lerp(MatteSurface, tint, 0.14f) else MatteSurface
    val edge = if (tint != null) lerp(MatteLine, tint, 0.55f) else MatteLine
    return this
        .clip(shape)
        .background(fill)
        .border(1.dp, edge, shape)
}

/** Tarjeta mate con contenido propio. */
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
