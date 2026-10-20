package com.pilltv.app.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.pilltv.app.MainViewModel
import com.pilltv.app.TrackOption
import com.pilltv.app.data.Channel
import com.pilltv.app.data.Kind
import kotlinx.coroutines.delay

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

/** Botón redondo del reproductor. Con el control remoto se enfoca y se activa con OK. */
@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    boxSize: Dp = 48.dp,
    filled: Boolean = false,
    focus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(boxSize)
            .clip(CircleShape)
            .background(if (filled) Lavender else Color.Transparent)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .pressClick(shape = CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (filled) Ink else Color.White,
            modifier = Modifier.size(if (filled) 30.dp else 24.dp),
        )
    }
}

@Composable
private fun ControlChip(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .pressClick(shape = CircleShape, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

/**
 * Reproductor pensado para el control remoto.
 *
 * - Con los controles visibles, el foco se mueve entre los botones con las flechas y OK activa el botón.
 * - Con los controles ocultos, el primer toque de OK o de una flecha (o un toque en pantalla) solo
 *   muestra los controles: no pausa, no salta ni cambia de canal. Las teclas multimedia del control
 *   (play/pausa, adelantar, retroceder) siguen actuando directo.
 * - Atrás cierra el reproductor (lo maneja AppRoot).
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: MainViewModel) {
    val current by vm.current.collectAsStateWithLifecycle()
    val playing by vm.playWhenReady.collectAsStateWithLifecycle()
    val buffering by vm.buffering.collectAsStateWithLifecycle()
    val error by vm.playerError.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()

    val audioOpts by vm.audioOptions.collectAsStateWithLifecycle()
    val textOpts by vm.textOptions.collectAsStateWithLifecycle()
    val qualityOpts by vm.videoOptions.collectAsStateWithLifecycle()
    val queueItems by vm.queueItems.collectAsStateWithLifecycle()

    var showTracks by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    // La cola va al costado del reproductor y solo se abre con su botón.
    var showQueue by remember { mutableStateOf(false) }
    // Atrás cierra primero la cola y, si ya está cerrada, el reproductor.
    BackHandler(enabled = showQueue) { showQueue = false }

    var controls by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var notice by remember { mutableStateOf<String?>(null) }
    var seekable by remember { mutableStateOf(false) }
    // Solo cambia una vez por minuto (antes se redibujaba toda la pantalla cada 5 s).
    var minute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }
    val nowMs = minute * 60_000L
    var hint by remember { mutableStateOf<String?>(null) }
    var hintLeft by remember { mutableStateOf(true) }
    var hintTick by remember { mutableIntStateOf(0) }
    val fmt = remember { clockFormat() }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    // Oculta los controles solos a los 3,5 s mientras se reproduce.
    LaunchedEffect(controls, tick, playing, showQueue) {
        if (controls && playing && !showQueue) {
            delay(3500)
            controls = false
        }
    }

    // Reloj para pedir la guía de programación de nuevo cuando termina un programa.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            minute = System.currentTimeMillis() / 60_000L
        }
    }

    // Posición y duración para la barra: solo mientras los controles se ven.
    LaunchedEffect(controls) {
        if (!controls) return@LaunchedEffect
        while (true) {
            pos = vm.player.currentPosition.coerceAtLeast(0L)
            dur = vm.player.duration.coerceAtLeast(0L)
            seekable = vm.player.isCurrentMediaItemSeekable
            delay(500)
        }
    }

    // Aviso de salto (+10 s / -10 s), visible aunque los controles estén ocultos.
    LaunchedEffect(hintTick) {
        if (hint != null) {
            delay(800)
            hint = null
        }
    }

    // Aviso corto (por ejemplo, "no permite retroceder").
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2500)
            notice = null
        }
    }

    // Con los controles visibles el foco está en Reproducir; ocultos, en la capa del reproductor,
    // que es la que recibe las teclas del control remoto.
    LaunchedEffect(controls) {
        runCatching {
            if (controls) playFocus.requestFocus() else rootFocus.requestFocus()
        }
    }

    val ch = current ?: return
    val isFav = favs.any { it.url == ch.url }
    val isMovie = ch.kind == Kind.MOVIE

    // Guía del canal en vivo (programa actual y siguiente).
    LaunchedEffect(ch.url, minute) { vm.loadEpg(ch) }
    // Solo la guía de este canal: la de los demás no redibuja el reproductor.
    val epgNow by remember(ch.url) { vm.epgFor(ch.url) }
        .collectAsStateWithLifecycle(initialValue = vm.epg.value[ch.url])
    val epg = epgNow

    fun wake() {
        controls = true
        tick++
    }

    // Salto de 10 segundos; si el canal no permite saltar, lo dice.
    fun jump(ms: Long) {
        val ok = vm.seekBy(ms)
        hint = if (ok) {
            if (ms < 0) "−10 s" else "+10 s"
        } else {
            "Este canal no permite saltar"
        }
        hintLeft = ms < 0
        hintTick++
    }

    if (showTracks) {
        TracksDialog(
            audio = audioOpts,
            text = textOpts,
            onAudio = { vm.selectAudio(it) },
            onText = { vm.selectText(it) },
            onDismiss = {
                showTracks = false
                tick++
            },
        )
    }

    if (showQuality) {
        QualityDialog(
            options = qualityOpts,
            onPick = { vm.selectVideo(it) },
            onDismiss = {
                showQuality = false
                tick++
            },
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            // Un toque en pantalla con los controles ocultos solo los muestra.
            .pointerInput(controls) {
                detectTapGestures { if (!controls) wake() }
            }
            // Cualquier tecla mantiene los controles vivos mientras se navega.
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) tick++
                false
            }
            // Con los controles ocultos, las flechas y OK actúan directamente.
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || controls) return@onKeyEvent false
                when (e.key) {
                    // Primer toque: solo despierta los controles.
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                        wake()
                        true
                    }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        vm.togglePlay()
                        wake()
                        true
                    }
                    Key.MediaRewind -> {
                        jump(-10_000L)
                        wake()
                        true
                    }
                    Key.MediaFastForward -> {
                        jump(10_000L)
                        wake()
                        true
                    }
                    else -> false
                }
            },
    ) {
        // El video y los controles dejan libre el costado derecho cuando se ve la cola.
        val queueVisible = showQueue && queueItems.size > 1
        Box(Modifier.fillMaxSize().padding(end = if (queueVisible) 320.dp else 0.dp)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    player = vm.player
                    keepScreenOn = true
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        if (error != null) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(error ?: "", color = TextHi, textAlign = TextAlign.Center)
                ControlChip(Icons.Rounded.PlayArrow, "Reintentar") { vm.retry() }
            }
        } else if (buffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Lavender)
        }

        val h = hint
        if (h != null) {
            Text(
                text = h,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(if (hintLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }

        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                )

                // Barra superior
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton(Icons.Rounded.KeyboardArrowDown, "Minimizar") {
                        vm.closePlayer()
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        Text(
                            text = ch.name,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(text = ch.group, color = TextLo, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // Calidad: solo si el video tiene más de una resolución disponible.
                    if (qualityOpts.size > 2) {
                        ControlButton(Icons.Rounded.HighQuality, "Calidad de video") {
                            showQuality = true
                        }
                    }
                    // Audio y subtítulos: solo si el video trae más de una pista.
                    if (audioOpts.size > 1 || textOpts.isNotEmpty()) {
                        ControlButton(Icons.Rounded.Subtitles, "Audio y subtítulos") {
                            showTracks = true
                        }
                    }
                }

                // Zona inferior: progreso o guía + píldora de controles
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (isMovie) {
                        val frac = if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(formatTime(pos), color = Color.White, fontSize = 13.sp)
                            LinearProgressIndicator(
                                progress = { frac },
                                color = Lavender,
                                trackColor = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp).height(6.dp),
                            )
                            Text(formatTime(dur), color = Color.White, fontSize = 13.sp)
                        }
                    } else {
                        // Programa actual y siguiente (si el servidor los informa).
                        if (epg != null && (epg.now != null || epg.next != null)) {
                            GuideLines(
                                epg = epg,
                                nowMs = nowMs,
                                fmt = fmt,
                                modifier = Modifier
                                    .padding(horizontal = 48.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                        // "En vivo" solo aparece si el canal permite retroceder (hay algo a lo que volver).
                        if (seekable) {
                            ControlChip(Icons.Rounded.FastForward, "En vivo") {
                                vm.goLive()
                                tick++
                            }
                        }
                    }

                    val n = notice
                    if (n != null) {
                        Text(
                            text = n,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }

                    // Píldora de controles
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isMovie) {
                            ControlButton(Icons.Rounded.Replay10, "Retroceder 10 segundos") {
                                jump(-10_000L)
                                tick++
                            }
                        } else {
                            ControlButton(Icons.Rounded.SkipPrevious, "Canal anterior") {
                                vm.step(-1)
                                tick++
                            }
                        }
                        ControlButton(
                            icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            description = if (playing) "Pausar" else "Reproducir",
                            boxSize = 60.dp,
                            filled = true,
                            focus = playFocus,
                        ) {
                            vm.togglePlay()
                            tick++
                        }
                        if (isMovie) {
                            ControlButton(Icons.Rounded.Forward10, "Adelantar 10 segundos") {
                                jump(10_000L)
                                tick++
                            }
                        } else {
                            ControlButton(Icons.Rounded.SkipNext, "Canal siguiente") {
                                vm.step(1)
                                tick++
                            }
                        }
                        ControlButton(
                            icon = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            description = "Favorito",
                        ) {
                            vm.toggleFavorite(ch)
                            tick++
                        }
                        // Cola de reproducción: canales de la lista o capítulos de la serie.
                        if (queueItems.size > 1) {
                            ControlButton(Icons.Rounded.QueueMusic, "Cola de reproducción") {
                                showQueue = !showQueue
                                tick++
                            }
                        }
                    }
                }
            }
        }
        }

        if (queueVisible) {
            QueuePanel(
                modifier = Modifier.align(Alignment.CenterEnd),
                items = queueItems,
                current = ch,
                onPick = {
                    vm.play(it, queueItems)
                    tick++
                },
            )
        }
    }
}

/** Ventana para elegir el idioma del audio y los subtítulos. */
@Composable
private fun TracksDialog(
    audio: List<TrackOption>,
    text: List<TrackOption>,
    onAudio: (Int) -> Unit,
    onText: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1B2E),
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo", color = Lavender) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (audio.size > 1) {
                    Text("Audio", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    audio.forEachIndexed { i, o -> TrackRow(o) { onAudio(i) } }
                }
                if (text.isNotEmpty()) {
                    Text(
                        text = "Subtítulos",
                        color = TextHi,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = if (audio.size > 1) 16.dp else 0.dp),
                    )
                    text.forEachIndexed { i, o -> TrackRow(o) { onText(i) } }
                }
            }
        },
    )
}

@Composable
private fun TrackRow(option: TrackOption, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressClick(shape = RoundedCornerShape(12.dp), onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = option.selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Lavender, unselectedColor = TextLo),
        )
        Text(option.label, color = TextHi, fontSize = 15.sp, modifier = Modifier.width(240.dp))
    }
}

/** Calidad de video: automática o una resolución fija. */
@Composable
private fun QualityDialog(options: List<TrackOption>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1B2E),
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo", color = Lavender) } },
        title = { Text("Calidad de video", color = TextHi, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, o -> TrackRow(o) { onPick(i) } }
            }
        },
    )
}

/** Cola de reproducción al costado del reproductor: la lista en la que se está viendo, con el canal actual marcado. */
@Composable
private fun QueuePanel(
    modifier: Modifier = Modifier,
    items: List<Channel>,
    current: Channel,
    onPick: (Channel) -> Unit,
) {
    val listState = rememberLazyListState()
    val index = items.indexOfFirst { it.url == current.url }
    LaunchedEffect(index) {
        if (index > 0) listState.scrollToItem(index)
    }
    Column(
        modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(Brush.verticalGradient(listOf(Color(0xE6402A5C), Color(0xF2120D1A))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = "Cola de reproducción",
            color = TextHi,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.url }) { c ->
                val isNow = c.url == current.url
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glassCard(RoundedCornerShape(16.dp), tint = if (isNow) Lavender else null)
                        .pressClick(shape = RoundedCornerShape(16.dp)) { onPick(c) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ChannelLogo(c, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = c.name,
                            color = TextHi,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = c.group,
                            color = TextLo,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isNow) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Reproduciendo", tint = Lavender)
                    }
                }
            }
        }
    }
}
