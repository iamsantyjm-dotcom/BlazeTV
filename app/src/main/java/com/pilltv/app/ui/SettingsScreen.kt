package com.pilltv.app.ui

import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Category
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pilltv.app.MainViewModel
import com.pilltv.app.data.Kind
import com.pilltv.app.data.PlaylistSource
import com.pilltv.app.data.folderKey

private val DialogBg = Color(0xFF1E1B2E)

/**
 * Páginas de Ajustes. Quien muestra esta pantalla guarda solo el número, por eso son constantes
 * con nombre y no un enum: así no hay que tocar a quien la llama.
 */
object SettingsPages {
    const val MAIN = 0
    const val LISTS = 1
    const val LIVE_FOLDERS = 2
    const val MOVIE_FOLDERS = 3
}

@Composable
fun SettingsScreen(vm: MainViewModel, page: Int, onPage: (Int) -> Unit, backEnabled: Boolean) {
    BackHandler(enabled = backEnabled && page != SettingsPages.MAIN) { onPage(SettingsPages.MAIN) }
    AnimatedContent(
        targetState = page,
        // Fundido simple entre páginas: sin deslizamientos ni rebotes, para que sea fluido en TV.
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "ajustes",
    ) { p ->
        when (p) {
            SettingsPages.LISTS -> ListsPage(vm, onBack = { onPage(SettingsPages.MAIN) })
            SettingsPages.LIVE_FOLDERS -> FoldersPage(vm, Kind.LIVE, onBack = { onPage(SettingsPages.MAIN) })
            SettingsPages.MOVIE_FOLDERS -> FoldersPage(vm, Kind.MOVIE, onBack = { onPage(SettingsPages.MAIN) })
            else -> MainPage(vm, onPage)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Página principal
// ---------------------------------------------------------------------------------------------

@Composable
private fun MainPage(vm: MainViewModel, onPage: (Int) -> Unit) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val autoNext by vm.autoNextEpisodes.collectAsStateWithLifecycle()
    val landscape by vm.landscapeStart.collectAsStateWithLifecycle()
    val showGuide by vm.showGuide.collectAsStateWithLifecycle()
    val miniPlayer by vm.miniPlayer.collectAsStateWithLifecycle()
    val defaultGroup by vm.defaultLiveGroup.collectAsStateWithLifecycle()
    val hideAdult by vm.hideAdult.collectAsStateWithLifecycle()
    val pinSet by vm.hasPin.collectAsStateWithLifecycle()
    var pinMode by rememberSaveable { mutableStateOf(PinMode.NONE) }
    val liveGroups = remember(live) { live.map { it.group }.distinct().sorted() }
    var pickingDefault by rememberSaveable { mutableStateOf(false) }

    val liveTotal = folders.count { it.kind == Kind.LIVE }
    val liveHidden = folders.count { it.kind == Kind.LIVE && folderKey(it.kind, it.name) in hidden }
    val movieTotal = folders.count { it.kind == Kind.MOVIE }
    val movieHidden = folders.count { it.kind == Kind.MOVIE && folderKey(it.kind, it.name) in hidden }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 200.dp),
    ) {
        Text(
            text = "Ajustes",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = TextHi,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Listas")
            SettingsItem(
                icon = Icons.Rounded.VideoLibrary,
                title = "Mis listas",
                subtitle = when (sources.size) {
                    0 -> "Agrega tu lista IPTV"
                    1 -> "1 lista · editar, agregar o quitar"
                    else -> "${sources.size} listas · editar, agregar o quitar"
                },
                onClick = { onPage(SettingsPages.LISTS) },
            )
            SettingsItem(
                icon = Icons.Rounded.Refresh,
                title = "Actualizar listas ahora",
                subtitle = if (loading) "Actualizando…" else (status ?: "Vuelve a descargar canales y películas"),
                onClick = { if (!loading) vm.refreshAll() },
                chevron = false,
            )

            SectionLabel("Qué quieres ver")
            SettingsItem(
                icon = Icons.Rounded.LiveTv,
                title = "Carpetas de TV en vivo",
                subtitle = folderSummary(liveTotal, liveHidden),
                onClick = { onPage(SettingsPages.LIVE_FOLDERS) },
            )
            SettingsItem(
                icon = Icons.Rounded.Movie,
                title = "Carpetas de películas y series",
                subtitle = folderSummary(movieTotal, movieHidden),
                onClick = { onPage(SettingsPages.MOVIE_FOLDERS) },
            )

            SectionLabel("Reproducción")
            SettingsToggle(
                icon = Icons.Rounded.SkipNext,
                title = "Pasar al siguiente capítulo",
                subtitle = "Al terminar un capítulo, sigue con el siguiente de la serie",
                checked = autoNext,
                onChange = { vm.setAutoNextEpisodes(it) },
            )
            SettingsToggle(
                icon = Icons.Rounded.Fullscreen,
                title = "Abrir en horizontal",
                subtitle = "Los canales y películas se abren girados",
                checked = landscape,
                onChange = { vm.setLandscapeStart(it) },
            )
            SettingsToggle(
                icon = Icons.Rounded.PlayArrow,
                title = "Mini reproductor",
                subtitle = "Al salir del reproductor, el contenido sigue en una barra abajo. Si lo desactivas, se detiene",
                checked = miniPlayer,
                onChange = { vm.setMiniPlayer(it) },
            )

            SectionLabel("TV en vivo")
            SettingsItem(
                icon = Icons.Rounded.Category,
                title = "Categoría predeterminada",
                subtitle = defaultGroup ?: "Todas las categorías",
                onClick = { pickingDefault = true },
            )
            SettingsToggle(
                icon = Icons.Rounded.Schedule,
                title = "Mostrar guía de programación",
                subtitle = "Programa actual y siguiente en la lista de canales",
                checked = showGuide,
                onChange = { vm.setShowGuide(it) },
            )

            SectionLabel("Control de contenido")
            SettingsToggle(
                icon = Icons.Rounded.Lock,
                title = "Ocultar canales y películas para adultos",
                subtitle = "Quita de todas las pantallas lo que tenga marcas de contenido para adultos",
                checked = hideAdult,
                // Mostrar adultos pide el PIN si hay uno puesto.
                onChange = { wantHide ->
                    if (wantHide || !pinSet) vm.setHideAdult(wantHide) else pinMode = PinMode.VERIFY_OFF
                },
            )
            SettingsItem(
                icon = Icons.Rounded.Key,
                title = if (pinSet) "Cambiar PIN" else "Crear PIN",
                subtitle = if (pinSet) "Pide el PIN actual antes de cambiarlo" else "Protege el contenido para adultos",
                onClick = { pinMode = if (pinSet) PinMode.VERIFY_CHANGE else PinMode.CREATE },
            )
            if (pinSet) {
                SettingsItem(
                    icon = Icons.Rounded.Delete,
                    title = "Quitar PIN",
                    subtitle = "Requiere el PIN actual",
                    onClick = { pinMode = PinMode.VERIFY_REMOVE },
                    chevron = false,
                )
            }

            SectionLabel("Datos")
            SettingsItem(
                icon = Icons.Rounded.History,
                title = "Borrar canales vistos recientemente",
                subtitle = "${recent.size} guardados",
                onClick = { vm.clearRecent() },
                chevron = false,
            )
            SettingsItem(
                icon = Icons.Rounded.Delete,
                title = "Borrar progreso de películas",
                subtitle = "Quita lo que quedó a medias en \"Continuar viendo\"",
                onClick = { vm.clearProgress() },
                chevron = false,
            )

            Text(
                text = "Blaze IPTV v1.0",
                color = TextLo,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
    if (pickingDefault) {
        CategorySheet(
            groups = liveGroups,
            selected = defaultGroup,
            onPick = {
                vm.setDefaultLiveGroup(it)
                pickingDefault = false
            },
            onDismiss = { pickingDefault = false },
        )
    }
    PinDialogHost(pinMode, vm) { pinMode = it }
}

private fun folderSummary(total: Int, hidden: Int): String = when {
    total == 0 -> "Aún no hay carpetas"
    hidden == 0 -> "$total carpetas · todas visibles"
    else -> "$total carpetas · $hidden ocultas"
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = TextLo,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    chevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(22.dp))
            .pressClick(shape = RoundedCornerShape(22.dp), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Lavender.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Lavender, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextLo, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (chevron) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextLo)
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.focusRing(CircleShape)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = TextHi)
        }
        Text(
            text = title,
            color = TextHi,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Listas: editar, agregar y quitar
// ---------------------------------------------------------------------------------------------

@Composable
private fun ListsPage(vm: MainViewModel, onBack: () -> Unit) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlaylistSource?>(null) }
    var deleting by remember { mutableStateOf<PlaylistSource?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val fileName = (uri.lastPathSegment ?: "").substringAfterLast('/').substringBeforeLast('.')
            vm.addSource(fileName, uri.toString())
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageHeader("Mis listas", onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (sources.isEmpty()) {
                item {
                    Text(
                        text = "Aún no agregaste ninguna lista.",
                        color = TextLo,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
            items(sources, key = { it.id }) { src ->
                SourceRow(
                    src = src,
                    onEdit = { editing = src },
                    onDelete = { deleting = src },
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        modifier = Modifier.focusRing(),
                        onClick = { adding = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Agregar lista", modifier = Modifier.padding(start = 6.dp))
                    }
                    TextButton(
                        modifier = Modifier.focusRing(),
                        onClick = { picker.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Lavender),
                    ) { Text("Desde un archivo") }
                }
            }
            item {
                TextButton(
                    modifier = Modifier.focusRing(),
                    onClick = { if (!loading) vm.refreshAll() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Lavender),
                ) { Text(if (loading) "Actualizando…" else "Actualizar listas ahora") }
            }
            val st = status
            if (st != null) {
                item {
                    Text(st, color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }

    if (adding) {
        SourceDialog(
            title = "Agregar lista",
            initialName = "",
            initialUrl = "",
            confirmLabel = "Agregar",
            onDismiss = { adding = false },
            onConfirm = { name, url ->
                vm.addSource(name, url)
                adding = false
            },
        )
    }

    val ed = editing
    if (ed != null) {
        SourceDialog(
            title = "Editar lista",
            initialName = ed.name,
            initialUrl = ed.url,
            confirmLabel = "Guardar",
            onDismiss = { editing = null },
            onConfirm = { name, url ->
                vm.updateSource(ed.id, name, url)
                editing = null
            },
        )
    }

    val del = deleting
    if (del != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = DialogBg,
            title = { Text("¿Quitar «${del.name}»?", color = TextHi) },
            text = {
                Text(
                    "Se quitan de la app sus canales y películas. Tu lista en el proveedor no se toca.",
                    color = TextLo,
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.focusRing(),
                    onClick = {
                        vm.removeSource(del.id)
                        deleting = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Coral),
                ) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.focusRing(),
                    onClick = { deleting = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Lavender),
                ) { Text("Cancelar") }
            },
        )
    }
}

/** Muestra solo el servidor para no dejar a la vista el usuario y la clave de la lista. */
private fun urlSummary(url: String): String {
    if (url.startsWith("content://")) return "Archivo del teléfono"
    val noScheme = url.substringAfter("://", url)
    return noScheme.substringBefore('/').substringBefore('?')
}

@Composable
private fun SourceRow(src: PlaylistSource, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(22.dp))
            .padding(start = 18.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(src.name, color = TextHi, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (src.url.startsWith("http://")) "Sin cifrar · ${urlSummary(src.url)}" else urlSummary(src.url),
                color = if (src.url.startsWith("http://")) AmberWarn else TextLo,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.focusRing(CircleShape)) {
            Icon(Icons.Rounded.Edit, contentDescription = "Editar lista", tint = TextHi)
        }
        IconButton(onClick = onDelete, modifier = Modifier.focusRing(CircleShape)) {
            Icon(Icons.Rounded.Delete, contentDescription = "Quitar lista", tint = Coral)
        }
    }
}

@Composable
private fun SourceDialog(
    title: String,
    initialName: String,
    initialUrl: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    val clean = url.trim()
    val valid = clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("content://")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        title = { Text(title, color = TextHi) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogField(name, "Nombre", singleLine = true) { name = it }
                DialogField(url, "Dirección de la lista", singleLine = false) { url = it }
                Text(
                    text = "Para Xtream: http://servidor:puerto/get.php?username=USUARIO&password=CLAVE&type=m3u_plus&output=ts",
                    color = TextLo,
                    fontSize = 11.sp,
                )
                if (clean.isNotEmpty() && !valid) {
                    Text("La dirección debe empezar con http:// o https://", color = Coral, fontSize = 12.sp)
                }
                if (clean.startsWith("http://")) {
                    Text(
                        "Esta lista usa conexión sin cifrar (http). Agrégala solo si confías en el proveedor.",
                        color = AmberWarn,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.focusRing(),
                onClick = { onConfirm(name, clean) },
                enabled = valid,
                colors = ButtonDefaults.textButtonColors(contentColor = Lavender, disabledContentColor = TextLo),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.focusRing(),
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Lavender),
            ) { Text("Cancelar") }
        },
    )
}

@Composable
private fun DialogField(value: String, label: String, singleLine: Boolean, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        label = { Text(label, color = TextLo) },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Lavender,
            focusedTextColor = TextHi,
            unfocusedTextColor = TextHi,
        ),
        modifier = Modifier.fillMaxWidth().focusRing(RoundedCornerShape(16.dp), scale = false),
    )
}

// ---------------------------------------------------------------------------------------------
// Carpetas: ocultar las que no quieres ver
// ---------------------------------------------------------------------------------------------

@Composable
private fun FoldersPage(vm: MainViewModel, kind: Kind, onBack: () -> Unit) {
    val all by vm.folders.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    var query by rememberSaveable(kind) { mutableStateOf("") }

    val mine = remember(all, kind) { all.filter { it.kind == kind } }
    val shown = remember(mine, query) {
        if (query.isBlank()) mine else mine.filter { it.name.contains(query, ignoreCase = true) }
    }
    val hiddenCount = mine.count { folderKey(kind, it.name) in hidden }
    val isLive = kind == Kind.LIVE
    val unit = if (isLive) "canales" else "títulos"

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageHeader(if (isLive) "Carpetas de TV en vivo" else "Carpetas de películas y series", onBack)
        Text(
            text = "Apaga las carpetas que no quieras ver. No se borran de tu lista: puedes volver a encenderlas cuando quieras.",
            color = TextLo,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Text(
            text = "${mine.size - hiddenCount} visibles · $hiddenCount ocultas",
            color = Lavender,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
        SearchField(query, "Buscar carpeta") { query = it }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                modifier = Modifier.focusRing(),
                onClick = { vm.setFoldersHidden(kind, shown.map { it.name }, false) },
                colors = ButtonDefaults.textButtonColors(contentColor = Lavender),
            ) { Text(if (query.isBlank()) "Mostrar todas" else "Mostrar estas") }
            if (query.isNotBlank() && shown.isNotEmpty()) {
                TextButton(
                    modifier = Modifier.focusRing(),
                    onClick = { vm.setFoldersHidden(kind, shown.map { it.name }, true) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Coral),
                ) { Text("Ocultar estas (${shown.size})") }
            }
        }

        if (mine.isEmpty()) {
            Text(
                text = "Aún no hay carpetas. Agrega una lista primero.",
                color = TextLo,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.name }) { f ->
                    val visible = folderKey(kind, f.name) !in hidden
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(RoundedCornerShape(20.dp))
                            .alpha(if (visible) 1f else 0.5f)
                            .pressClick(shape = RoundedCornerShape(20.dp)) { vm.setFoldersHidden(kind, listOf(f.name), visible) }
                            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = if (visible) Lavender else TextLo,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = f.name,
                                color = if (visible) TextHi else TextLo,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("${f.count} $unit", color = TextLo, fontSize = 12.sp)
                        }
                        // Solo muestra el estado: la fila entera es el único punto de foco.
                        Switch(
                            checked = visible,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink,
                                checkedTrackColor = Lavender,
                                uncheckedThumbColor = TextLo,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                                uncheckedBorderColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(22.dp))
            .pressClick(shape = RoundedCornerShape(22.dp)) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Lavender.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Lavender, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextLo, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // Solo muestra el estado: la fila entera es el único punto de foco.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Lavender, checkedThumbColor = Ink),
        )
    }
}

private val AmberWarn = Color(0xFFFFB347)

private enum class PinMode { NONE, CREATE, VERIFY_OFF, VERIFY_CHANGE, VERIFY_REMOVE }

@Composable
private fun PinDialogHost(mode: PinMode, vm: MainViewModel, onMode: (PinMode) -> Unit) {
    when (mode) {
        PinMode.NONE -> Unit
        PinMode.CREATE -> PinDialog(
            title = if (vm.hasPin.value) "Nuevo PIN" else "Crear PIN",
            message = "Elige un PIN de 4 a 6 números.",
            confirm = true,
            onSubmit = { pin ->
                vm.setPin(pin)
                onMode(PinMode.NONE)
                null
            },
            onDismiss = { onMode(PinMode.NONE) },
        )
        PinMode.VERIFY_OFF -> PinDialog(
            title = "Mostrar adultos",
            message = "Ingresa tu PIN para mostrar el contenido para adultos.",
            confirm = false,
            onSubmit = { pin ->
                if (vm.checkPin(pin)) {
                    vm.setHideAdult(false)
                    onMode(PinMode.NONE)
                    null
                } else "PIN incorrecto"
            },
            onDismiss = { onMode(PinMode.NONE) },
        )
        PinMode.VERIFY_CHANGE -> PinDialog(
            title = "Cambiar PIN",
            message = "Ingresa tu PIN actual.",
            confirm = false,
            onSubmit = { pin ->
                if (vm.checkPin(pin)) {
                    onMode(PinMode.CREATE)
                    null
                } else "PIN incorrecto"
            },
            onDismiss = { onMode(PinMode.NONE) },
        )
        PinMode.VERIFY_REMOVE -> PinDialog(
            title = "Quitar PIN",
            message = "Ingresa tu PIN actual para quitarlo.",
            confirm = false,
            onSubmit = { pin ->
                if (vm.checkPin(pin)) {
                    vm.setPin(null)
                    onMode(PinMode.NONE)
                    null
                } else "PIN incorrecto"
            },
            onDismiss = { onMode(PinMode.NONE) },
        )
    }
}

/** Pide un PIN. [onSubmit] devuelve null si todo salió bien, o el mensaje de error. */
@Composable
private fun PinDialog(
    title: String,
    message: String,
    confirm: Boolean,
    onSubmit: (String) -> String?,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = pin.length in 4..6 && (!confirm || again.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        title = { Text(title, color = TextHi) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message, color = TextLo, fontSize = 13.sp)
                PinField(pin, "PIN") { pin = it.filter(Char::isDigit).take(6) }
                if (confirm) PinField(again, "Repetir PIN") { again = it.filter(Char::isDigit).take(6) }
                error?.let { Text(it, color = Coral, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.focusRing(),
                enabled = valid,
                onClick = {
                    error = if (confirm && pin != again) "Los PIN no coinciden" else onSubmit(pin)
                },
                colors = ButtonDefaults.textButtonColors(contentColor = Lavender, disabledContentColor = TextLo),
            ) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.focusRing(),
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Lavender),
            ) { Text("Cancelar") }
        },
    )
}

@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label, color = TextLo) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedTextColor = TextHi,
            unfocusedTextColor = TextHi,
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Lavender,
        ),
        modifier = Modifier.focusRing(RoundedCornerShape(16.dp), scale = false),
    )
}
