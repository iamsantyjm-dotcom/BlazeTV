package com.pilltv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pilltv.app.MainViewModel
import com.pilltv.app.data.Channel
import com.pilltv.app.data.DetailTarget
import com.pilltv.app.data.Episode
import com.pilltv.app.data.Progress
import com.pilltv.app.data.Series

/** Ficha de una película o de una serie: carátula, año, sinopsis y (en series) temporadas y capítulos. */
@Composable
fun DetailScreen(vm: MainViewModel, target: DetailTarget) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            // Absorbe los toques para que no lleguen a la pantalla que está debajo.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        AppBackground()
        when (target) {
            is DetailTarget.Movie -> MovieDetail(vm, target.channel)
            is DetailTarget.Show -> ShowDetail(vm, target.series)
        }
    }
}

// ---------------------------------------------------------------- Película

@Composable
private fun MovieDetail(vm: MainViewModel, ch: Channel) {
    val infoMap by vm.vodInfo.collectAsStateWithLifecycle()
    val loading by vm.detailLoading.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val info = infoMap[ch.url]
    val p = progress[ch.url]
    val watched = p?.watched == true
    var expanded by remember(ch.url) { mutableStateOf(false) }

    val meta = listOfNotNull(
        info?.year ?: ch.year,
        (info?.rating ?: ch.rating)?.let { "★ $it" },
        prettyDuration(info?.duration),
    ).joinToString("  ·  ")
    val label = if (p != null && !p.watched && p.posMs > 0L) {
        "Continuar desde ${clockText(p.posMs)}"
    } else {
        "Reproducir"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BackBar(vm) }
        item { Header(info?.cover ?: ch.logo, ch.name, meta, info?.genre) }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlayButton(label) { vm.play(ch, listOf(ch)) }
                TextButton(onClick = { vm.setWatched(ch, !watched) }) {
                    Text(if (watched) "Marcar como no vista" else "Marcar como vista", color = TextLo)
                }
            }
        }
        item { Plot(info?.plot, loading, expanded) { expanded = !expanded } }
        val credits = listOfNotNull(
            info?.director?.let { "Dirección: $it" },
            info?.cast?.let { "Reparto: $it" },
        )
        if (credits.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    credits.forEach { Text(it, color = TextLo, fontSize = 13.sp, lineHeight = 18.sp) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Serie

@Composable
private fun ShowDetail(vm: MainViewModel, s: Series) {
    val dataMap by vm.seriesData.collectAsStateWithLifecycle()
    val loading by vm.detailLoading.collectAsStateWithLifecycle()
    val failed by vm.detailFailed.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val data = dataMap[vm.seriesKey(s)]
    val episodes = data?.episodes.orEmpty()
    val seasons = remember(data) { data?.seasons().orEmpty() }
    var expanded by remember(s.id) { mutableStateOf(false) }
    var picked by rememberSaveable(s.id) { mutableIntStateOf(Int.MIN_VALUE) }

    val next = remember(episodes, progress) { pickNext(episodes, progress) }
    val season = if (picked in seasons) picked else (next?.season ?: seasons.firstOrNull() ?: 1)
    val seasonEpisodes = remember(episodes, season) { episodes.filter { it.season == season } }
    val started = remember(episodes, progress) { episodes.any { progress[it.url] != null } }

    val meta = listOfNotNull(
        data?.year ?: s.year,
        (data?.rating ?: s.rating)?.let { "★ $it" },
        if (seasons.size == 1) "1 temporada" else if (seasons.size > 1) "${seasons.size} temporadas" else null,
    ).joinToString("  ·  ")

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BackBar(vm) }
        item { Header(s.cover, s.name, meta, data?.genre) }
        if (next != null) {
            item {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    PlayButton((if (started) "Continuar" else "Reproducir") + " T${next.season} E${next.number}") {
                        vm.playEpisode(s, next, episodes)
                    }
                }
            }
        }
        item { Plot(data?.plot ?: s.plot, loading, expanded) { expanded = !expanded } }

        when {
            loading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Lavender)
                }
            }

            failed -> item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("No se pudieron cargar los capítulos.", color = TextLo)
                    Button(onClick = { vm.openSeries(s) }) { Text("Reintentar") }
                }
            }

            else -> {
                if (seasons.size > 1) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(seasons) { n ->
                                PillChip(seasonLabel(n), n == season) { picked = n }
                            }
                        }
                    }
                }
                items(seasonEpisodes) { ep ->
                    val p = progress[ep.url]
                    EpisodeRow(
                        ep = ep,
                        fallbackImage = s.cover,
                        progress = p,
                        onPlay = { vm.playEpisode(s, ep, episodes) },
                        onToggleWatched = {
                            vm.setWatched(vm.episodeChannel(s, ep), p?.watched != true)
                        },
                    )
                }
            }
        }
    }
}

/** Capítulo que toca ver: el que quedó a medias, o el siguiente al último visto, o el primero. */
private fun pickNext(eps: List<Episode>, progress: Map<String, Progress>): Episode? {
    if (eps.isEmpty()) return null
    val inProgress = eps
        .filter { val p = progress[it.url]; p != null && !p.watched && p.posMs > 0L }
        .maxByOrNull { progress[it.url]?.updated ?: 0L }
    if (inProgress != null) return inProgress
    val lastSeen = eps.indexOfLast { progress[it.url]?.watched == true }
    return if (lastSeen >= 0 && lastSeen + 1 < eps.size) eps[lastSeen + 1] else eps.first()
}

private fun seasonLabel(n: Int): String = if (n == 0) "Especiales" else "Temporada $n"

@Composable
private fun EpisodeRow(
    ep: Episode,
    fallbackImage: String?,
    progress: Progress?,
    onPlay: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    val image = ep.image ?: fallbackImage
    var failed by remember(image) { mutableStateOf(false) }
    val watched = progress?.watched == true
    // La fila de reproducir y el botón de visto son hermanos (no anidados), para que el control
    // remoto los recorra sin quedarse atrapado dentro de uno de ellos.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .pressClick(shape = RoundedCornerShape(16.dp), onClick = onPlay)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .width(116.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
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
                        text = ep.number.toString(),
                        color = Lavender,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (progress != null && !watched && progress.fraction() > 0f) {
                    ProgressLine(progress.fraction(), Modifier.align(Alignment.BottomCenter))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${ep.number}. ${ep.title}",
                    color = TextHi,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                prettyDuration(ep.duration)?.let { Text(it, color = TextLo, fontSize = 12.sp) }
                if (!ep.plot.isNullOrBlank()) {
                    Text(
                        text = ep.plot,
                        color = TextLo,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onToggleWatched) {
            Icon(
                imageVector = if (watched) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (watched) "Marcar como no visto" else "Marcar como visto",
                tint = if (watched) Lavender else TextLo,
            )
        }
    }
}

// ---------------------------------------------------------------- Piezas comunes

@Composable
private fun BackBar(vm: MainViewModel) {
    IconButton(onClick = { vm.closeDetail() }, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = TextHi)
    }
}

@Composable
private fun Header(cover: String?, title: String, meta: String, extra: String?) {
    var failed by remember(cover) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .width(120.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            if (cover != null && !failed) {
                AsyncImage(
                    model = cover,
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
                    fontSize = 32.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextHi,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank()) Text(meta, color = TextLo, fontSize = 13.sp)
            if (!extra.isNullOrBlank()) {
                Text(extra, color = TextLo, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlayButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Plot(text: String?, loading: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        when {
            !text.isNullOrBlank() -> Text(
                text = text,
                color = TextHi.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.pressClick(shape = RoundedCornerShape(12.dp), onClick = onToggle),
            )

            loading -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = Lavender,
            )

            else -> Text("Sin sinopsis disponible.", color = TextLo, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) Lavender else Color.White.copy(alpha = 0.08f))
            .pressClick(shape = CircleShape, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Ink else TextHi,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun clockText(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

/** "01:45:12" -> "1 h 45 min". Si no se entiende el formato, devuelve null. */
private fun prettyDuration(raw: String?): String? {
    val parts = raw?.trim()?.split(":") ?: return null
    if (parts.size != 3) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h == 0 && m == 0) return null
    return if (h == 0) "$m min" else "$h h $m min"
}
