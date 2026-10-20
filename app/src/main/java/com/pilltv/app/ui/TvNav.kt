package com.pilltv.app.ui

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Navegación vertical propia para control remoto (TV).
 *
 * Cada botón o tarjeta enfocable (pressClick / focusRing) se registra aquí con su posición en
 * pantalla. Al pulsar Arriba o Abajo se elige el elemento más cercano en esa dirección, se le pasa
 * el foco directamente y, si queda cerca del borde, la lista se desplaza para dejarlo a la vista.
 * Así la navegación no depende de la búsqueda de foco automática de Compose, que en listas y
 * cuadrículas largas dejaba el indicador "perdido".
 */
class TvEntry {
    val requester = FocusRequester()
    val bringIntoView = BringIntoViewRequester()
    var rect: Rect = Rect.Zero
    var focused: Boolean = false
    var enabled: Boolean = true
}

class TvNav(private val scope: CoroutineScope) {
    private val entries = ArrayList<TvEntry>()

    /** Alto de la ventana y márgenes (en píxeles) donde se considera que el elemento "está en el borde". */
    var viewportH: Float = 0f
    var topMargin: Float = 0f
    var bottomMargin: Float = 0f

    fun add(e: TvEntry) {
        if (!entries.contains(e)) entries.add(e)
    }

    fun remove(e: TvEntry) {
        entries.remove(e)
    }

    /** Mueve el foco hacia abajo o hacia arriba. Devuelve true si la pulsación quedó atendida. */
    fun move(down: Boolean): Boolean {
        val usable = entries.filter { it.enabled && it.rect.width > 0f && it.rect.height > 0f }
        if (usable.isEmpty()) return false
        val cur = usable.firstOrNull { it.focused }

        val ordered: List<TvEntry> = if (cur == null) {
            // Sin nada enfocado: la primera flecha enfoca lo más alto de lo que se ve.
            usable
                .filter { it.rect.bottom > 0f && (viewportH <= 0f || it.rect.top < viewportH) }
                .sortedWith(compareBy<TvEntry>({ it.rect.top }, { it.rect.left }))
        } else {
            val c = cur.rect
            usable
                .filter { it !== cur }
                .filter { if (down) it.rect.center.y > c.center.y + 4f else it.rect.center.y < c.center.y - 4f }
                .sortedBy { e ->
                    val r = e.rect
                    val dy = abs(r.center.y - c.center.y)
                    // Distancia horizontal entre ambos (0 si se solapan): prefiere el que queda justo debajo/encima.
                    val gapX = max(0f, max(r.left, c.left) - min(r.right, c.right))
                    dy + 3f * gapX
                }
        }

        for (t in ordered.take(6)) {
            val r: Any? = try {
                t.requester.requestFocus()
            } catch (_: Throwable) {
                false
            }
            if (r != false) {
                reveal(t, down)
                return true
            }
        }
        // No hay nada más en esa dirección: la pulsación se queda aquí y no salta a otro lado.
        return cur != null
    }

    private fun reveal(t: TvEntry, down: Boolean) {
        val r = t.rect
        if (down && r.bottom > viewportH - bottomMargin) {
            scope.launch {
                runCatching { t.bringIntoView.bringIntoView(Rect(0f, 0f, r.width, r.height + bottomMargin)) }
            }
        } else if (!down && r.top < topMargin) {
            scope.launch {
                runCatching { t.bringIntoView.bringIntoView(Rect(0f, -topMargin, r.width, r.height)) }
            }
        }
    }
}

val LocalTvNav = compositionLocalOf<TvNav?> { null }

@Composable
fun rememberTvNav(topMargin: Dp, bottomMargin: Dp): TvNav {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val nav = remember { TvNav(scope) }
    nav.topMargin = with(density) { topMargin.toPx() }
    nav.bottomMargin = with(density) { bottomMargin.toPx() }
    return nav
}

/** Atiende las flechas Arriba y Abajo con la navegación propia. Va en la raíz de una pantalla o ventana. */
fun Modifier.tvNavKeys(nav: TvNav): Modifier = this.onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) {
        false
    } else {
        when (e.key) {
            Key.DirectionDown -> nav.move(true)
            Key.DirectionUp -> nav.move(false)
            else -> false
        }
    }
}

/** Registra este elemento en la navegación de la pantalla. Lo usan pressClick y focusRing. */
@Composable
fun Modifier.tvEntry(enabled: Boolean = true): Modifier {
    val nav = LocalTvNav.current ?: return this
    val entry = remember { TvEntry() }
    entry.enabled = enabled
    DisposableEffect(nav, entry) {
        nav.add(entry)
        onDispose { nav.remove(entry) }
    }
    return this
        .onFocusChanged { entry.focused = it.isFocused }
        .focusRequester(entry.requester)
        .bringIntoViewRequester(entry.bringIntoView)
        .onGloballyPositioned { entry.rect = it.boundsInWindow() }
}
