package com.pilltv.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pilltv.app.data.Channel

/** Fondo con dos halos de color que cambian según la pestaña. */
@Composable
fun AppBackground(primary: Color = Lavender, secondary: Color = Azure) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .drawBehind {
                val c1 = Offset(size.width * 0.95f, size.height * 0.05f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.38f), Color.Transparent),
                        center = c1,
                        radius = size.width * 0.95f,
                    ),
                    radius = size.width * 0.95f,
                    center = c1,
                )
                val c2 = Offset(size.width * 0.05f, size.height * 0.62f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondary.copy(alpha = 0.30f), Color.Transparent),
                        center = c2,
                        radius = size.width * 0.9f,
                    ),
                    radius = size.width * 0.9f,
                    center = c2,
                )
            }
    )
}

/** Cápsula de cristal: velo claro y borde fino. Sin desenfoque, para que sea fluida en TV. */
fun Modifier.glassPill(shape: Shape = CircleShape): Modifier =
    this
        .clip(shape)
        .background(Color.White.copy(alpha = 0.10f))
        .border(1.dp, Color.White.copy(alpha = 0.20f), shape)

@Composable
fun ChannelLogo(channel: Channel, size: Dp, modifier: Modifier = Modifier) {
    var failed by remember(channel.logo) { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (channel.logo != null && !failed) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.1f),
            )
        } else {
            Text(
                text = channel.name.take(1).uppercase(),
                color = Lavender,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
