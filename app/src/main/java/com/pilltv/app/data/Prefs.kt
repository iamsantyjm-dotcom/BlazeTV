package com.pilltv.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("pilltv_prefs", Context.MODE_PRIVATE)

    private fun channelToJson(c: Channel): JSONObject =
        JSONObject()
            .put("name", c.name)
            .put("url", c.url)
            .put("logo", c.logo ?: "")
            .put("group", c.group)
            .put("tvg", c.tvgId ?: "")
            .put("kind", c.kind.name)
            .put("year", c.year ?: "")
            .put("rating", c.rating ?: "")

    private fun channelFromJson(o: JSONObject): Channel =
        Channel(
            name = o.getString("name"),
            url = o.getString("url"),
            logo = o.optString("logo", "").ifBlank { null },
            group = o.optString("group", "Sin grupo"),
            tvgId = o.optString("tvg", "").ifBlank { null },
            kind = runCatching { Kind.valueOf(o.optString("kind", "LIVE")) }.getOrDefault(Kind.LIVE),
            year = o.optString("year", "").ifBlank { null },
            rating = o.optString("rating", "").ifBlank { null },
        )

    fun loadSources(): List<PlaylistSource> = runCatching {
        val arr = JSONArray(sp.getString("sources", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            PlaylistSource(o.getString("id"), o.getString("name"), o.getString("url"))
        }
    }.getOrDefault(emptyList())

    fun saveSources(list: List<PlaylistSource>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url))
        }
        sp.edit().putString("sources", arr.toString()).apply()
    }

    fun loadHidden(): Set<String> = runCatching {
        val arr = JSONArray(sp.getString("hidden_folders", "[]"))
        val out = HashSet<String>()
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        out.toSet()
    }.getOrDefault(emptySet())

    fun saveHidden(set: Set<String>) {
        val arr = JSONArray()
        set.forEach { arr.put(it) }
        sp.edit().putString("hidden_folders", arr.toString()).apply()
    }

    fun loadFavorites(): List<Channel> = runCatching {
        val arr = JSONArray(sp.getString("favorites", "[]"))
        List(arr.length()) { i -> channelFromJson(arr.getJSONObject(i)) }
    }.getOrDefault(emptyList())

    fun saveFavorites(list: List<Channel>) {
        val arr = JSONArray()
        list.forEach { arr.put(channelToJson(it)) }
        sp.edit().putString("favorites", arr.toString()).apply()
    }

    fun loadLastChannel(): Channel? = runCatching {
        val raw = sp.getString("last_channel", null)
        if (raw == null) null else channelFromJson(JSONObject(raw))
    }.getOrNull()

    fun saveLastChannel(channel: Channel?) {
        if (channel == null) {
            sp.edit().remove("last_channel").apply()
        } else {
            sp.edit().putString("last_channel", channelToJson(channel).toString()).apply()
        }
    }

    // ---------- Canales vistos hace poco ----------

    fun loadRecent(): List<Channel> = runCatching {
        val arr = JSONArray(sp.getString("recent_channels", "[]"))
        List(arr.length()) { i -> channelFromJson(arr.getJSONObject(i)) }
    }.getOrDefault(emptyList())

    fun saveRecent(list: List<Channel>) {
        val arr = JSONArray()
        list.forEach { arr.put(channelToJson(it)) }
        sp.edit().putString("recent_channels", arr.toString()).apply()
    }

    // ---------- Continuar viendo (minuto de cada película o capítulo y lo ya visto) ----------

    fun loadProgress(): Map<String, Progress> = runCatching {
        val arr = JSONArray(sp.getString("progress", "[]"))
        val out = HashMap<String, Progress>(arr.length() * 2)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val url = o.getString("url")
            out[url] = Progress(
                url = url,
                posMs = o.optLong("pos", 0L),
                durMs = o.optLong("dur", 0L),
                watched = o.optBoolean("watched", false),
                updated = o.optLong("updated", 0L),
                title = o.optString("title", ""),
                image = o.optString("image", "").ifBlank { null },
            )
        }
        out
    }.getOrDefault(emptyMap())

    /** Guarda solo los [MAX_PROGRESS] más recientes para que el archivo no crezca sin límite. */
    fun saveProgress(map: Map<String, Progress>) {
        val arr = JSONArray()
        map.values.sortedByDescending { it.updated }.take(MAX_PROGRESS).forEach {
            arr.put(
                JSONObject()
                    .put("url", it.url)
                    .put("pos", it.posMs)
                    .put("dur", it.durMs)
                    .put("watched", it.watched)
                    .put("updated", it.updated)
                    .put("title", it.title)
                    .put("image", it.image ?: "")
            )
        }
        sp.edit().putString("progress", arr.toString()).apply()
    }

    private companion object {
        const val MAX_PROGRESS = 500
    }

    // ---------- Ajustes generales ----------

    fun getBool(key: String, default: Boolean): Boolean = sp.getBoolean(key, default)

    fun setBool(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
    }

    fun getString(key: String): String? = sp.getString(key, null)

    fun setString(key: String, value: String?) {
        val e = sp.edit()
        if (value == null) e.remove(key) else e.putString(key, value)
        e.apply()
    }
}
