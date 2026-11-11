package com.tuapp.piano.presentation.ui.practica

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import com.tuapp.piano.presentation.ui.theme.movimientoReducido
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.random.Random
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tuapp.piano.presentation.ui.common.pulsable
import com.tuapp.piano.R
import com.tuapp.piano.presentation.ui.theme.Espacio
import com.tuapp.piano.presentation.ui.theme.PianoColors
import kotlinx.coroutines.delay

/** Estrellas (0–3) según la precisión final, en %. */
fun estrellasPara(precision: Int): Int = when {
    precision >= 95 -> 3
    precision >= 80 -> 2
    precision >= 60 -> 1
    else -> 0
}

/**
 * Resultado al terminar una práctica. Compacto a propósito: la pantalla del piano va
 * en horizontal y el alto disponible en un teléfono es poco (~360 dp).
 */
@Composable
fun DialogoResultadoPractica(
    precision: Int,
    aciertos: Int,
    errores: Int,
    mejorRacha: Int,
    onRepetir: () -> Unit,
    onCerrar: () -> Unit,
    onVolver: () -> Unit,
) {
    val estrellas = estrellasPara(precision)
    val descripcionEstrellas = stringResource(R.string.resultado_estrellas_desc, estrellas)
    val reducido = movimientoReducido()
    // El porcentaje sube contando desde 0: el resultado se "revela" en vez de aparecer de golpe.
    val porcentaje = remember { Animatable(if (reducido) precision.toFloat() else 0f) }
    LaunchedEffect(precision) {
        if (!reducido) porcentaje.animateTo(precision.toFloat(), tween(DURACION_CUENTA_MS, delayMillis = RETRASO_CUENTA_MS, easing = FastOutSlowInEasing))
    }
    Dialog(onDismissRequest = onCerrar) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = PianoColors.Superficie),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Box {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Espacio.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                    modifier = Modifier.semantics { contentDescription = descripcionEstrellas },
                ) {
                    repeat(3) { i -> Estrella(llena = i < estrellas, retrasoMs = 150L * i, reducido = reducido) }
                }
                Spacer(Modifier.height(Espacio.m))
                Text(
                    stringResource(mensajePara(estrellas)),
                    color = PianoColors.Texto,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Espacio.l))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Dato(stringResource(R.string.resultado_valor_porcentaje, porcentaje.value.roundToInt()), stringResource(R.string.resultado_precision), PianoColors.ManoDerecha)
                    Dato(aciertos.toString(), stringResource(R.string.resultado_aciertos), PianoColors.Exito)
                    Dato(errores.toString(), stringResource(R.string.resultado_errores), PianoColors.Error)
                    Dato(mejorRacha.toString(), stringResource(R.string.resultado_mejor_racha), PianoColors.Aviso)
                }
                Spacer(Modifier.height(Espacio.xl))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Espacio.s, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onVolver, modifier = Modifier.pulsable()) { Text(stringResource(R.string.resultado_volver)) }
                    Button(
                        onClick = onRepetir,
                        colors = ButtonDefaults.buttonColors(containerColor = PianoColors.ManoDerecha, contentColor = PianoColors.SobreAcento),
                        modifier = Modifier.pulsable(),
                    ) { Text(stringResource(R.string.piano_repetir)) }
                }
            }
            // Tres estrellas: una celebración breve encima de la tarjeta (no recibe toques).
            if (estrellas == 3 && !reducido) Celebracion(Modifier.matchParentSize())
            }
        }
    }
}

private fun mensajePara(estrellas: Int) = when (estrellas) {
    3 -> R.string.resultado_mensaje_3
    2 -> R.string.resultado_mensaje_2
    1 -> R.string.resultado_mensaje_1
    else -> R.string.resultado_mensaje_0
}

/** Las estrellas entran una tras otra con un pequeño rebote. */
@Composable
private fun Estrella(llena: Boolean, retrasoMs: Long, reducido: Boolean) {
    val escala = remember { Animatable(if (reducido) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reducido) return@LaunchedEffect
        delay(retrasoMs)
        escala.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    Icon(
        if (llena) Icons.Filled.Star else Icons.Outlined.StarOutline,
        contentDescription = null,
        tint = if (llena) PianoColors.Aviso else PianoColors.TextoDeshabilitado,
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = escala.value
                scaleY = escala.value
            },
    )
}

@Composable
private fun Dato(valor: String, etiqueta: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, color = color, style = MaterialTheme.typography.headlineSmall)
        Text(etiqueta, color = PianoColors.TextoSecundario, style = MaterialTheme.typography.labelSmall)
    }
}

private const val DURACION_CUENTA_MS = 900
private const val RETRASO_CUENTA_MS = 200
private const val DURACION_CELEBRACION_MS = 1400
private const val PARTICULAS = 40

/** Una partícula de confeti: dirección, rapidez (fracción del alto por segundo), color y tamaño. */
private class Particula(val angulo: Float, val rapidez: Float, val color: Color, val radio: Float, val giro: Float)

/**
 * Confeti que sale de las estrellas y cae. Sin dependencias: un Canvas y una sola animación
 * de progreso; las partículas se generan una vez.
 */
@Composable
private fun Celebracion(modifier: Modifier) {
    val colores = listOf(PianoColors.Aviso, PianoColors.ManoDerecha, PianoColors.ManoIzquierda, PianoColors.Exito)
    val particulas = remember {
        val azar = Random(7)
        List(PARTICULAS) {
            Particula(
                // Hacia arriba en abanico (radianes, 0 = derecha; -π/2 = arriba).
                angulo = (-PI / 2 + (azar.nextFloat() - 0.5f) * 2.2).toFloat(),
                rapidez = 0.6f + azar.nextFloat() * 0.9f,
                color = colores[it % colores.size],
                radio = 3f + azar.nextFloat() * 3f,
                giro = azar.nextFloat(),
            )
        }
    }
    val progreso = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progreso.animateTo(1f, tween(DURACION_CELEBRACION_MS, easing = LinearEasing)) }
    Canvas(modifier) {
        val t = progreso.value
        if (t >= 1f) return@Canvas
        val origen = Offset(size.width / 2f, size.height * 0.12f)
        val escala = size.height
        val gravedad = 1.6f * escala
        val segundos = t * DURACION_CELEBRACION_MS / 1000f
        for (p in particulas) {
            val vx = cos(p.angulo) * p.rapidez * escala
            val vy = sin(p.angulo) * p.rapidez * escala
            val x = origen.x + vx * segundos
            val y = origen.y + vy * segundos + 0.5f * gravedad * segundos * segundos
            val alfa = (1f - t) * (0.6f + 0.4f * p.giro)
            drawCircle(p.color.copy(alpha = alfa), radius = p.radio * density, center = Offset(x, y))
        }
    }
}
