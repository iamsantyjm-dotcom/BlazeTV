package com.pilltv.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Selector de categorías: una sola píldora con la categoría actual. Al elegirla (OK) se abre una hoja
 * con todas en cuadrícula, así no hay que deslizar una fila interminable. Atrás cierra la hoja.
 */
@Composable
internal fun CategoryPicker(groups: List<String>, selected: String?, onPick: (String?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    // Al cerrar la hoja, el foco vuelve a la píldora para seguir navegando con el control remoto.
    val triggerFocus = remember { FocusRequester() }
    var sheetWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            sheetWasOpen = true
        } else if (sheetWasOpen) {
            sheetWasOpen = false
            runCatching { triggerFocus.requestFocus() }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .glassCard(CircleShape)
                .focusRequester(triggerFocus)
                .pressClick(shape = CircleShape) { open = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = null, tint = Lavender, modifier = Modifier.size(18.dp))
            Text(
                text = selected ?: "Todas las categorías",
                color = TextHi,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Elegir categoría",
                tint = TextLo,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(text = "${groups.size} categorías", color = TextLo, fontSize = 12.sp)
    }
    if (open) {
        CategorySheet(
            groups = groups,
            selected = selected,
            onPick = {
                onPick(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

/**
 * Lista de categorías en una ventana sencilla y fija (sin hoja deslizable): no se arrastra, no se
 * encoge y Atrás la cierra con una sola pulsación.
 */
@Composable
internal fun CategorySheet(
    groups: List<String>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Al abrir, el foco va a la primera casilla ("Todas"), que siempre está compuesta en la cuadrícula.
    val firstTile = remember { FocusRequester() }
    val panel = RoundedCornerShape(24.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LaunchedEffect(Unit) { runCatching { firstTile.requestFocus() } }
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .clip(panel)
                .background(InkSoft)
                .border(1.dp, MatteLine, panel)
                .padding(top = 12.dp, bottom = 8.dp),
        ) {
            Text(
                text = "Categorías",
                color = TextHi,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    CategoryTile("Todas", selected == null, Modifier.focusRequester(firstTile)) { onPick(null) }
                }
                items(groups, key = { it }) { g ->
                    CategoryTile(g, selected == g) { onPick(g) }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tileShape = RoundedCornerShape(18.dp)
    val fg by animateColorAsState(if (selected) Ink else TextHi, tween(260), label = "tileFg")
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .glassCard(tileShape, tint = if (selected) Lavender else null)
            .pressClick(shape = tileShape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
