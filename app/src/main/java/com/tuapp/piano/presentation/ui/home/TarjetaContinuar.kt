package com.tuapp.piano.presentation.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tuapp.piano.R
import com.tuapp.piano.domain.inicio.CancionInicio
import com.tuapp.piano.domain.model.ModoPiano
import com.tuapp.piano.presentation.ui.common.ESCALA_ICONO
import com.tuapp.piano.presentation.ui.common.pulsable
import com.tuapp.piano.presentation.ui.common.tocable
import com.tuapp.piano.presentation.ui.practica.estrellasPara
import com.tuapp.piano.presentation.ui.theme.Espacio
import com.tuapp.piano.presentation.ui.theme.Movimiento
import com.tuapp.piano.presentation.ui.theme.PianoColors
import com.tuapp.piano.presentation.ui.theme.movimientoReducido
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Tarjeta grande del inicio: la canción con la que seguir. Tocarla entera la abre en su
 * modo; el botón secundario la abre en el otro (practicar ↔ escuchar).
 * A la derecha, una cascada fija de adorno (distinta por canción) que se alza al presionar.
 */
@Composable
fun TarjetaContinuar(
    cancion: CancionInicio,
    modo: ModoPiano,
    yaTocada: Boolean,
    onAbrir: (ModoPiano) -> Unit,
    modifier: Modifier = Modifier,
) {
    var presionada by remember { mutableStateOf(false) }
    val reducido = movimientoReducido()
    val fondo by animateColorAsState(
        targetValue = if (presionada) PianoColors.SuperficieAlta else PianoColors.Superficie,
        animationSpec = if (presionada) Movimiento.pulsar() else Movimiento.soltar(),
        label = "fondo",
    )
    // Pista de dirección: las notas "suben" hacia el dedo, anticipando que la canción arranca.
    val alzado by animateFloatAsState(
        targetValue = if (presionada && !reducido) 1.18f else 1f,
        animationSpec = if (presionada) Movimiento.pulsar() else Movimiento.soltar(),
        label = "alzado",
    )
    val practicar = modo == ModoPiano.PRACTICA
    val otroModo = if (practicar) ModoPiano.REPRODUCIR else ModoPiano.PRACTICA
    val r = cancion.resumen

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = fondo),
        // tocable primero: su escala abarca también el borde.
        modifier = modifier
            .tocable(etiqueta = r.titulo, onClick = { onAbrir(modo) }, alPresionar = { presionada = it })
            .border(1.dp, PianoColors.ManoDerecha.copy(alpha = 0.35f), MaterialTheme.shapes.extraLarge),
    ) {
        Row(Modifier.padding(Espacio.xl)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (yaTocada) R.string.home_continuar else R.string.home_nueva).uppercase(),
                    color = PianoColors.ManoDerecha,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(Espacio.s))
                Text(
                    r.titulo,
                    color = PianoColors.Texto,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Espacio.xs))
                Text(
                    stringResource(R.string.home_detalle, r.bpm.roundToInt(), r.compas.toString(), duracionCorta(r.duracionSegundos)),
                    color = PianoColors.TextoSecundario,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(Espacio.m))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                    Estrellas(cancion.mejorPrecision, tamano = 18.dp)
                    Text(
                        cancion.mejorPrecision?.let { stringResource(R.string.home_mejor, it) } ?: stringResource(R.string.home_sin_practicar),
                        color = PianoColors.TextoSecundario,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.height(Espacio.l))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
                    FilledIconButton(
                        onClick = { onAbrir(modo) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = PianoColors.ManoDerecha, contentColor = PianoColors.SobreAcento),
                        modifier = Modifier
                            .size(52.dp)
                            .pulsable(escala = ESCALA_ICONO),
                    ) {
                        Icon(
                            if (practicar) Icons.Filled.School else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(if (practicar) R.string.home_seguir_practicando else R.string.home_reproducir),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    TextButton(onClick = { onAbrir(otroModo) }, modifier = Modifier.pulsable()) {
                        Text(stringResource(if (practicar) R.string.home_escuchar else R.string.home_practicar), color = PianoColors.ManoDerecha)
                    }
                }
            }
            CascadaDecorativa(
                semilla = r.id,
                alzado = alzado,
                modifier = Modifier
                    .padding(start = Espacio.m)
                    .width(64.dp)
                    .height(140.dp)
                    .align(Alignment.Bottom),
            )
        }
    }
}

/** Barras fijas con los colores de las manos, como notas cayendo. Mismo dibujo para la misma canción. */
@Composable
private fun CascadaDecorativa(semilla: Long, alzado: Float, modifier: Modifier) {
    val barras = remember(semilla) {
        val azar = Random(semilla)
        List(6) { i -> Triple(i, 0.25f + azar.nextFloat() * 0.6f, azar.nextBoolean()) }
    }
    Canvas(modifier) {
        val ancho = size.width / barras.size
        barras.forEach { (i, alto, derecha) ->
            val h = (size.height * alto * alzado).coerceAtMost(size.height)
            drawRoundRect(
                color = (if (derecha) PianoColors.ManoDerecha else PianoColors.ManoIzquierda).copy(alpha = 0.75f),
                topLeft = Offset(i * ancho + ancho * 0.18f, size.height - h),
                size = Size(ancho * 0.64f, h),
                cornerRadius = CornerRadius(ancho * 0.2f),
            )
        }
    }
}

/** Tres estrellas según la mejor precisión; vacías si nunca se practicó. */
@Composable
fun Estrellas(mejorPrecision: Int?, tamano: Dp) {
    val llenas = mejorPrecision?.let(::estrellasPara) ?: 0
    val descripcion = stringResource(R.string.resultado_estrellas_desc, llenas)
    Row(Modifier.semantics { contentDescription = descripcion }) {
        repeat(3) { i ->
            Icon(
                if (i < llenas) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (i < llenas) PianoColors.Aviso else PianoColors.TextoDeshabilitado,
                modifier = Modifier.size(tamano),
            )
        }
    }
}

/** "1:23". */
fun duracionCorta(segundos: Double): String {
    val s = segundos.roundToInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
