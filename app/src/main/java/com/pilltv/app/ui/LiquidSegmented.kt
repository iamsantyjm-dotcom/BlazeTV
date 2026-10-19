package com.pilltv.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Selector de dos o más opciones. Una marca de color se desliza hacia la opción elegida
 * con una transición corta y suave (250 ms). Cada opción se enfoca con el control remoto.
 */
@Composable
fun LiquidSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = options.size
    val lefts = remember(n) { List(n) { 0f }.toMutableStateList() }
    val widths = remember(n) { List(n) { 0f }.toMutableStateList() }

    val targetLeft = lefts[selected]
    val targetWidth = widths[selected]
    val measured = targetWidth > 0f

    // La primera vez la marca se coloca de golpe, sin deslizarse desde el borde.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(measured) {
        if (measured) {
            withFrameNanos { }
            settled = true
        }
    }

    val slide: FiniteAnimationSpec<Float> =
        if (settled) tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing) else snap<Float>()
    val left by animateFloatAsState(targetLeft, slide, label = "marcaIzqSeg")
    val right by animateFloatAsState(targetLeft + targetWidth, slide, label = "marcaDerSeg")

    Row(
        modifier = modifier
            .clip(CircleShape)
            .glassCard(CircleShape)
            .padding(4.dp)
            .drawMarca(measured, left, right),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val tint by animateColorAsState(
                if (on) Ink else TextHi,
                tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "segTint",
            )
            Box(
                Modifier
                    .onGloballyPositioned {
                        val x = it.positionInParent().x
                        val w = it.size.width.toFloat()
                        if (lefts[i] != x) lefts[i] = x
                        if (widths[i] != w) widths[i] = w
                    }
                    .pressClick(shape = CircleShape) { onSelect(i) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label,
                    color = tint,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Dibuja la marca detrás de las opciones, con la forma de píldora y sin deformarse. */
private fun Modifier.drawMarca(measured: Boolean, left: Float, right: Float): Modifier =
    this.drawBehind {
        if (!measured) return@drawBehind
        val w = (right - left).coerceAtLeast(0f)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFFFFB36B), Lavender),
                startX = left,
                endX = left + w,
            ),
            topLeft = Offset(left, 0f),
            size = Size(w, size.height),
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
