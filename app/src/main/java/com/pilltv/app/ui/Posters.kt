package com.pilltv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Carátula con título. Sirve para películas, series y la fila "Continuar viendo".
 * Es enfocable con el control remoto; el botón de favorito también se puede enfocar aparte.
 */
@Composable
fun PosterCard(
    title: String,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float = 0f,
    watched: Boolean = false,
    favorite: Boolean? = null,
    onFavorite: (() -> Unit)? = null,
) {
    val posterShape = RoundedCornerShape(16.dp)
    var failed by remember(image) { mutableStateOf(false) }
    Column(
        modifier = modifier.pressClick(shape = posterShape, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(posterShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            if (image != null && !failed) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = title.take(1).uppercase(),
                    color = Lavender,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // Título y año dentro de la propia imagen, sobre un degradado oscuro en la parte de abajo.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                        )
                    )
                    .padding(start = 10.dp, end = 10.dp, top = 48.dp, bottom = 14.dp),
            ) {
                Column {
                    Text(
                        text = title,
                        color = TextHi,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(text = subtitle, color = TextLo, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            if (favorite != null && onFavorite != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .pressClick(shape = CircleShape, onClick = onFavorite),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (favorite) "Quitar de favoritos" else "Agregar a favoritos",
                        tint = if (favorite) Coral else Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (watched) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Visto",
                    tint = Lavender,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(22.dp),
                )
            }
            if (!watched && progress > 0f) {
                ProgressLine(
                    fraction = progress,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/** Barra fina de avance (0 a 1). */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.Black.copy(alpha = 0.55f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(Lavender)
        )
    }
}
