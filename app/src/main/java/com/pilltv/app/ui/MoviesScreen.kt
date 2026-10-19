package com.pilltv.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pilltv.app.MainViewModel

/** Pestaña de películas y series. Las series se agrupan: se abre la serie y de ahí se elige temporada y capítulo. */
@Composable
fun MoviesScreen(vm: MainViewModel, goToLists: () -> Unit) {
    val movies by vm.movies.collectAsStateWithLifecycle()
    val series by vm.series.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()

    var showSeries by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val hasSeries = series.isNotEmpty()
    val seriesMode = showSeries && hasSeries

    val groups = remember(movies, series, seriesMode) {
        if (seriesMode) series.map { it.group }.distinct().sorted()
        else movies.map { it.group }.distinct().sorted()
    }
    val shownMovies = remember(movies, query, group) {
        movies.filter {
            (group == null || it.group == group) &&
                (query.isBlank() || it.name.contains(query, ignoreCase = true))
        }
    }
    val shownSeries = remember(series, query, group) {
        series.filter {
            (group == null || it.group == group) &&
                (query.isBlank() || it.name.contains(query, ignoreCase = true))
        }
    }
    val favUrls = remember(favs) { favs.map { it.url }.toHashSet() }

    // Lo que quedó a medias, lo más reciente primero.
    val continueList = remember(progress) {
        progress.values
            .filter { !it.watched && it.posMs > 0L && it.title.isNotBlank() }
            .sortedByDescending { it.updated }
            .take(15)
    }

    LaunchedEffect(groups) {
        val g = group
        if (g != null && !groups.contains(g)) group = null
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            text = if (hasSeries) "Películas y series" else "Películas",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = TextHi,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        if (hasSeries) {
            LiquidSegmented(
                options = listOf("Películas", "Series"),
                selected = if (seriesMode) 1 else 0,
                onSelect = { i ->
                    showSeries = i == 1
                    group = null
                },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (groups.size > 1) CategoryPicker(groups, group) { group = it }
            }
        }

        val nothingToShow = if (seriesMode) shownSeries.isEmpty() else shownMovies.isEmpty()
        when {
            loading && movies.isEmpty() && series.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Lavender)
                }

            movies.isEmpty() && series.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp).padding(bottom = 160.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Tu lista no trae películas", color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Las películas y series se separan solas de la TV en vivo cuando la lista las incluye.",
                    color = TextLo,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                )
                Button(onClick = goToLists) { Text("Ver listas") }
            }

            nothingToShow && !(continueList.isNotEmpty() && !seriesMode && query.isBlank() && group == null) ->
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        if (query.isBlank()) "No hay títulos en esta categoría." else "Nada coincide con la búsqueda.",
                        color = TextLo,
                        textAlign = TextAlign.Center,
                    )
                }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Continuar viendo: solo en la vista normal de películas, sin filtros.
                if (!seriesMode && continueList.isNotEmpty() && query.isBlank() && group == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Continuar viendo",
                                color = TextHi,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(continueList, key = { it.url }) { p ->
                                    PosterCard(
                                        title = p.title,
                                        image = p.image,
                                        progress = p.fraction(),
                                        onClick = { vm.resume(p) },
                                        modifier = Modifier.width(110.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (seriesMode) {
                    items(shownSeries, key = { it.sourceId + ":" + it.id }) { s ->
                        PosterCard(
                            title = s.name,
                            image = s.cover,
                            subtitle = s.year,
                            onClick = { vm.openSeries(s) },
                        )
                    }
                } else {
                    items(shownMovies, key = { it.url }) { m ->
                        val p = progress[m.url]
                        PosterCard(
                            title = m.name,
                            image = m.logo,
                            subtitle = m.year,
                            progress = p?.fraction() ?: 0f,
                            watched = p?.watched == true,
                            favorite = m.url in favUrls,
                            onFavorite = { vm.toggleFavorite(m) },
                            onClick = { vm.onMovieClick(m, shownMovies) },
                        )
                    }
                }
            }
        }
    }
}
