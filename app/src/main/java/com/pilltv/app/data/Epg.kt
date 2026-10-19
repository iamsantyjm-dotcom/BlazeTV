package com.pilltv.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/**
 * Guía de programación de listas Xtream Codes.
 *
 * La dirección de un canal Xtream tiene esta forma:
 *   http://servidor:puerto/live/USUARIO/CLAVE/12345.ts
 * De ahí se sacan el servidor, el usuario, la clave y el número del canal, y se le pregunta al servidor
 * (player_api.php, acción get_short_epg) qué programa está al aire y cuál sigue.
 */
object XtreamEpg {

    private val pattern = Regex("""^(https?://[^/]+)(?:/live)?/([^/]+)/([^/]+)/(\d+)(?:\.\w+)?(?:\?.*)?$""")

    /** true si la dirección del canal parece de una lista Xtream (y por lo tanto puede tener guía). */
    fun supports(url: String): Boolean = pattern.matches(url)

    fun fetch(channelUrl: String, nowMs: Long): EpgNow {
        val m = pattern.matchEntire(channelUrl) ?: return EpgNow(null, null, nowMs)
        val base = m.groupValues[1]
        val user = m.groupValues[2]
        val pass = m.groupValues[3]
        val id = m.groupValues[4]
        return try {
            val api = "$base/player_api.php?username=$user&password=$pass&action=get_short_epg&stream_id=$id&limit=4"
            val conn = URL(api).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 BlazePlayer")
            val text = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            parse(text, nowMs)
        } catch (e: Exception) {
            EpgNow(null, null, nowMs)
        }
    }

    internal fun parse(text: String, nowMs: Long): EpgNow {
        val arr = JSONObject(text).optJSONArray("epg_listings") ?: return EpgNow(null, null, nowMs)
        val list = ArrayList<EpgProgram>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val start = o.optString("start_timestamp", "").toLongOrNull() ?: continue
            val end = o.optString("stop_timestamp", "").toLongOrNull() ?: continue
            val title = decodeTitle(o.optString("title", ""))
            if (title.isBlank()) continue
            list.add(EpgProgram(title, start * 1000L, end * 1000L))
        }
        list.sortBy { it.startMs }
        val now = list.firstOrNull { it.startMs <= nowMs && nowMs < it.endMs }
        val after = now?.endMs ?: (nowMs + 1)
        val next = list.firstOrNull { it.startMs >= after }
        return EpgNow(now, next, nowMs)
    }

    /** Casi todos los servidores mandan el título en base64; algunos lo mandan en texto normal. */
    private fun decodeTitle(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return ""
        return try {
            val bytes = Base64.getDecoder().decode(s)
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString().trim()
            if (decoded.isNotEmpty() && decoded.none { it.isISOControl() }) decoded else s
        } catch (e: Exception) {
            s
        }
    }
}
