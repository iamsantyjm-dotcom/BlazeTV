package com.pilltv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilltv.app.data.EpgNow
import com.pilltv.app.data.EpgProgram
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun clockFormat(): SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun range(p: EpgProgram, fmt: SimpleDateFormat): String =
    fmt.format(Date(p.startMs)) + "–" + fmt.format(Date(p.endMs))

/**
 * Las dos líneas de la guía: lo que está al aire (con barra de avance) y lo que sigue.
 * Si [epg] es null todavía se está consultando al servidor.
 */
@Composable
internal fun GuideLines(
    epg: EpgNow?,
    nowMs: Long,
    fmt: SimpleDateFormat,
    modifier: Modifier = Modifier,
) {
    val now = epg?.now
    val next = epg?.next
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (epg == null) {
            Text("Cargando guía…", color = TextLo, fontSize = 12.sp)
        } else if (now == null && next == null) {
            Text("Sin información de programación", color = TextLo, fontSize = 12.sp)
        } else {
            if (now != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("AHORA", color = Lavender, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = now.title,
                        color = TextHi,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(range(now, fmt), color = TextLo, fontSize = 11.sp, maxLines = 1)
                }
                val total = (now.endMs - now.startMs).coerceAtLeast(1L)
                val done = ((nowMs - now.startMs).toFloat() / total.toFloat()).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(done)
                            .height(3.dp)
                            .background(Lavender)
                    )
                }
            }
            if (next != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SIGUE", color = TextLo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = next.title,
                        color = TextLo,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(fmt.format(Date(next.startMs)), color = TextLo, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}
