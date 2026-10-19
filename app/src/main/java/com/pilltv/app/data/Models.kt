package com.pilltv.app.data

enum class Kind { LIVE, MOVIE }

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
    val tvgId: String? = null,
    val kind: Kind = Kind.LIVE,
    val year: String? = null,
    val rating: String? = null,
)

data class PlaylistSource(
    val id: String,
    val name: String,
    val url: String,
)

/** Una carpeta (grupo) de la lista, con la cantidad de canales o películas que trae. */
data class Folder(
    val kind: Kind,
    val name: String,
    val count: Int,
)

/** Clave con la que se guarda qué carpetas están ocultas. */
fun folderKey(kind: Kind, group: String): String = kind.name + "|" + group

/** Un programa de la guía. Las horas van en milisegundos desde 1970 (UTC). */
data class EpgProgram(
    val title: String,
    val startMs: Long,
    val endMs: Long,
)

/** Lo que está al aire y lo que sigue en un canal. */
data class EpgNow(
    val now: EpgProgram?,
    val next: EpgProgram?,
    val fetchedMs: Long,
)

// ---------- Series (listas Xtream) ----------

/** Una serie completa (no un capítulo suelto). Los capítulos se piden al abrirla. */
data class Series(
    val sourceId: String,
    val id: String,
    val name: String,
    val cover: String?,
    val plot: String?,
    val year: String?,
    val rating: String?,
    val group: String,
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val url: String,
    val image: String?,
    val plot: String?,
    val duration: String?,
)

/** Detalle de una serie: sinopsis y todos sus capítulos. */
data class SeriesData(
    val plot: String?,
    val year: String?,
    val rating: String?,
    val genre: String?,
    val cast: String?,
    val episodes: List<Episode>,
) {
    /** Números de temporada que existen, de menor a mayor. */
    fun seasons(): List<Int> = episodes.map { it.season }.distinct().sorted()
}

/** Detalle de una película (sinopsis, reparto, duración...). */
data class VodInfo(
    val plot: String?,
    val year: String?,
    val rating: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val duration: String?,
    val cover: String?,
)

/** Qué ficha está abierta: una película o una serie. */
sealed interface DetailTarget {
    data class Movie(val channel: Channel) : DetailTarget
    data class Show(val series: Series) : DetailTarget
}

// ---------- Continuar viendo ----------

/** Hasta dónde se vio una película o capítulo. [title] e [image] sirven para la fila "Continuar viendo". */
data class Progress(
    val url: String,
    val posMs: Long,
    val durMs: Long,
    val watched: Boolean,
    val updated: Long,
    val title: String = "",
    val image: String? = null,
) {
    /** Fracción vista, de 0 a 1. */
    fun fraction(): Float = if (durMs > 0L) (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0f
}
