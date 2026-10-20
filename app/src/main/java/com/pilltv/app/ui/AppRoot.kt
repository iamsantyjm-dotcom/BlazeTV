package com.pilltv.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.pilltv.app.MainViewModel
import com.pilltv.app.data.Channel

private const val SETTINGS_TAB = 3

/** Dos halos de color por pestaña: en vivo, películas, favoritos y ajustes. */
private fun moodOf(tab: Int): Pair<Color, Color> = when (tab) {
    0 -> Lavender to Azure
    1 -> Color(0xFFFF5E8A) to Color(0xFF7B5CFF)
    2 -> Color(0xFFFFB347) to Color(0xFFFF5E8A)
    else -> Color(0xFF3FD0C9) to Color(0xFF4C7DFF)
}

private data class TabItem(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    TabItem("En vivo", Icons.Rounded.LiveTv),
    TabItem("Películas", Icons.Rounded.Movie),
    TabItem("Favoritos", Icons.Rounded.Favorite),
)

private val PillShape = RoundedCornerShape(percent = 50)

@Composable
fun AppRoot(vm: MainViewModel) {
    val playerOpen by vm.playerOpen.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Pestaña a la que volver al cerrar Ajustes (el engrane ya no está en la barra inferior).
    var previousTab by rememberSaveable { mutableIntStateOf(0) }
    var settingsPage by rememberSaveable { mutableIntStateOf(0) }
    // Los botones "Agregar lista" de las pantallas vacías llevan directo a Ajustes > Mis listas.
    val goToLists = {
        if (tab != SETTINGS_TAB) previousTab = tab
        settingsPage = SettingsPages.LISTS
        tab = SETTINGS_TAB
    }

    // Atrás cierra, de arriba a abajo: reproductor, ficha, búsqueda.
    BackHandler(enabled = searchOpen && detail == null && !playerOpen) { searchOpen = false }
    BackHandler(enabled = detail != null && !playerOpen) { vm.closeDetail() }
    BackHandler(enabled = playerOpen) { vm.closePlayer() }

    val overlayOpen = detail != null || searchOpen

    // Atajos del control remoto: dónde está el foco ahora (arriba, abajo o en el contenido).
    val focusManager = LocalFocusManager.current
    val gearRequester = remember { FocusRequester() }
    val tabRequesters = remember { List(Tabs.size) { FocusRequester() } }
    var searchFocused by remember { mutableStateOf(false) }
    var gearFocused by remember { mutableStateOf(false) }
    var pillFocused by remember { mutableStateOf(false) }
    LaunchedEffect(playerOpen, overlayOpen) {
        searchFocused = false
        gearFocused = false
        pillFocused = false
    }

    // Ajustes se abre y se cierra con el engrane de arriba; Atrás también lo cierra.
    fun toggleSettings() {
        if (tab == SETTINGS_TAB) {
            settingsPage = SettingsPages.MAIN
            tab = previousTab
        } else {
            previousTab = tab
            tab = SETTINGS_TAB
        }
    }
    BackHandler(enabled = tab == SETTINGS_TAB && settingsPage == SettingsPages.MAIN && !playerOpen && !overlayOpen) {
        toggleSettings()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || playerOpen || overlayOpen) return@onPreviewKeyEvent false
                val inSettings = tab == SETTINGS_TAB
                val onSearch = searchFocused && !inSettings
                val onPill = pillFocused && !inSettings
                val inContent = !onSearch && !gearFocused && !onPill
                val currentTab = if (inSettings) previousTab else tab
                when (ev.key) {
                    // Derecha: se mueve normal por la cuadrícula; solo si ya no hay nada a la
                    // derecha salta a lo más alto (Ajustes).
                    Key.DirectionRight -> if (inContent) {
                        focusManager.moveFocus(FocusDirection.Right) ||
                            runCatching { gearRequester.requestFocus() }.isSuccess
                    } else false
                    // Izquierda: se mueve normal; solo si ya no hay nada a la izquierda salta abajo,
                    // a la pestaña activa. Desde Buscar salta directo.
                    Key.DirectionLeft -> if (!inSettings && onSearch) {
                        runCatching { tabRequesters[currentTab].requestFocus() }.isSuccess
                    } else if (!inSettings && inContent) {
                        focusManager.moveFocus(FocusDirection.Left) ||
                            runCatching { tabRequesters[currentTab].requestFocus() }.isSuccess
                    } else false
                    else -> false
                }
            },
    ) {
        // El fondo cambia de tono suavemente según la pestaña: es el único efecto animado que se conserva.
        val moodA by animateColorAsState(moodOf(tab).first, tween(1100), label = "moodA")
        val moodB by animateColorAsState(moodOf(tab).second, tween(1100), label = "moodB")
        AppBackground(moodA, moodB)

        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "pestanas",
        ) { t ->
            when (t) {
                0 -> ChannelsScreen(vm, favoritesOnly = false, goToLists = goToLists)
                1 -> MoviesScreen(vm, goToLists = goToLists)
                2 -> ChannelsScreen(vm, favoritesOnly = true, goToLists = goToLists)
                else -> SettingsScreen(
                    vm,
                    page = settingsPage,
                    onPage = { settingsPage = it },
                    backEnabled = !playerOpen && !overlayOpen,
                )
            }
        }

        // Barra superior: buscar y ajustes, uno junto al otro.
        if (!playerOpen && !overlayOpen) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = tab != SETTINGS_TAB,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150)),
                ) {
                    CircleGlassButton(
                        Icons.Rounded.Search,
                        "Buscar en todo",
                        TextHi,
                        modifier = Modifier.onFocusChanged { searchFocused = it.hasFocus },
                    ) {
                        searchOpen = true
                    }
                }
                CircleGlassButton(
                    Icons.Rounded.Settings,
                    "Ajustes",
                    if (tab == SETTINGS_TAB) Lavender else TextHi,
                    modifier = Modifier
                        .focusRequester(gearRequester)
                        .onFocusChanged { gearFocused = it.hasFocus },
                ) { toggleSettings() }
            }
        }

        AnimatedVisibility(
            visible = searchOpen,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
        ) {
            SearchOverlay(vm) { searchOpen = false }
        }
        detail?.let { DetailScreen(vm, it) }

        if (!playerOpen && !overlayOpen) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MiniPillHost(vm)
                AnimatedVisibility(
                    visible = tab != SETTINGS_TAB,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 2 },
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it / 2 },
                ) {
                    // En Ajustes la barra se oculta; se usa la última pestaña para no perder la selección.
                    NavPill(
                        selected = if (tab == SETTINGS_TAB) previousTab else tab,
                        onSelect = { tab = it },
                        requesters = tabRequesters,
                        onFocusChange = { pillFocused = it },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = playerOpen,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
        ) {
            PlayerScreen(vm)
        }
    }
}

/**
 * Barra de navegación. El indicador se desliza suavemente hacia la pestaña elegida
 * (una sola transición de 250 ms, sin rebotes) y cada pestaña se enfoca con el control remoto.
 */
@Composable
private fun NavPill(
    selected: Int,
    onSelect: (Int) -> Unit,
    requesters: List<FocusRequester>,
    onFocusChange: (Boolean) -> Unit,
) {
    val n = Tabs.size
    val lefts = remember { List(n) { 0f }.toMutableStateList() }
    val widths = remember { List(n) { 0f }.toMutableStateList() }
    val measured = widths[selected] > 0f

    val indLeft by animateFloatAsState(
        targetValue = lefts[selected],
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "indIzq",
    )
    val indWidth by animateFloatAsState(
        targetValue = widths[selected],
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "indAncho",
    )

    Box(Modifier.onFocusChanged { onFocusChange(it.hasFocus) }.glassPill(PillShape).padding(6.dp)) {
        Row(
            modifier = Modifier.drawBehind {
                if (!measured) return@drawBehind
                drawRoundRect(
                    color = Lavender,
                    topLeft = Offset(indLeft, 0f),
                    size = Size(indWidth, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tabs.forEachIndexed { i, tab ->
                val on = i == selected
                val tint by animateColorAsState(
                    targetValue = if (on) Ink else TextHi,
                    animationSpec = tween(200),
                    label = "tinte",
                )
                val padH by animateDpAsState(
                    targetValue = if (on) 18.dp else 14.dp,
                    animationSpec = tween(250),
                    label = "relleno",
                )
                Row(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val x = coords.positionInParent().x
                            val w = coords.size.width.toFloat()
                            if (lefts[i] != x) lefts[i] = x
                            if (widths[i] != w) widths[i] = w
                        }
                        .focusRequester(requesters[i])
                        .pressClick(shape = CircleShape) { onSelect(i) }
                        .padding(horizontal = padH, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    AnimatedVisibility(
                        visible = on,
                        enter = fadeIn(tween(200)) + expandHorizontally(tween(250)),
                        exit = fadeOut(tween(120)) + shrinkHorizontally(tween(200)),
                    ) {
                        Text(
                            text = tab.label,
                            color = Ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mini reproductor que queda abajo al salir del reproductor a pantalla completa.
 * Entra y sale con un deslizamiento suave.
 */
@Composable
private fun MiniPillHost(vm: MainViewModel) {
    val current by vm.current.collectAsStateWithLifecycle()
    var last by remember { mutableStateOf<Channel?>(null) }
    current?.let { last = it }

    AnimatedVisibility(
        visible = current != null,
        enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 2 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it / 2 },
    ) {
        last?.let { MiniPill(it, vm) }
    }
}

@Composable
private fun MiniPill(c: Channel, vm: MainViewModel) {
    val playing by vm.playWhenReady.collectAsStateWithLifecycle()
    val buffering by vm.buffering.collectAsStateWithLifecycle()
    val error by vm.playerError.collectAsStateWithLifecycle()

    val statusText = when {
        error != null -> "No se pudo reproducir"
        buffering -> "Cargando…"
        playing -> "En vivo"
        else -> "En pausa"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassPill(PillShape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MiniVideo(vm)
        Column(Modifier.weight(1f)) {
            Text(
                text = c.name,
                color = TextHi,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusText,
                color = if (error != null) Coral else TextLo,
                fontSize = 12.sp,
            )
        }
        MiniButton(
            icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            description = if (playing) "Pausar" else "Reproducir",
            tint = TextHi,
        ) { vm.togglePlay() }
        MiniButton(icon = Icons.Rounded.Close, description = "Cerrar", tint = TextLo) { vm.stop() }
    }
}

@Composable
private fun MiniButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressClick(shape = CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun CircleGlassButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        // pressClick va antes de glassPill para que el halo de foco no quede recortado por el círculo.
        modifier = modifier
            .size(44.dp)
            .pressClick(shape = CircleShape, onClick = onClick)
            .glassPill(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Video en miniatura dentro de la barra: el mismo reproductor, así se sigue viendo al minimizar. */
@Composable
private fun MiniVideo(vm: MainViewModel) {
    Box(
        Modifier
            .width(104.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .pressClick(shape = RoundedCornerShape(12.dp)) { vm.openPlayer() },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = vm.player
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
