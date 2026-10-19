package com.pilltv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pilltv.app.MainViewModel
import com.pilltv.app.data.Channel
import com.pilltv.app.data.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** Resultado de una búsqueda: canales, películas y series a la vez. */
private class Results(
    val live: List<Channel>,
    val movies: List<Channel>,
    val series: List<Series>,
) {
    fun isEmpty() = live.isEmpty() && movies.isEmpty() && series.isEmpty()
}

private val Marks = Regex("\\p{Mn}+")

/** Minúsculas y sin tildes: "Película" y "pelicula" se encuentran entre sí. */
private fun norm(s: String): String {
    if (s.all { it.code < 128 }) return s.lowercase()
    return Marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()
}

/** Nombres ya normalizados, para no repetir ese trabajo en cada letra que se escribe. */
private class Index(live: List<Channel>, movies: List<Channel>, series: List<Series>) {
    private val liveItems = live
    private val movieItems = movies
    private val seriesItems = series
    private val liveNames = Array(live.size) { norm(live[it].name) }
    private val movieNames = Array(movies.size) { norm(movies[it].name) }
    private val seriesNames = Array(series.size) { norm(series[it].name) }

    fun search(query: String): Results {
        val terms = norm(query).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return Results(emptyList(), emptyList(), emptyList())
        return Results(
            live = find(liveNames, liveItems, terms),
            movies = find(movieNames, movieItems, terms),
            series = find(seriesNames, seriesItems, terms),
        )
    }

    /** Primero los que empiezan con lo escrito; luego los que lo contienen. Máximo [CAP] por tipo. */
    private fun <T> find(names: Array<String>, items: List<T>, terms: List<String>): List<T> {
        val first = ArrayList<T>()
        val rest = ArrayList<T>()
        for (i in names.indices) {
            val n = names[i]
            if (terms.all { n.contains(it) }) {
                if (n.startsWith(terms[0])) first.add(items[i]) else if (rest.size < CAP) rest.add(items[i])
                if (first.size >= CAP) break
            }
        }
        return (first + rest).take(CAP)
    }

    private companion object {
        const val CAP = 60
    }
}

/**
 * Contenido de la búsqueda. Con el mando: el campo de texto recibe el foco al abrir; OK abre el
 * teclado de la TV; "Buscar" del teclado (o abajo) lleva el foco a los resultados.
 */
@Composable
fun SearchScreen(vm: MainViewModel, onClose: () -> Unit, active: Boolean = true) {
    val live by vm.live.collectAsStateWithLifecycle()
    val movies by vm.movies.collectAsStateWithLifecycle()
    val series by vm.series.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<Results?>(null) }
    var fieldFocused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // El foco (y el teclado) solo mientras la búsqueda está abierta; al cerrar se quitan.
    LaunchedEffect(active) {
        if (active) runCatching { focus.requestFocus() } else focusManager.clearFocus()
    }

    val index by produceState<Index?>(null, live, movies, series) {
        value = withContext(Dispatchers.Default) { Index(live, movies, series) }
    }

    LaunchedEffect(query, index) {
        val idx = index ?: return@LaunchedEffect
        if (query.isBlank()) {
            results = null
            return@LaunchedEffect
        }
        delay(200) // espera a que termine de escribir
        results = withContext(Dispatchers.Default) { idx.search(query) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            // Absorbe los toques para que no lleguen a la pantalla que está debajo.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        AppBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .pressClick(shape = CircleShape) { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = TextHi)
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Canales, películas y series", color = TextLo) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextLo) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .pressClick(shape = CircleShape) {
                                        query = ""
                                        runCatching { focus.requestFocus() }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Borrar búsqueda", tint = TextLo)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Lavender,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus)
                        .onFocusChanged { fieldFocused = it.isFocused }
                        // Borde lavanda con el foco, igual que el resto de elementos enfocables.
                        .border(3.dp, if (fieldFocused) Lavender else Color.Transparent, CircleShape)
                        .onPreviewKeyEvent { e ->
                            // OK/Enter en el campo abre el teclado de la TV; el evento sigue su curso.
                            if (e.type == KeyEventType.KeyUp &&
                                (e.key == Key.DirectionCenter || e.key == Key.Enter)
                            ) {
                                keyboard?.show()
                            }
                            false
                        },
                )
            }

            val r = results
            when {
                index == null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(color = Lavender)
                }

                query.isBlank() -> Hint("Escribe para buscar en toda tu lista a la vez.")

                r == null -> Unit

                r.isEmpty() -> Hint("No se encontró nada para \"${query.trim()}\".")

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (r.live.isNotEmpty()) {
                        item { SectionTitle("Canales") }
                        items(r.live) { ch ->
                            ChannelResult(ch, playing = false) { vm.play(ch, r.live) }
                        }
                    }
                    if (r.movies.isNotEmpty()) {
                        item { SectionTitle("Películas") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(r.movies) { m ->
                                    val p = progress[m.url]
                                    PosterCard(
                                        title = m.name,
                                        image = m.logo,
                                        subtitle = m.year,
                                        progress = p?.fraction() ?: 0f,
                                        watched = p?.watched == true,
                                        onClick = { vm.onMovieClick(m, r.movies) },
                                        modifier = Modifier.width(110.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (r.series.isNotEmpty()) {
                        item { SectionTitle("Series") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(r.series) { s ->
                                    PosterCard(
                                        title = s.name,
                                        image = s.cover,
                                        subtitle = s.year,
                                        onClick = { vm.openSeries(s) },
                                        modifier = Modifier.width(110.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, color = TextLo, fontSize = 14.sp)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = TextHi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun ChannelResult(channel: Channel, playing: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(if (playing) Lavender.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            .pressClick(shape = shape, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChannelLogo(channel, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = TextHi,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = channel.group,
                color = TextLo,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
