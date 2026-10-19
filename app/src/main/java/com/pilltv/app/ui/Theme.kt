package com.pilltv.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// Paleta tomada del icono de la llama: fondo morado-carbón, naranja y rosa de la llama, violeta del borde.
// Los nombres (Lavender, Azure, Coral) se conservaron para no tocar todas las pantallas; hoy son:
// Lavender = naranja de la llama, Azure = violeta, Coral = rosa.
val Ink = Color(0xFF0C0912)
val InkSoft = Color(0xFF1C1526)
val Lavender = Color(0xFFFF9A4D)
val Azure = Color(0xFF9B6BFF)
val Coral = Color(0xFFFF5E8A)
val TextHi = Color(0xFFFBF6F2)
val TextLo = Color(0xFFBDB2CC)

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
