package com.pilltv.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/** Ajustes de la app. Se guardan en Prefs y se exponen como flujos para la interfaz. */
class AppSettings(private val prefs: Prefs) {

    private val _autoNext = MutableStateFlow(prefs.getBool("auto_next", true))
    val autoNextEpisodes: StateFlow<Boolean> = _autoNext.asStateFlow()

    private val _landscape = MutableStateFlow(prefs.getBool("landscape_start", true))
    val landscapeStart: StateFlow<Boolean> = _landscape.asStateFlow()

    private val _showGuide = MutableStateFlow(prefs.getBool("show_guide", true))
    val showGuide: StateFlow<Boolean> = _showGuide.asStateFlow()

    private val _defaultGroup = MutableStateFlow(prefs.getString("default_live_group"))
    val defaultLiveGroup: StateFlow<String?> = _defaultGroup.asStateFlow()

    private val _miniPlayer = MutableStateFlow(prefs.getBool("mini_player", true))
    val miniPlayer: StateFlow<Boolean> = _miniPlayer.asStateFlow()

    // Formato de pantalla del reproductor: 0 = ajustar, 1 = rellenar (recorta), 2 = estirar.
    private val _resizeMode = MutableStateFlow(prefs.getString("resize_mode")?.toIntOrNull()?.coerceIn(0, 2) ?: 0)
    val resizeMode: StateFlow<Int> = _resizeMode.asStateFlow()

    private val _hideAdult = MutableStateFlow(prefs.getBool("hide_adult", true))
    val hideAdult: StateFlow<Boolean> = _hideAdult.asStateFlow()

    private val _hasPin = MutableStateFlow(prefs.getString("pin_hash") != null)
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    fun setAutoNextEpisodes(v: Boolean) {
        _autoNext.value = v
        prefs.setBool("auto_next", v)
    }

    fun setLandscapeStart(v: Boolean) {
        _landscape.value = v
        prefs.setBool("landscape_start", v)
    }

    fun setShowGuide(v: Boolean) {
        _showGuide.value = v
        prefs.setBool("show_guide", v)
    }

    fun setDefaultLiveGroup(group: String?) {
        _defaultGroup.value = group
        prefs.setString("default_live_group", group)
    }

    fun setMiniPlayer(v: Boolean) {
        _miniPlayer.value = v
        prefs.setBool("mini_player", v)
    }

    fun cycleResizeMode(): Int {
        val next = (_resizeMode.value + 1) % 3
        _resizeMode.value = next
        prefs.setString("resize_mode", next.toString())
        return next
    }

    fun setHideAdult(v: Boolean) {
        _hideAdult.value = v
        prefs.setBool("hide_adult", v)
    }

    // ---------- PIN para contenido para adultos ----------
    // Se guarda el hash con sal (SHA-256), nunca el PIN escrito.

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString("pin_salt") ?: return false
        val stored = prefs.getString("pin_hash") ?: return false
        return hashPin(pin, salt) == stored
    }

    /** [pin] null quita el PIN. */
    fun setPin(pin: String?) {
        if (pin == null) {
            prefs.setString("pin_hash", null)
            prefs.setString("pin_salt", null)
            _hasPin.value = false
            return
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs.setString("pin_salt", salt)
        prefs.setString("pin_hash", hashPin(pin, salt))
        _hasPin.value = true
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
