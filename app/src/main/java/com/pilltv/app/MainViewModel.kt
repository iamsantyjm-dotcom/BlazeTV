package com.pilltv.app

import com.pilltv.app.data.Adult
import com.pilltv.app.data.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.pilltv.app.data.Channel
import com.pilltv.app.data.ChannelCache
import com.pilltv.app.data.DetailTarget
import com.pilltv.app.data.EpgNow
import com.pilltv.app.data.Episode
import com.pilltv.app.data.Folder
import com.pilltv.app.data.Kind
import com.pilltv.app.data.M3uParser
import com.pilltv.app.data.PlaylistSource
import com.pilltv.app.data.Prefs
import com.pilltv.app.data.Progress
import com.pilltv.app.data.Series
import com.pilltv.app.data.SeriesCache
import com.pilltv.app.data.SeriesData
import com.pilltv.app.data.VodInfo
import com.pilltv.app.data.XtreamApi
import com.pilltv.app.data.XtreamEpg
import com.pilltv.app.data.folderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

/** Una pista de audio o de subtítulos que se puede elegir en el reproductor. */
data class TrackOption(val label: String, val selected: Boolean)

@OptIn(UnstableApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    // ---------- Reproductor ----------
    // Buffer grande: guarda hasta 90 s por delante (y hasta 48 MB) para aguantar los tirones de listas Xtream.
    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 30_000,
                    /* maxBufferMs = */ 90_000,
                    /* bufferForPlaybackMs = */ 2_500,
                    /* bufferForPlaybackAfterRebufferMs = */ 6_000,
                )
                .setTargetBufferBytes(48 * 1024 * 1024)
                .setBackBuffer(20_000, false)
                .build()
        )
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(20_000)
                    .setUserAgent("Mozilla/5.0 BlazePlayer")
            )
        )
        .build()

    private val _current = MutableStateFlow<Channel?>(null)
    val current: StateFlow<Channel?> = _current.asStateFlow()

    private val _playWhenReady = MutableStateFlow(false)
    val playWhenReady: StateFlow<Boolean> = _playWhenReady.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _playerOpen = MutableStateFlow(false)
    val playerOpen: StateFlow<Boolean> = _playerOpen.asStateFlow()

    private val _inPip = MutableStateFlow(false)
    val inPip: StateFlow<Boolean> = _inPip.asStateFlow()

    // Cola de reproducción: lo que se ve en el reproductor para cambiar de canal o de capítulo.
    private val _queueItems = MutableStateFlow<List<Channel>>(emptyList())
    val queueItems: StateFlow<List<Channel>> = _queueItems.asStateFlow()
    private var queue: List<Channel>
        get() = _queueItems.value
        set(value) { _queueItems.value = value }

    // Reconexión automática
    private var playbackState = Player.STATE_IDLE
    private var reconnecting = false
    private var retryCount = 0
    private var retryJob: Job? = null
    private var stallJob: Job? = null
    private var lastStartMs = 0L

    // Seguir con el siguiente capítulo al terminar uno
    private var autoNext = false

    // ---------- Audio y subtítulos ----------
    private var audioPicks: List<Pair<Tracks.Group, Int>> = emptyList()
    private var textPicks: List<Pair<Tracks.Group, Int>> = emptyList()

    private val _audioOptions = MutableStateFlow<List<TrackOption>>(emptyList())
    val audioOptions: StateFlow<List<TrackOption>> = _audioOptions.asStateFlow()

    /** El primero es siempre "Sin subtítulos". */
    private val _textOptions = MutableStateFlow<List<TrackOption>>(emptyList())
    val textOptions: StateFlow<List<TrackOption>> = _textOptions.asStateFlow()

    // ---------- Calidad de video ----------
    private var videoPicks: List<Pair<Tracks.Group, Int>> = emptyList()
    /** 0 = automática; n = la calidad n-ésima de la lista. */
    private var videoChoice = 0
    private val _videoOptions = MutableStateFlow<List<TrackOption>>(emptyList())
    val videoOptions: StateFlow<List<TrackOption>> = _videoOptions.asStateFlow()

    // ---------- Canales vistos hace poco ----------
    private val _recent = MutableStateFlow(prefs.loadRecent())

    // ---------- Ajustes (la lógica está en AppSettings) ----------
    val settings = AppSettings(prefs)
    val autoNextEpisodes = settings.autoNextEpisodes
    val landscapeStart = settings.landscapeStart
    val showGuide = settings.showGuide
    val defaultLiveGroup = settings.defaultLiveGroup
    val hideAdult = settings.hideAdult
    val miniPlayer = settings.miniPlayer
    val hasPin = settings.hasPin
    fun setAutoNextEpisodes(v: Boolean) = settings.setAutoNextEpisodes(v)
    fun setLandscapeStart(v: Boolean) = settings.setLandscapeStart(v)
    fun setShowGuide(v: Boolean) = settings.setShowGuide(v)
    fun setDefaultLiveGroup(group: String?) = settings.setDefaultLiveGroup(group)
    fun setHideAdult(v: Boolean) = settings.setHideAdult(v)
    fun setMiniPlayer(v: Boolean) = settings.setMiniPlayer(v)
    fun checkPin(pin: String): Boolean = settings.checkPin(pin)
    fun setPin(pin: String?) = settings.setPin(pin)

    fun clearRecent() {
        _recent.value = emptyList()
        prefs.saveRecent(emptyList())
    }

    fun clearProgress() {
        _progress.value = emptyMap()
        prefs.saveProgress(emptyMap())
    }
    val recent: StateFlow<List<Channel>> = _recent.asStateFlow()

    // ---------- Continuar viendo ----------
    private val _progress = MutableStateFlow(prefs.loadProgress())
    val progress: StateFlow<Map<String, Progress>> = _progress.asStateFlow()
    private var progressDirty = false

    // ---------- Listas y canales ----------
    private val _sources = MutableStateFlow(prefs.loadSources())
    val sources: StateFlow<List<PlaylistSource>> = _sources.asStateFlow()

    private val _all = MutableStateFlow<List<Channel>>(emptyList())
    private val _allSeries = MutableStateFlow<List<Series>>(emptyList())

    private val _live = MutableStateFlow<List<Channel>>(emptyList())
    // Listas visibles: si "ocultar adultos" está activo, se quitan de todas las pantallas.
    val live: StateFlow<List<Channel>> = combine(_live, settings.hideAdult) { l, hide ->
        if (hide) l.filterNot { Adult.matches(it.group, it.name) } else l
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _movies = MutableStateFlow<List<Channel>>(emptyList())
    val movies: StateFlow<List<Channel>> = combine(_movies, settings.hideAdult) { l, hide ->
        if (hide) l.filterNot { Adult.matches(it.group, it.name) } else l
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _series = MutableStateFlow<List<Series>>(emptyList())
    val series: StateFlow<List<Series>> = combine(_series, settings.hideAdult) { l, hide ->
        if (hide) l.filterNot { Adult.matches(it.group, it.name) } else l
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _favorites = MutableStateFlow(prefs.loadFavorites())
    val favorites: StateFlow<List<Channel>> = _favorites.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    // ---------- Ficha de película o serie ----------
    private val _detail = MutableStateFlow<DetailTarget?>(null)
    val detail: StateFlow<DetailTarget?> = _detail.asStateFlow()

    private val _vodInfo = MutableStateFlow<Map<String, VodInfo>>(emptyMap())
    val vodInfo: StateFlow<Map<String, VodInfo>> = _vodInfo.asStateFlow()

    private val _seriesData = MutableStateFlow<Map<String, SeriesData>>(emptyMap())
    val seriesData: StateFlow<Map<String, SeriesData>> = _seriesData.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    private val _detailFailed = MutableStateFlow(false)
    val detailFailed: StateFlow<Boolean> = _detailFailed.asStateFlow()

    private var detailJob: Job? = null

    // ---------- Carpetas ocultas ----------
    private val _hidden = MutableStateFlow(prefs.loadHidden())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    // ---------- Guía de programación (Xtream) ----------
    private val _epg = MutableStateFlow<Map<String, EpgNow>>(emptyMap())
    val epg: StateFlow<Map<String, EpgNow>> = _epg.asStateFlow()
    private val epgGate = Semaphore(4)
    // Canales cuya guía ya se está pidiendo (solo se toca desde el hilo principal): evita pedidos repetidos al hacer scroll.
    private val epgInFlight = HashSet<String>()

    /** La guía de un solo canal: así el reproductor no se redibuja cuando llega la guía de otros canales. */
    fun epgFor(url: String): Flow<EpgNow?> = _epg.map { it[url] }.distinctUntilChanged()

    // Solo una publicación de canales a la vez (se hace fuera del hilo principal).
    private val publishLock = Mutex()

    // Los guardados en disco van fuera del hilo principal y de a uno (siempre se escribe lo último).
    private val saveLock = Mutex()

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _playWhenReady.value = playWhenReady
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                this@MainViewModel.playbackState = playbackState
                when (playbackState) {
                    Player.STATE_READY -> {
                        // Volvió a andar: se reinicia la cuenta de reintentos.
                        retryCount = 0
                        reconnecting = false
                        stallJob?.cancel()
                        _playerError.value = null
                    }

                    Player.STATE_BUFFERING -> startStallWatch()

                    Player.STATE_ENDED -> {
                        stallJob?.cancel()
                        onEnded()
                    }

                    else -> stallJob?.cancel()
                }
                refreshBuffering()
            }

            override fun onTracksChanged(tracks: Tracks) {
                refreshTracks(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                val http = httpCodeOf(error)
                // Un 404 o 403 no se arregla reintentando muchas veces.
                val fatal = http != null && http in 400..499 && http != 408 && http != 429
                handleFailure(fatal, http)
            }
        })

        // Si la película trae audio en español, arranca con ese.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage("es")
            .build()

        // El último canal queda listo en la píldora (en pausa) para retomarlo.
        _current.value = prefs.loadLastChannel()

        // Cada 5 s se anota el minuto de la película o capítulo; cada 15 s se guarda en el teléfono.
        viewModelScope.launch {
            var n = 0
            while (true) {
                delay(5_000)
                if (player.isPlaying) recordProgress()
                if (++n % 3 == 0) persistProgress()
            }
        }

        if (_sources.value.isNotEmpty()) {
            // Mientras se restaura la copia guardada se muestra "cargando", no "sin canales".
            _loading.value = true
            viewModelScope.launch {
                val (restored, restoredSeries) = coroutineScope {
                    val a = async(Dispatchers.IO) { loadAllCached() }
                    val b = async(Dispatchers.IO) { loadAllSeriesCached() }
                    a.await() to b.await()
                }
                if (restored.isNotEmpty()) publish(restored, restoredSeries)
                val stale = restored.isEmpty() || _sources.value.any { src ->
                    val f = cacheFile(src.id)
                    !f.exists() || System.currentTimeMillis() - f.lastModified() > REFRESH_AFTER_MS
                }
                if (stale) {
                    refreshAll()
                } else {
                    _status.value = "${restored.size} canales (copia reciente)"
                    _loading.value = false
                }
            }
        }
    }

    private fun refreshBuffering() {
        _buffering.value = playbackState == Player.STATE_BUFFERING || reconnecting
    }

    private class Published(
        val live: List<Channel>,
        val movies: List<Channel>,
        val series: List<Series>,
        val folders: List<Folder>,
    )

    /**
     * Filtra y agrupa canales, películas y series. El trabajo pesado se hace fuera del hilo principal
     * (con listas de decenas de miles de títulos, hacerlo en la pantalla la trababa). Si no se pasan
     * [all] o [series], se usan los que ya estaban cargados.
     */
    private suspend fun publish(all: List<Channel>? = null, series: List<Series>? = null) {
        publishLock.withLock {
            val allNow = all ?: _all.value
            val seriesNow = series ?: _allSeries.value
            val hidden = _hidden.value
            val out = withContext(Dispatchers.Default) {
                // Las categorías de series se ocultan junto con las de películas (misma pantalla de Ajustes).
                val visibleSeries =
                    if (hidden.isEmpty()) seriesNow
                    else seriesNow.filter { folderKey(Kind.MOVIE, it.group) !in hidden }
                // Una sola pasada por la lista (antes eran tres).
                val live = ArrayList<Channel>(allNow.size)
                val movies = ArrayList<Channel>()
                for (c in allNow) {
                    if (hidden.isNotEmpty() && folderKey(c.kind, c.group) in hidden) continue
                    if (c.kind == Kind.LIVE) live.add(c) else if (c.kind == Kind.MOVIE) movies.add(c)
                }
                Published(
                    live = live,
                    movies = movies,
                    series = visibleSeries,
                    folders = computeFolders(allNow, seriesNow),
                )
            }
            _all.value = allNow
            _allSeries.value = seriesNow
            _series.value = out.series
            _live.value = out.live
            _movies.value = out.movies
            _folders.value = out.folders
        }
    }

    private fun computeFolders(all: List<Channel>, series: List<Series>): List<Folder> {
        val live = HashMap<String, Int>()
        val movie = HashMap<String, Int>()
        for (c in all) {
            val m = if (c.kind == Kind.MOVIE) movie else live
            m[c.group] = (m[c.group] ?: 0) + 1
        }
        for (s in series) movie[s.group] = (movie[s.group] ?: 0) + 1
        val out = ArrayList<Folder>(live.size + movie.size)
        for ((g, n) in live) out.add(Folder(Kind.LIVE, g, n))
        for ((g, n) in movie) out.add(Folder(Kind.MOVIE, g, n))
        out.sortWith(compareBy<Folder>({ it.kind.ordinal }, { it.name.lowercase() }))
        return out
    }

    /** Oculta o vuelve a mostrar carpetas. No se borra nada de la lista: solo deja de mostrarse. */
    fun setFoldersHidden(kind: Kind, names: List<String>, hide: Boolean) {
        val cur = _hidden.value.toMutableSet()
        for (n in names) {
            val k = folderKey(kind, n)
            if (hide) cur.add(k) else cur.remove(k)
        }
        _hidden.value = cur
        prefs.saveHidden(cur)
        viewModelScope.launch { publish() }
    }

    // ---------- Guía: programa actual y siguiente ----------
    suspend fun loadEpg(channel: Channel) {
        if (channel.kind != Kind.LIVE) return
        val t = System.currentTimeMillis()
        val cached = _epg.value[channel.url]
        if (cached != null) {
            val n = cached.now
            val fresh = if (n != null) n.endMs > t else t - cached.fetchedMs < EPG_RETRY_MS
            if (fresh) return
        }
        if (!XtreamEpg.supports(channel.url)) return
        if (!epgInFlight.add(channel.url)) return
        try {
            val result = epgGate.withPermit {
                withContext(Dispatchers.IO) { XtreamEpg.fetch(channel.url, System.currentTimeMillis()) }
            }
            _epg.update { it + (channel.url to result) }
        } finally {
            epgInFlight.remove(channel.url)
        }
    }

    // ---------- Audio y subtítulos ----------

    private fun trackLabel(f: Format, n: Int): String {
        val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let {
            Locale.forLanguageTag(it).getDisplayLanguage(Locale("es"))
                .replaceFirstChar { c -> c.uppercase() }
                .takeIf { name -> name.isNotBlank() }
        }
        val base = f.label?.takeIf { it.isNotBlank() } ?: lang ?: "Pista $n"
        val surround = if (f.channelCount >= 6) " 5.1" else ""
        return base + surround
    }

    private fun refreshTracks(tracks: Tracks) {
        val a = ArrayList<Pair<Tracks.Group, Int>>()
        val aOpts = ArrayList<TrackOption>()
        val t = ArrayList<Pair<Tracks.Group, Int>>()
        val tOpts = ArrayList<TrackOption>()
        val v = ArrayList<Triple<Int, Tracks.Group, Int>>()
        for (g in tracks.groups) {
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                when (g.type) {
                    C.TRACK_TYPE_AUDIO -> {
                        a.add(g to i)
                        aOpts.add(TrackOption(trackLabel(g.getTrackFormat(i), aOpts.size + 1), g.isTrackSelected(i)))
                    }

                    C.TRACK_TYPE_TEXT -> {
                        t.add(g to i)
                        tOpts.add(TrackOption(trackLabel(g.getTrackFormat(i), tOpts.size + 1), g.isTrackSelected(i)))
                    }

                    C.TRACK_TYPE_VIDEO -> v.add(Triple(g.getTrackFormat(i).height, g, i))
                }
            }
        }
        audioPicks = a
        textPicks = t
        _audioOptions.value = aOpts
        _textOptions.value =
            if (tOpts.isEmpty()) emptyList()
            else listOf(TrackOption("Sin subtítulos", tOpts.none { it.selected })) + tOpts

        // Calidades: una por altura, de mayor a menor. La opción 0 es "Automática".
        val heights = v.filter { it.first > 0 }.sortedByDescending { it.first }.distinctBy { it.first }
        videoPicks = heights.map { it.second to it.third }
        _videoOptions.value =
            if (heights.isEmpty()) emptyList()
            else listOf(TrackOption("Automática", videoChoice == 0)) +
                heights.mapIndexed { k, h -> TrackOption("${h.first}p", videoChoice == k + 1) }
    }

    /** [index] 0 = automática; 1 en adelante = una calidad fija. */
    fun selectVideo(index: Int) {
        val b = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (index > 0) {
            val (g, i) = videoPicks.getOrNull(index - 1) ?: return
            b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
        }
        videoChoice = index
        player.trackSelectionParameters = b.build()
        refreshTracks(player.currentTracks)
    }

    fun selectAudio(index: Int) {
        val (g, i) = audioPicks.getOrNull(index) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
            .build()
    }

    /** [index] 0 apaga los subtítulos; 1 en adelante son las pistas. */
    fun selectText(index: Int) {
        val b = player.trackSelectionParameters.buildUpon()
        if (index <= 0) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val (g, i) = textPicks.getOrNull(index - 1) ?: return
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
        }
        player.trackSelectionParameters = b.build()
    }

    // ---------- Reconexión automática ----------

    /** Si se queda cargando demasiado tiempo sin avanzar, se trata como un corte. */
    private fun startStallWatch() {
        stallJob?.cancel()
        val c = _current.value ?: return
        val limit = if (c.kind == Kind.LIVE) STALL_LIVE_MS else STALL_VOD_MS
        stallJob = viewModelScope.launch {
            delay(limit)
            if (player.playbackState == Player.STATE_BUFFERING && player.playWhenReady) handleFailure(false)
        }
    }

    /** Un error, un corte o un directo que termina: se vuelve a conectar solo, con espera creciente. */
    private fun handleFailure(fatalHttp: Boolean, httpCode: Int? = null) {
        val c = _current.value ?: return
        if (retryJob?.isActive == true) return
        val isLive = c.kind == Kind.LIVE

        if (!player.playWhenReady) {
            // Si el usuario lo dejó en pausa no se reconecta a escondidas.
            _playerError.value = failureMessage(isLive, httpCode, cut = true)
            return
        }

        val max = when {
            fatalHttp -> 2
            isLive -> MAX_RETRIES_LIVE
            else -> MAX_RETRIES_VOD
        }
        if (retryCount >= max) {
            reconnecting = false
            refreshBuffering()
            _playerError.value = failureMessage(isLive, httpCode, cut = false)
            return
        }

        retryCount++
        reconnecting = true
        refreshBuffering()
        val wait = (retryCount * 1_000L).coerceAtMost(5_000L)
        // En películas y capítulos se retoma en el mismo minuto; en directo se vuelve al presente
        // (no al inicio de la ventana, que es lo que pasaba con la posición 0).
        val resumeAt = if (isLive) C.TIME_UNSET else player.currentPosition.takeIf { it > 0L } ?: lastStartMs
        retryJob = viewModelScope.launch {
            delay(wait)
            if (_current.value?.url != c.url) return@launch
            player.setMediaItem(MediaItem.fromUri(c.url), resumeAt)
            player.prepare()
            player.playWhenReady = true
        }
    }

    /** Busca el código HTTP del servidor (401, 403, 404…) en la cadena de causas del error. */
    private fun httpCodeOf(error: Throwable): Int? {
        var t: Throwable? = error
        var depth = 0
        while (t != null && depth < 8) {
            if (t is HttpDataSource.InvalidResponseCodeException) return t.responseCode
            t = t.cause
            depth++
        }
        return null
    }

    /** Texto del error según lo que respondió el proveedor. [cut] es true si el usuario lo había dejado en pausa. */
    private fun failureMessage(isLive: Boolean, code: Int?, cut: Boolean): String = when (code) {
        401, 403 ->
            "El proveedor rechazó la conexión. Puede que tu cuenta esté vencida o que ya estés usando " +
                "el máximo de conexiones a la vez."

        404 ->
            if (isLive) "Este canal ya no está disponible en tu proveedor."
            else "Este video ya no está disponible en tu proveedor."

        429, 509 ->
            "Tu proveedor no permite tantas conexiones al mismo tiempo. Cierra otras pantallas o " +
                "dispositivos y vuelve a intentar."

        else ->
            if (isLive) (if (cut) "Se cortó la transmisión." else "Se perdió la conexión con el canal.")
            else "No se pudo reproducir este video."
    }

    // ---------- Continuar viendo ----------

    /** Anota hasta dónde va la película o capítulo actual. Los canales en vivo no se anotan. */
    private fun recordProgress() {
        val c = _current.value ?: return
        if (c.kind == Kind.LIVE) return
        if (player.playbackState == Player.STATE_IDLE) return
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur <= 0L) return
        val pos = player.currentPosition.coerceIn(0L, dur)
        val old = _progress.value[c.url]
        if (pos < MIN_SAVE_MS && old == null) return
        val finished = pos >= (dur * WATCHED_FRACTION).toLong()
        val p = Progress(
            url = c.url,
            posMs = pos,
            durMs = dur,
            watched = old?.watched == true || finished,
            updated = System.currentTimeMillis(),
            title = c.name,
            image = c.logo,
        )
        _progress.update { it + (c.url to p) }
        progressDirty = true
    }

    private fun persistProgress(sync: Boolean = false) {
        if (!progressDirty) return
        progressDirty = false
        if (sync) {
            prefs.saveProgress(_progress.value)
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                saveLock.withLock { prefs.saveProgress(_progress.value) }
            }
        }
    }

    /** Marca una película o capítulo como visto, o lo devuelve a "no visto" (y vuelve a empezar de cero). */
    fun setWatched(channel: Channel, watched: Boolean) {
        val old = _progress.value[channel.url]
        val p = Progress(
            url = channel.url,
            posMs = if (watched) old?.posMs ?: 0L else 0L,
            durMs = old?.durMs ?: 0L,
            watched = watched,
            updated = System.currentTimeMillis(),
            title = channel.name,
            image = channel.logo,
        )
        _progress.update { it + (channel.url to p) }
        progressDirty = true
        persistProgress()
    }

    /**
     * Retoma desde la fila "Continuar viendo". Arranca de inmediato; si es un capítulo, en segundo
     * plano se buscan los demás capítulos de la serie para que al terminar pase solo al siguiente.
     */
    fun resume(p: Progress) {
        val ch = Channel(
            name = p.title.ifBlank { "Video" },
            url = p.url,
            logo = p.image,
            group = "",
            kind = Kind.MOVIE,
        )
        play(ch, listOf(ch))

        // El título de un capítulo es "Serie · T1 E3"; una película no lleva ese " · T".
        val seriesName = p.title.substringBefore(" · T", "")
        if (seriesName.isBlank()) return
        val s = _allSeries.value.firstOrNull { it.name == seriesName } ?: return
        viewModelScope.launch {
            val episodes = episodesOf(s) ?: return@launch
            val list = episodes.map { episodeChannel(s, it) }
            // Si mientras tanto se cambió de video, no se toca nada.
            if (_current.value?.url != p.url || list.none { it.url == p.url }) return@launch
            queue = list
            autoNext = true
        }
    }

    /** Capítulos de una serie: de lo ya cargado o, si no está, de la lista del proveedor. */
    private suspend fun episodesOf(s: Series): List<Episode>? {
        val key = seriesKey(s)
        val data: SeriesData = _seriesData.value[key] ?: run {
            val acc = _sources.value.firstOrNull { it.id == s.sourceId }?.let { XtreamApi.account(it.url) }
                ?: return null
            val fetched = withContext(Dispatchers.IO) {
                runCatching { XtreamApi.seriesInfo(acc, s.id) }.getOrNull()
            } ?: return null
            _seriesData.update { it + (key to fetched) }
            fetched
        }
        // Se acepta tanto una lista simple como capítulos agrupados por temporada.
        val raw: Any = data.episodes
        val out: List<Episode> = when (raw) {
            is Map<*, *> -> raw.values.flatMap { (it as? List<*>).orEmpty().filterIsInstance<Episode>() }
            is List<*> -> raw.filterIsInstance<Episode>()
            else -> emptyList()
        }
        return out.ifEmpty { null }
    }

    /** Al terminar un video: se marca como visto y, si es un capítulo, pasa al siguiente. */
    private fun onEnded() {
        val c = _current.value ?: return
        if (c.kind == Kind.LIVE) {
            handleFailure(false)
            return
        }
        recordProgress()
        setWatched(c, true)
        if (autoNext && settings.autoNextEpisodes.value) {
            val i = queue.indexOfFirst { it.url == c.url }
            if (i >= 0 && i + 1 < queue.size) startPlayback(queue[i + 1])
        }
    }

    // ---------- Acciones de reproducción ----------
    fun play(channel: Channel, context: List<Channel>, nextOnEnd: Boolean = false) {
        queue = context
        autoNext = nextOnEnd
        startPlayback(channel)
        _playerOpen.value = true
    }

    private fun startPlayback(channel: Channel) {
        // Se guarda el minuto de lo que se estaba viendo antes de cambiar.
        recordProgress()
        persistProgress()

        retryJob?.cancel()
        stallJob?.cancel()
        retryCount = 0
        reconnecting = false

        _current.value = channel
        _playerError.value = null
        prefs.saveLastChannel(channel)

        // Cada video empieza sin pistas elegidas a mano (y con subtítulos apagados).
        videoChoice = 0
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()

        // Canales vistos hace poco: el último arriba, sin repetir, máximo 15.
        if (channel.kind == Kind.LIVE) {
            val list = (listOf(channel) + _recent.value.filter { it.url != channel.url }).take(MAX_RECENT)
            _recent.value = list
            prefs.saveRecent(list)
        }

        // Películas y capítulos arrancan donde se quedaron (si no estaban terminados).
        val start = if (channel.kind == Kind.MOVIE) {
            _progress.value[channel.url]
                ?.takeIf { !it.watched && it.posMs > MIN_SAVE_MS && (it.durMs <= 0L || it.posMs < it.durMs - 10_000L) }
                ?.posMs ?: 0L
        } else {
            C.TIME_UNSET // canales en vivo: siempre al presente
        }
        lastStartMs = if (start == C.TIME_UNSET) 0L else start
        player.setMediaItem(MediaItem.fromUri(channel.url), start)
        player.prepare()
        player.playWhenReady = true
        refreshBuffering()
    }

    fun step(delta: Int) {
        val c = _current.value ?: return
        if (queue.isEmpty()) queue = if (c.kind == Kind.MOVIE) movies.value else live.value
        if (queue.isEmpty()) return
        val i = queue.indexOfFirst { it.url == c.url }
        if (i < 0) return
        startPlayback(queue[(i + delta + queue.size) % queue.size])
    }

    fun retry() {
        _current.value?.let { startPlayback(it) }
    }

    fun togglePlay() {
        if (player.playWhenReady) {
            player.pause()
            recordProgress()
            persistProgress()
        } else if (player.mediaItemCount == 0 ||
            (_current.value?.kind == Kind.LIVE && !player.isCurrentMediaItemSeekable)
        ) {
            // Canal recordado de la sesión anterior, o directo que no admite pausa: se vuelve a conectar.
            _current.value?.let { startPlayback(it) }
        } else {
            player.prepare()
            player.play()
        }
    }

    fun pause() {
        player.pause()
        recordProgress()
        persistProgress()
    }

    fun stop() {
        recordProgress()
        persistProgress()
        retryJob?.cancel()
        stallJob?.cancel()
        reconnecting = false
        player.stop()
        player.clearMediaItems()
        _current.value = null
        _playerOpen.value = false
        _playerError.value = null
        prefs.saveLastChannel(null)
        refreshBuffering()
    }

    fun openPlayer() {
        if (_current.value != null) _playerOpen.value = true
    }

    fun closePlayer() {
        // Sin mini reproductor, al salir del reproductor se detiene el contenido.
        if (!settings.miniPlayer.value) {
            stop()
            return
        }
        recordProgress()
        persistProgress()
        _playerOpen.value = false
    }

    fun setPip(value: Boolean) {
        _inPip.value = value
    }

    // ---------- Avanzar y retroceder ----------
    /** Mueve la reproducción. Devuelve false si este canal o video no permite saltar. */
    fun seekBy(deltaMs: Long): Boolean {
        if (!player.isCurrentMediaItemSeekable) return false
        val dur = player.duration
        val maxPos = if (dur == C.TIME_UNSET) Long.MAX_VALUE else dur
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, maxPos))
        return true
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun goLive() {
        player.seekToDefaultPosition()
        player.play()
    }

    // ---------- Favoritos ----------
    fun toggleFavorite(channel: Channel) {
        val list = _favorites.value
        _favorites.value = if (list.any { it.url == channel.url }) {
            list.filterNot { it.url == channel.url }
        } else {
            list + channel
        }
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock { prefs.saveFavorites(_favorites.value) }
        }
    }

    // ---------- Fichas de películas y series ----------

    /** Clave con la que se guardan los capítulos de una serie. */
    fun seriesKey(s: Series): String = s.sourceId + ":" + s.id

    /** Una película de lista Xtream abre su ficha; las demás (M3U sueltos) se reproducen directo. */
    fun onMovieClick(m: Channel, context: List<Channel>) {
        if (XtreamApi.isVod(m.url)) openMovie(m) else play(m, context)
    }

    fun openMovie(m: Channel) {
        _detail.value = DetailTarget.Movie(m)
        _detailFailed.value = false
        if (_vodInfo.value.containsKey(m.url)) {
            _detailLoading.value = false
            return
        }
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _detailLoading.value = true
            val info = withContext(Dispatchers.IO) {
                runCatching {
                    val pair = XtreamApi.fromStreamUrl(m.url) ?: return@runCatching null
                    XtreamApi.vodInfo(pair.first, pair.second)
                }.getOrNull()
            }
            if (info != null) _vodInfo.update { it + (m.url to info) }
            _detailLoading.value = false
        }
    }

    fun openSeries(s: Series) {
        _detail.value = DetailTarget.Show(s)
        _detailFailed.value = false
        val key = seriesKey(s)
        if (_seriesData.value.containsKey(key)) {
            _detailLoading.value = false
            return
        }
        val acc = _sources.value.firstOrNull { it.id == s.sourceId }?.let { XtreamApi.account(it.url) }
        if (acc == null) {
            _detailLoading.value = false
            _detailFailed.value = true
            return
        }
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _detailLoading.value = true
            val data = withContext(Dispatchers.IO) {
                runCatching { XtreamApi.seriesInfo(acc, s.id) }.getOrNull()
            }
            if (data == null || data.episodes.isEmpty()) {
                _detailFailed.value = true
            } else {
                _seriesData.update { it + (key to data) }
            }
            _detailLoading.value = false
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _detailLoading.value = false
        _detail.value = null
    }

    /** Un capítulo como si fuera un video más (para el reproductor, el progreso y "visto"). */
    fun episodeChannel(s: Series, ep: Episode): Channel =
        Channel(
            name = "${s.name} · T${ep.season} E${ep.number}",
            url = ep.url,
            logo = ep.image ?: s.cover,
            group = s.name,
            kind = Kind.MOVIE,
            year = s.year,
        )

    /** Reproduce un capítulo; al terminar pasa solo al siguiente. */
    fun playEpisode(s: Series, ep: Episode, episodes: List<Episode>) {
        val list = episodes.map { episodeChannel(s, it) }
        play(episodeChannel(s, ep), list, nextOnEnd = true)
    }

    // ---------- Listas ----------
    fun addSource(name: String, url: String) {
        val clean = url.trim()
        val valid = clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("content://")
        if (!valid) {
            _status.value = "La dirección debe empezar con http:// o https://"
            return
        }
        val label = name.trim().ifBlank { "Lista ${_sources.value.size + 1}" }
        val src = PlaylistSource(UUID.randomUUID().toString(), label, clean)
        _sources.value = _sources.value + src
        prefs.saveSources(_sources.value)
        refreshAll()
    }

    fun updateSource(id: String, name: String, url: String) {
        val clean = url.trim()
        val valid = clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("content://")
        if (!valid) {
            _status.value = "La dirección debe empezar con http:// o https://"
            return
        }
        val old = _sources.value.firstOrNull { it.id == id } ?: return
        val label = name.trim().ifBlank { old.name }
        _sources.value = _sources.value.map { if (it.id == id) it.copy(name = label, url = clean) else it }
        prefs.saveSources(_sources.value)
        if (clean != old.url) refreshAll()
    }

    fun removeSource(id: String) {
        _sources.value = _sources.value.filterNot { it.id == id }
        prefs.saveSources(_sources.value)
        runCatching { cacheFile(id).delete() }
        runCatching { seriesFile(id).delete() }
        refreshAll()
    }

    /** Lo que se obtuvo de una lista: canales, series y si se usó la copia guardada por falta de conexión. */
    private class Loaded(val channels: List<Channel>, val series: List<Series>, val offline: Boolean)

    private var refreshJob: Job? = null

    fun refreshAll() {
        val list = _sources.value
        // Si ya había una actualización en curso se cancela: así no se pisan dos descargas a la vez.
        refreshJob?.cancel()
        if (list.isEmpty()) {
            refreshJob = viewModelScope.launch {
                publish(emptyList(), emptyList())
                _status.value = null
                _loading.value = false
            }
            return
        }
        refreshJob = viewModelScope.launch {
            _loading.value = true
            val all = ArrayList<Channel>()
            val allSeries = ArrayList<Series>()
            val failed = mutableListOf<String>()
            val offline = mutableListOf<String>()
            // Todas las listas se descargan a la vez (antes una detrás de otra); se juntan en el mismo orden.
            val results = coroutineScope {
                list.map { src ->
                    async(Dispatchers.IO) { runCatching { loadSource(src) } }
                }.awaitAll()
            }
            list.forEachIndexed { i, src ->
                val loaded = results[i].getOrNull()
                if (loaded == null) {
                    failed.add(src.name)
                } else {
                    if (loaded.offline) offline.add(src.name)
                    all.addAll(loaded.channels)
                    allSeries.addAll(loaded.series)
                }
            }
            publish(all, allSeries)
            _status.value = buildString {
                append("${_live.value.size} canales en vivo, ${_movies.value.size} películas y ${allSeries.size} series")
                if (offline.isNotEmpty()) append(". Sin conexión, se usó la copia guardada de: ${offline.joinToString()}")
                if (failed.isNotEmpty()) append(". No se pudo leer: ${failed.joinToString()}")
            }
            _loading.value = false
        }
    }

    /**
     * Si la lista es Xtream se lee de su API (más rápido y con las series agrupadas);
     * si no funciona o es un M3U normal, se lee el M3U. Sin conexión se usa la copia guardada.
     */
    private suspend fun loadSource(src: PlaylistSource): Loaded {
        try {
            var channels: List<Channel>? = null
            var series: List<Series> = emptyList()
            val acc = XtreamApi.account(src.url)
            if (acc != null) {
                val r = runCatching { XtreamApi.load(acc, src.id) }.getOrNull()
                if (r != null) {
                    channels = r.first
                    series = r.second
                }
            }
            val result = channels ?: fetchAndParse(src)
            runCatching { ChannelCache.write(cacheFile(src.id), result) }
            runCatching { SeriesCache.write(seriesFile(src.id), series) }
            return Loaded(result, series, false)
        } catch (e: Exception) {
            val f = cacheFile(src.id)
            if (!f.exists()) throw e
            val cachedSeries = runCatching { SeriesCache.read(seriesFile(src.id)) }.getOrDefault(emptyList())
            return Loaded(ChannelCache.read(f), cachedSeries, true)
        }
    }

    private fun cacheFile(id: String): File =
        File(getApplication<Application>().filesDir, "cache_$id.bin")

    private fun seriesFile(id: String): File =
        File(getApplication<Application>().filesDir, "series_$id.bin")

    private fun loadAllCached(): List<Channel> =
        _sources.value.flatMap { src ->
            val f = cacheFile(src.id)
            if (f.exists()) {
                runCatching { ChannelCache.read(f) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }

    private fun loadAllSeriesCached(): List<Series> =
        _sources.value.flatMap { src ->
            runCatching { SeriesCache.read(seriesFile(src.id)) }.getOrDefault(emptyList())
        }

    private fun fetchAndParse(src: PlaylistSource): List<Channel> {
        val ctx = getApplication<Application>()
        if (src.url.startsWith("content://")) {
            val stream = ctx.contentResolver.openInputStream(Uri.parse(src.url))
                ?: throw IllegalStateException("No se pudo abrir el archivo")
            return stream.bufferedReader().useLines { M3uParser.parse(it) }
        }
        val conn = URL(src.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 BlazePlayer")
        try {
            return conn.inputStream.bufferedReader().useLines { M3uParser.parse(it) }
        } finally {
            conn.disconnect()
        }
    }

    override fun onCleared() {
        recordProgress()
        persistProgress(sync = true)
        player.release()
        super.onCleared()
    }

    private companion object {
        const val REFRESH_AFTER_MS = 60L * 60L * 1000L
        const val EPG_RETRY_MS = 5L * 60L * 1000L

        // Reconexión: cuántas veces se reintenta y cuánto se espera antes de darlo por cortado.
        const val MAX_RETRIES_LIVE = 8
        const val MAX_RETRIES_VOD = 3
        const val STALL_LIVE_MS = 20_000L
        const val STALL_VOD_MS = 45_000L

        // Continuar viendo
        const val MIN_SAVE_MS = 5_000L
        const val WATCHED_FRACTION = 0.93

        // Canales recientes
        const val MAX_RECENT = 15
    }
}
