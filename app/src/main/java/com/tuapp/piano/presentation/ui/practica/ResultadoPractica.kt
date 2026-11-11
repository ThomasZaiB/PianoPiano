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
    Dialog(onDismissRequest = onCerrar) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = PianoColors.Superficie),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
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
                    repeat(3) { i -> Estrella(llena = i < estrellas, retrasoMs = 150L * i) }
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
                    Dato(stringResource(R.string.resultado_valor_porcentaje, precision), stringResource(R.string.resultado_precision), PianoColors.ManoDerecha)
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
private fun Estrella(llena: Boolean, retrasoMs: Long) {
    val escala = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
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
