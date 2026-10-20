package com.pilltv.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pilltv.app.MainViewModel
import com.pilltv.app.data.Channel
import com.pilltv.app.data.EpgNow
import com.pilltv.app.data.Kind
import com.pilltv.app.data.XtreamEpg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat

@Composable
fun ChannelsScreen(vm: MainViewModel, favoritesOnly: Boolean, goToLists: () -> Unit) {
    val all by vm.live.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    // Se lee dentro de cada fila (no aquí) para que, al llegar la guía de un canal, no se redibuje toda la pantalla.
    val epgState = vm.epg.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val showGuide by vm.showGuide.collectAsStateWithLifecycle()

    // Reloj para la barra de avance y para pedir la guía de nuevo cuando termina un programa.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val fmt = remember { clockFormat() }

    val base = if (favoritesOnly) favs else all
    // En TV en vivo se empieza en la categoría predeterminada (Ajustes); en favoritos, en "Todos".
    var group by rememberSaveable(favoritesOnly) {
        mutableStateOf<String?>(if (favoritesOnly) null else vm.defaultLiveGroup.value)
    }
    var query by rememberSaveable(favoritesOnly) { mutableStateOf("") }

    // Categorías y filtro se calculan fuera del hilo principal (con listas grandes escribir en el buscador se trababa).
    val groups by produceState(initialValue = emptyList<String>(), base) {
        value = withContext(Dispatchers.Default) { base.map { it.group }.distinct().sorted() }
    }
    // Mientras se recalcula se sigue mostrando la lista anterior; null solo la primera vez.
    val shownState = produceState<List<Channel>?>(initialValue = null, base, query, group) {
        // Espera breve al escribir: si llega otra letra, esta búsqueda se cancela.
        if (query.isNotBlank()) delay(150)
        value = withContext(Dispatchers.Default) {
            val q = query.trim()
            val seen = HashSet<String>()
            val out = ArrayList<Channel>()
            for (c in base) {
                if (group != null && c.group != group) continue
                if (q.isNotEmpty() && !c.name.contains(q, ignoreCase = true)) continue
                // Sin canales repetidos (misma dirección) para que cada fila tenga una clave única.
                if (seen.add(c.url)) out.add(c)
            }
            out
        }
    }
    val list: List<Channel> = shownState.value ?: emptyList()
    val ready = shownState.value != null
    val favUrls = remember(favs) { favs.map { it.url }.toHashSet() }

    // Canales vistos hace poco (solo los que siguen visibles en la lista).
    val recentShown = remember(recent, all) {
        if (recent.isEmpty()) {
            emptyList()
        } else {
            val urls = recent.map { it.url }.toHashSet()
            val byUrl = all.filter { it.url in urls }.associateBy { it.url }
            recent.mapNotNull { byUrl[it.url] }
        }
    }

    LaunchedEffect(groups) {
        val g = group
        // Con la lista de categorías todavía vacía (recién calculándose) no se borra la elegida.
        if (g != null && groups.isNotEmpty() && !groups.contains(g)) group = null
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            text = if (favoritesOnly) "Favoritos" else "TV en vivo",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = TextHi,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (groups.size > 1) CategoryPicker(groups, group) { group = it }
            }
        }

        when {
            (loading && base.isEmpty()) || (base.isNotEmpty() && !ready) ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Lavender)
                }

            base.isEmpty() -> EmptyState(favoritesOnly, goToLists)

            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    if (query.isBlank()) "No hay canales en esta categoría." else "Ningún canal coincide con la búsqueda.",
                    color = TextLo,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!favoritesOnly && query.isBlank() && group == null && recentShown.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Vistos hace poco",
                                color = TextHi,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(recentShown, key = { it.url }) { ch ->
                                    Column(
                                        modifier = Modifier
                                            .width(76.dp)
                                            .glassCard(RoundedCornerShape(16.dp))
                                            .pressClick(shape = RoundedCornerShape(16.dp)) { vm.play(ch, list) }
                                            .padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        ChannelLogo(ch, 60.dp)
                                        Text(
                                            text = ch.name,
                                            color = TextHi,
                                            fontSize = 11.sp,
                                            lineHeight = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                gridItems(list, key = { it.url }) { ch ->
                    // Solo se consulta la guía de los canales que están a la vista.
                    LaunchedEffect(ch.url, nowMs / 60_000L) { vm.loadEpg(ch) }
                    ChannelRow(
                        channel = ch,
                        showGuide = showGuide,
                        playing = current?.url == ch.url,
                        favorite = ch.url in favUrls,
                        epg = epgState.value[ch.url],
                        nowMs = nowMs,
                        fmt = fmt,
                        onClick = { vm.play(ch, list) },
                        onFavorite = { vm.toggleFavorite(ch) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SearchField(query: String, placeholder: String = "Buscar canal", onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = TextLo) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextLo) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Borrar búsqueda", tint = TextLo)
                }
            }
        },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Lavender,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@Composable
private fun ChannelRow(
    channel: Channel,
    showGuide: Boolean,
    playing: Boolean,
    favorite: Boolean,
    epg: EpgNow?,
    nowMs: Long,
    fmt: SimpleDateFormat,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    val guideSupported = remember(channel.url) { channel.kind == Kind.LIVE && XtreamEpg.supports(channel.url) }
    val hasGuide = showGuide && guideSupported
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(22.dp), tint = if (playing) Lavender else null)
            .pressClick(shape = RoundedCornerShape(22.dp), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChannelLogo(channel, 52.dp)
        Column(Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = TextHi,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasGuide) {
                GuideLines(epg, nowMs, fmt, Modifier.padding(top = 4.dp))
            } else {
                Text(
                    text = channel.group,
                    color = TextLo,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Botón de favorito enfocable por separado (con el mismo anillo lavanda que el resto).
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .pressClick(shape = CircleShape, onClick = onFavorite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (favorite) "Quitar de favoritos" else "Agregar a favoritos",
                tint = if (favorite) Coral else TextLo,
            )
        }
    }
}

@Composable
private fun EmptyState(favoritesOnly: Boolean, goToLists: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp).padding(bottom = 160.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (favoritesOnly) "Aún no tienes favoritos" else "Aún no hay canales en vivo",
            color = TextHi,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (favoritesOnly) {
                "Toca el corazón de un canal para guardarlo aquí."
            } else {
                "Agrega una lista M3U para ver tus canales."
            },
            color = TextLo,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        if (!favoritesOnly) {
            Button(onClick = goToLists, modifier = Modifier.focusRing()) { Text("Agregar lista") }
        }
    }
}
