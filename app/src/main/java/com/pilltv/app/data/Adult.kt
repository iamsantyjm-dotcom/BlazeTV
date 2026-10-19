package com.pilltv.app.data

/** Detecta canales y películas para adultos por palabras en el nombre o la categoría. */
object Adult {
    private val words = listOf("adult", "xxx", "+18", "18+", "porn", "erotic", "erótic")

    fun matches(group: String, name: String): Boolean {
        val text = (group + " " + name).lowercase()
        return words.any { text.contains(it) }
    }
}
