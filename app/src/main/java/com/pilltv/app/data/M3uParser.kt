package com.pilltv.app.data

object M3uParser {

    private val vodExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "mpg", "mpeg", "m4v", "webm")
    private val vodWords = listOf("pelicula", "película", "movie", "film", "vod", "serie")

    /** Lee una lista M3U línea por línea (sin cargar el archivo completo en memoria). */
    fun parse(lines: Sequence<String>): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""
        var logo: String? = null
        var group = "Sin grupo"
        var tvgId: String? = null
        var pending = false

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pending = true
                logo = attr(line, "tvg-logo")
                group = attr(line, "group-title") ?: "Sin grupo"
                tvgId = attr(line, "tvg-id")
                val lastQuote = line.lastIndexOf('"')
                val comma = line.indexOf(',', if (lastQuote > 0) lastQuote else 0)
                name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                if (name.isBlank()) name = attr(line, "tvg-name") ?: "Canal"
            } else if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                if (pending && group == "Sin grupo") {
                    group = line.substringAfter(':').trim().ifBlank { "Sin grupo" }
                }
            } else if (line.startsWith("#")) {
                continue
            } else {
                if (pending) {
                    out.add(Channel(name, line, logo, group, tvgId, kindOf(line, group)))
                }
                pending = false
                name = ""
                logo = null
                group = "Sin grupo"
                tvgId = null
            }
        }
        return out
    }

    private fun attr(line: String, key: String): String? {
        val k = "$key=\""
        val i = line.indexOf(k)
        if (i < 0) return null
        val start = i + k.length
        val end = line.indexOf('"', start)
        if (end < 0) return null
        return line.substring(start, end).trim().takeIf { it.isNotEmpty() }
    }

    /** Distingue TV en vivo de películas/series por la ruta, la extensión o el nombre del grupo. */
    internal fun kindOf(url: String, group: String): Kind {
        val path = url.lowercase().substringBefore('?')
        if (path.contains("/movie/") || path.contains("/series/")) return Kind.MOVIE
        if (path.contains("/live/")) return Kind.LIVE
        val ext = path.substringAfterLast('.', "")
        if (ext in vodExtensions) return Kind.MOVIE
        val g = group.lowercase()
        if (vodWords.any { g.contains(it) }) return Kind.MOVIE
        return Kind.LIVE
    }
}
