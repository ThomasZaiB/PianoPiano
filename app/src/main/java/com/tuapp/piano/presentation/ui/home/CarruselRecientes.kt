package com.tuapp.piano.presentation.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tuapp.piano.domain.inicio.CancionInicio
import com.tuapp.piano.presentation.ui.common.tocable
import com.tuapp.piano.presentation.ui.theme.Espacio
import com.tuapp.piano.presentation.ui.theme.Movimiento
import com.tuapp.piano.presentation.ui.theme.PianoColors
import com.tuapp.piano.presentation.ui.theme.movimientoReducido
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ANCHO_TARJETA = 148.dp
private val ALTO_TARJETA = 128.dp
private val SEPARACION = 12.dp

/**
 * Carrusel horizontal de canciones con física de iOS:
 *  - Sigue al dedo 1:1 desde donde se agarró, también a mitad de un movimiento.
 *  - Al soltar proyecta el impulso ([FisicaCarrusel.proyectar]) y se detiene en la tarjeta
 *    más cercana a ese punto, partiendo con la velocidad exacta del dedo.
 *  - Más allá de los extremos ofrece resistencia elástica en vez de frenar en seco.
 *  - Con movimiento reducido no hay inercia ni rebote: se coloca directamente.
 */
@Composable
fun CarruselRecientes(
    canciones: List<CancionInicio>,
    onAbrir: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val densidad = LocalDensity.current
        val visible = with(densidad) { maxWidth.toPx() }
        val paso = with(densidad) { (ANCHO_TARJETA + SEPARACION).toPx() }
        val anchoContenido = with(densidad) {
            canciones.size * ANCHO_TARJETA.toPx() + (canciones.size - 1).coerceAtLeast(0) * SEPARACION.toPx()
        }
        val maximo = (anchoContenido - visible).coerceAtLeast(0f)
        val reducido = movimientoReducido()

        val posicion = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()

        // Si cambia el número de canciones o el ancho (al girar), no quedarse fuera de rango.
        LaunchedEffect(maximo) { if (posicion.value > maximo) posicion.snapTo(maximo) }

        fun destino(indice: Int) = (indice * paso).coerceAtMost(maximo)
        fun asentar(velocidad: Float) {
            val objetivo = destino(FisicaCarrusel.indiceDestino(posicion.value, velocidad, paso, canciones.size))
            scope.launch {
                if (reducido) {
                    posicion.snapTo(objetivo)
                } else {
                    // Venía de un lanzamiento: algo de rebote (amortiguación 0.8), como en iOS.
                    posicion.animateTo(objetivo, Movimiento.resorte(respuestaSegundos = 0.4f, amortiguacion = 0.8f), initialVelocity = velocidad)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(ALTO_TARJETA)
                .clipToBounds()
                .semantics {
                    horizontalScrollAxisRange = ScrollAxisRange(value = { posicion.value }, maxValue = { maximo })
                    scrollBy { x, _ ->
                        scope.launch { posicion.animateTo((posicion.value + x).coerceIn(0f, maximo), Movimiento.resorte()) }
                        true
                    }
                }
                .pointerInput(maximo, paso) {
                    awaitEachGesture {
                        val abajo = awaitFirstDown(requireUnconsumed = false)
                        // Agarrar a mitad de camino: se detiene donde se ve, sin saltos.
                        scope.launch { posicion.stop() }
                        val rastreador = VelocityTracker()
                        rastreador.addPosition(abajo.uptimeMillis, abajo.position)
                        var cruda = posicion.value
                        val arranque = awaitHorizontalTouchSlopOrCancellation(abajo.id) { cambio, _ ->
                            cambio.consume()
                        } ?: return@awaitEachGesture
                        rastreador.addPosition(arranque.uptimeMillis, arranque.position)
                        val completo = horizontalDrag(arranque.id) { cambio ->
                            cruda -= cambio.positionChange().x
                            val mostrada = FisicaCarrusel.conBordesElasticos(cruda, 0f, maximo, visible)
                            scope.launch { posicion.snapTo(mostrada) }
                            rastreador.addPosition(cambio.uptimeMillis, cambio.position)
                            cambio.consume()
                        }
                        // El contenido se mueve al revés que el dedo.
                        val velocidad = if (completo) -rastreador.calculateVelocity().x else 0f
                        asentar(velocidad)
                    }
                },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SEPARACION),
                modifier = Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .offset { IntOffset(-posicion.value.roundToInt(), 0) },
            ) {
                canciones.forEach { c -> TarjetaReciente(c, onAbrir = { onAbrir(c.resumen.id) }) }
            }
            // Borde que se desvanece donde sigue habiendo contenido, en vez de un corte seco.
            if (posicion.value > 1f) Desvanecido(Modifier.align(Alignment.CenterStart), haciaLaDerecha = true)
            if (posicion.value < maximo - 1f) Desvanecido(Modifier.align(Alignment.CenterEnd), haciaLaDerecha = false)
        }
    }
}

@Composable
private fun Desvanecido(modifier: Modifier, haciaLaDerecha: Boolean) {
    val colores = listOf(PianoColors.Fondo, Color.Transparent)
    Box(
        modifier
            .width(Espacio.xxl)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(if (haciaLaDerecha) colores else colores.reversed())),
    )
}

@Composable
private fun TarjetaReciente(c: CancionInicio, onAbrir: () -> Unit) {
    var presionada by remember { mutableStateOf(false) }
    val fondo by animateColorAsState(
        targetValue = if (presionada) PianoColors.SuperficieAlta else PianoColors.Superficie,
        animationSpec = if (presionada) Movimiento.pulsar() else Movimiento.soltar(),
        label = "fondo",
    )
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = fondo),
        modifier = Modifier
            .width(ANCHO_TARJETA)
            .height(ALTO_TARJETA)
            .tocable(etiqueta = c.resumen.titulo, onClick = onAbrir, alPresionar = { presionada = it }),
    ) {
        Column(Modifier.padding(Espacio.m)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(PianoColors.ManoDerecha.copy(alpha = if (presionada) 0.22f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = PianoColors.ManoDerecha, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                c.resumen.titulo,
                color = PianoColors.Texto,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Espacio.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    duracionCorta(c.resumen.duracionSegundos),
                    color = PianoColors.TextoSecundario,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                Estrellas(c.mejorPrecision, tamano = 12.dp)
            }
        }
    }
}
