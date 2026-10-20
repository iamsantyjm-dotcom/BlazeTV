package com.pilltv.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// Paleta mate: carbón neutro y un solo acento marfil. Sin transparencias ni brillos.
// Los nombres (Lavender, Azure, Coral) se conservaron para no tocar todas las pantallas; hoy son:
// Lavender = acento marfil, Azure = gris azulado suave, Coral = rojo apagado (errores).
val Ink = Color(0xFF111113)
val InkSoft = Color(0xFF1C1C1F)
val Lavender = Color(0xFFE8E3DA)
val Azure = Color(0xFF8E96A3)
val Coral = Color(0xFFE0766F)
val TextHi = Color(0xFFF2F0EC)
val TextLo = Color(0xFF9A9AA2)

private val Scheme = darkColorScheme(
    primary = Lavender,
    onPrimary = Ink,
    secondary = Azure,
    background = Ink,
    onBackground = TextHi,
    surface = InkSoft,
    onSurface = TextHi,
    onSurfaceVariant = TextLo,
    error = Coral,
)

@Composable
fun PillTheme(content: @Composable () -> Unit) {
    // En TV se lee a distancia: el texto es un 15 % más grande que en el teléfono.
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.15f)) {
        MaterialTheme(colorScheme = Scheme, content = content)
    }
}
