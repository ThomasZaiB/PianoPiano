package com.tuapp.piano.presentation.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.tuapp.piano.presentation.ui.theme.Movimiento
import com.tuapp.piano.presentation.ui.theme.movimientoReducido

/** Cuánto se hunde cada cosa: lo pequeño se hunde más para que se note igual. */
const val ESCALA_TARJETA = 0.97f
const val ESCALA_BOTON = 0.95f
const val ESCALA_ICONO = 0.88f

/**
 * Hundimiento al tocar para cualquier componente que ya maneja su propio clic (botones,
 * chips…): se añade a su `modifier` y nada más.
 *
 * Observa el toque en la pasada inicial, antes que el componente, y sin consumirlo: el
 * botón sigue recibiendo su clic, su ripple y su accesibilidad, pero el hundimiento
 * empieza en el mismo instante en que baja el dedo, también dentro de listas con scroll
 * (donde Compose retrasa el estado "presionado" para distinguir un toque de un desplazamiento).
 * Si el dedo se mueve más que el margen de arrastre o sale del componente, vuelve a su tamaño.
 */
@Composable
fun Modifier.pulsable(habilitado: Boolean = true, escala: Float = ESCALA_BOTON): Modifier {
    var presionado by remember { mutableStateOf(false) }
    val reducido = movimientoReducido()
    val actual by animateFloatAsState(
        targetValue = if (presionado && habilitado && !reducido) escala else 1f,
        animationSpec = if (presionado) Movimiento.pulsar() else Movimiento.soltar(),
        label = "pulsable",
    )
    return this
        .pointerInput(habilitado) {
            if (!habilitado) return@pointerInput
            awaitEachGesture {
                val abajo = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                presionado = true
                val margen = viewConfiguration.touchSlop
                while (true) {
                    val cambio = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == abajo.id } ?: break
                    val p = cambio.position
                    val fuera = p.x < 0f || p.y < 0f || p.x > size.width || p.y > size.height
                    if (!cambio.pressed || fuera || (p - abajo.position).getDistance() > margen) break
                }
                presionado = false
            }
        }
        .graphicsLayer {
            scaleX = actual
            scaleY = actual
        }
}

/**
 * Tarjeta tocable completa (sin ripple): se hunde al bajar el dedo y dispara [onClick] al
 * soltarlo. Los botones que la tarjeta tenga dentro reciben su toque primero y no la hunden.
 * Se anuncia como botón en TalkBack con [etiqueta].
 *
 * [alPresionar] permite a la tarjeta cambiar algo más que la escala (color, flecha…).
 */
@Composable
fun Modifier.tocable(
    etiqueta: String,
    onClick: () -> Unit,
    escala: Float = ESCALA_TARJETA,
    alPresionar: (Boolean) -> Unit = {},
): Modifier {
    var presionado by remember { mutableStateOf(false) }
    val alTocar by rememberUpdatedState(onClick)
    val avisar by rememberUpdatedState(alPresionar)
    val reducido = movimientoReducido()
    val actual by animateFloatAsState(
        targetValue = if (presionado && !reducido) escala else 1f,
        animationSpec = if (presionado) Movimiento.pulsar() else Movimiento.soltar(),
        label = "tocable",
    )
    return this
        .graphicsLayer {
            scaleX = actual
            scaleY = actual
        }
        .semantics(mergeDescendants = true) {
            role = Role.Button
            onClick(label = etiqueta) {
                alTocar()
                true
            }
        }
        // Si el dedo sale de la tarjeta o la lista se desplaza, el toque se cancela solo.
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    presionado = true
                    avisar(true)
                    tryAwaitRelease()
                    presionado = false
                    avisar(false)
                },
                onTap = { alTocar() },
            )
        }
}
