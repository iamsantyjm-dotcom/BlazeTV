package com.pilltv.app.data

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Datos de acceso a un servidor Xtream Codes. [ext] es la extensión de los canales en vivo (ts o m3u8). */
data class XtreamAccount(
    val base: String,
    val user: String,
    val pass: String,
    val ext: String,
)

/**
 * Lee una lista Xtream Codes directamente de su API (player_api.php) en vez del M3U:
 * es más rápida con listas grandes y trae las series agrupadas, con carátula y sinopsis.
 */
object XtreamApi {

    private const val UA = "Mozilla/5.0 BlazePlayer"

    private val streamPattern =
        Regex("""^(https?://[^/]+)/(?:live|movie|series)/([^/]+)/([^/]+)/(\d+)(?:\.\w+)?(?:\?.*)?$""")
    private val yearPattern = Regex("""(?:19|20)\d{2}""")

    // ---------- Reconocer una lista Xtream ----------

    /** Devuelve los datos de acceso si la dirección de la lista es de tipo get.php?username=...&password=... */
    fun account(url: String): XtreamAccount? {
        val clean = url.trim()
        if (!clean.startsWith("http://", true) && !clean.startsWith("https://", true)) return null
        val marker = listOf("/get.php", "/player_api.php").firstOrNull { clean.contains(it, true) } ?: return null
        val uri = runCatching { Uri.parse(clean) }.getOrNull() ?: return null
        val user = uri.getQueryParameter("username")?.takeIf { it.isNotBlank() } ?: return null
        val pass = uri.getQueryParameter("password")?.takeIf { it.isNotBlank() } ?: return null
        val base = clean.substring(0, clean.indexOf(marker, ignoreCase = true)).trimEnd('/')
        val ext = when (uri.getQueryParameter("output")?.lowercase()) {
            "m3u8", "hls" -> "m3u8"
            else -> "ts"
        }
        return XtreamAccount(base, user, pass, ext)
    }

    /** Saca los datos de acceso y el número del contenido de la dirección de un canal, película o capítulo. */
    fun fromStreamUrl(url: String): Pair<XtreamAccount, String>? {
        val m = streamPattern.matchEntire(url) ?: return null
        return XtreamAccount(m.groupValues[1], m.groupValues[2], m.groupValues[3], "ts") to m.groupValues[4]
    }

    /** true si es una película de una lista Xtream (por lo tanto se le puede pedir sinopsis). */
    fun isVod(url: String): Boolean = url.contains("/movie/") && streamPattern.matches(url)

    // ---------- Carga completa de la lista ----------

    /** Descarga en paralelo canales, películas y series. Si no llega nada, lanza error para usar el M3U. */
    suspend fun load(acc: XtreamAccount, sourceId: String): Pair<List<Channel>, List<Series>> = coroutineScope {
        val liveCats = async(Dispatchers.IO) { runCatching { categories(acc, "get_live_categories") }.getOrNull() }
        val vodCats = async(Dispatchers.IO) { runCatching { categories(acc, "get_vod_categories") }.getOrNull() }
        val serCats = async(Dispatchers.IO) { runCatching { categories(acc, "get_series_categories") }.getOrNull() }
        val live = async(Dispatchers.IO) { runCatching { liveStreams(acc, liveCats.await().orEmpty()) }.getOrNull() }
        val vod = async(Dispatchers.IO) { runCatching { vodStreams(acc, vodCats.await().orEmpty()) }.getOrNull() }
        val ser = async(Dispatchers.IO) { runCatching { seriesList(acc, sourceId, serCats.await().orEmpty()) }.getOrNull() }

        val liveList = live.await()
        val vodList = vod.await()
        val serList = ser.await()
        if (liveList.isNullOrEmpty() && vodList.isNullOrEmpty() && serList.isNullOrEmpty()) {
            throw IOException("La API de Xtream no devolvió contenido")
        }
        val channels = ArrayList<Channel>((liveList?.size ?: 0) + (vodList?.size ?: 0))
        liveList?.let { channels.addAll(it) }
        vodList?.let { channels.addAll(it) }
        channels to (serList ?: emptyList())
    }

    private fun categories(acc: XtreamAccount, action: String): Map<String, String> =
        request(acc, action) { r ->
            val out = HashMap<String, String>()
            readObjects(r) { m ->
                val id = m["category_id"]
                val name = m["category_name"]
                if (id != null && !name.isNullOrBlank()) out[id] = name.trim()
            }
            out
        }

    private fun liveStreams(acc: XtreamAccount, cats: Map<String, String>): List<Channel> =
        request(acc, "get_live_streams") { r ->
            val out = ArrayList<Channel>()
            readObjects(r) { m ->
                val id = m["stream_id"] ?: return@readObjects
                val name = m["name"]?.trim().orEmpty().ifBlank { "Canal" }
                out.add(
                    Channel(
                        name = name,
                        url = "${acc.base}/live/${acc.user}/${acc.pass}/$id.${acc.ext}",
                        logo = clean(m["stream_icon"]),
                        group = cats[m["category_id"]] ?: "Sin grupo",
                        tvgId = clean(m["epg_channel_id"]),
                        kind = Kind.LIVE,
                    )
                )
            }
            out
        }

    private fun vodStreams(acc: XtreamAccount, cats: Map<String, String>): List<Channel> =
        request(acc, "get_vod_streams") { r ->
            val out = ArrayList<Channel>()
            readObjects(r) { m ->
                val id = m["stream_id"] ?: return@readObjects
                val name = m["name"]?.trim().orEmpty().ifBlank { "Película" }
                val ext = clean(m["container_extension"]) ?: "mp4"
                out.add(
                    Channel(
                        name = name,
                        url = "${acc.base}/movie/${acc.user}/${acc.pass}/$id.$ext",
                        logo = clean(m["stream_icon"]),
                        group = cats[m["category_id"]] ?: "Sin grupo",
                        kind = Kind.MOVIE,
                        year = yearOf(m["year"], m["releasedate"], m["releaseDate"]) ?: yearOf(name),
                        rating = ratingOf(m["rating"]),
                    )
                )
            }
            out
        }

    private fun seriesList(acc: XtreamAccount, sourceId: String, cats: Map<String, String>): List<Series> =
        request(acc, "get_series") { r ->
            val out = ArrayList<Series>()
            readObjects(r) { m ->
                val id = m["series_id"] ?: return@readObjects
                val name = m["name"]?.trim().orEmpty().ifBlank { "Serie" }
                out.add(
                    Series(
                        sourceId = sourceId,
                        id = id,
                        name = name,
                        cover = clean(m["cover"]),
                        plot = clean(m["plot"]),
                        year = yearOf(m["year"], m["releaseDate"], m["release_date"]) ?: yearOf(name),
                        rating = ratingOf(m["rating"]),
                        group = cats[m["category_id"]] ?: "Sin grupo",
                    )
                )
            }
            out
        }

    // ---------- Detalle (se pide solo al abrir una película o una serie) ----------

    fun vodInfo(acc: XtreamAccount, vodId: String): VodInfo? {
        val o = JSONObject(text(acc, "get_vod_info", "&vod_id=$vodId"))
        val info = o.optJSONObject("info") ?: return null
        return VodInfo(
            plot = str(info, "plot") ?: str(info, "description"),
            year = yearOf(str(info, "releasedate"), str(info, "release_date"), str(info, "year")),
            rating = ratingOf(str(info, "rating")),
            genre = str(info, "genre"),
            cast = str(info, "cast") ?: str(info, "actors"),
            director = str(info, "director"),
            duration = str(info, "duration"),
            cover = str(info, "movie_image") ?: str(info, "cover_big"),
        )
    }

    fun seriesInfo(acc: XtreamAccount, seriesId: String): SeriesData {
        val o = JSONObject(text(acc, "get_series_info", "&series_id=$seriesId"))
        val info = o.optJSONObject("info")
        val eps = ArrayList<Episode>()

        fun add(e: JSONObject, seasonHint: Int) {
            val id = str(e, "id") ?: return
            val season = e.optInt("season", seasonHint).takeIf { it > 0 } ?: seasonHint
            val number = e.optInt("episode_num", 0)
            val ext = str(e, "container_extension") ?: "mp4"
            val epInfo = e.optJSONObject("info")
            eps.add(
                Episode(
                    id = id,
                    season = season,
                    number = number,
                    title = str(e, "title") ?: "Episodio $number",
                    url = "${acc.base}/series/${acc.user}/${acc.pass}/$id.$ext",
                    image = epInfo?.let { str(it, "movie_image") },
                    plot = epInfo?.let { str(it, "plot") },
                    duration = epInfo?.let { str(it, "duration") },
                )
            )
        }

        when (val raw = o.opt("episodes")) {
            is JSONObject -> raw.keys().forEach { key ->
                val arr = raw.optJSONArray(key) ?: return@forEach
                val season = key.toIntOrNull() ?: 1
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(it, season) }
            }
            is JSONArray -> for (i in 0 until raw.length()) {
                when (val el = raw.opt(i)) {
                    is JSONArray -> for (j in 0 until el.length()) el.optJSONObject(j)?.let { add(it, i + 1) }
                    is JSONObject -> add(el, 1)
                }
            }
        }
        eps.sortWith(compareBy<Episode>({ it.season }, { it.number }))

        return SeriesData(
            plot = info?.let { str(it, "plot") },
            year = info?.let { yearOf(str(it, "releaseDate"), str(it, "release_date"), str(it, "year")) },
            rating = info?.let { ratingOf(str(it, "rating")) },
            genre = info?.let { str(it, "genre") },
            cast = info?.let { str(it, "cast") },
            episodes = eps,
        )
    }

    // ---------- Utilidades ----------

    private fun apiUrl(acc: XtreamAccount, action: String, extra: String = ""): String =
        "${acc.base}/player_api.php?username=${Uri.encode(acc.user)}&password=${Uri.encode(acc.pass)}" +
            "&action=$action$extra"

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", UA)
        return conn
    }

    private fun text(acc: XtreamAccount, action: String, extra: String = ""): String {
        val conn = open(apiUrl(acc, action, extra))
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Lectura en flujo: las listas grandes (decenas de miles de canales) no se cargan enteras en memoria. */
    private fun <T> request(acc: XtreamAccount, action: String, block: (JsonReader) -> T): T {
        val conn = open(apiUrl(acc, action))
        return try {
            JsonReader(InputStreamReader(conn.inputStream, Charsets.UTF_8).buffered()).use { r ->
                r.isLenient = true
                block(r)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun readObjects(r: JsonReader, each: (Map<String, String?>) -> Unit) {
        if (r.peek() != JsonToken.BEGIN_ARRAY) {
            r.skipValue()
            return
        }
        val m = HashMap<String, String?>(24)
        r.beginArray()
        while (r.hasNext()) {
            if (r.peek() != JsonToken.BEGIN_OBJECT) {
                r.skipValue()
                continue
            }
            m.clear()
            r.beginObject()
            while (r.hasNext()) {
                val key = r.nextName()
                m[key] = scalar(r)
            }
            r.endObject()
            each(m)
        }
        r.endArray()
    }

    private fun scalar(r: JsonReader): String? = when (r.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> r.nextString()
        JsonToken.BOOLEAN -> r.nextBoolean().toString()
        JsonToken.NULL -> {
            r.nextNull()
            null
        }
        else -> {
            r.skipValue()
            null
        }
    }

    private fun clean(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun str(o: JSONObject, key: String): String? =
        if (o.isNull(key)) null else clean(o.optString(key))

    /** Primer año (19xx o 20xx) que aparezca en los textos dados. */
    private fun yearOf(vararg texts: String?): String? {
        for (t in texts) {
            val y = t?.let { yearPattern.find(it)?.value }
            if (y != null) return y
        }
        return null
    }

    private fun ratingOf(raw: String?): String? {
        val v = raw?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return null
        return if (v > 0.0) String.format(java.util.Locale.US, "%.1f", v) else null
    }
}
