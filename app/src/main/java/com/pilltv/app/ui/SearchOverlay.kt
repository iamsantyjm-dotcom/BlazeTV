package com.pilltv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pilltv.app.MainViewModel

/**
 * Búsqueda a pantalla completa. No tiene animaciones propias: la pantalla padre la muestra y la
 * oculta (fundido + desplazamiento suave). [onClose] cierra la búsqueda (botón Volver o Back).
 */
@Composable
fun SearchOverlay(vm: MainViewModel, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        SearchScreen(vm, onClose = onClose)
    }
}
