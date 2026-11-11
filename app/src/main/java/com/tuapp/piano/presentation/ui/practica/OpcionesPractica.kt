package com.tuapp.piano.presentation.ui.practica

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tuapp.piano.presentation.ui.common.pulsable
import com.tuapp.piano.presentation.ui.common.ESCALA_ICONO
import com.tuapp.piano.R
import com.tuapp.piano.domain.practica.ManoPractica
import com.tuapp.piano.domain.practica.SeleccionPractica
import com.tuapp.piano.presentation.ui.theme.Espacio
import com.tuapp.piano.presentation.ui.theme.PianoColors

/** Elegir mano y tramo. Los cambios se aplican al pulsar "Aplicar" (reinician la práctica). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoOpcionesPractica(
    actual: SeleccionPractica,
    totalCompases: Int,
    onAplicar: (SeleccionPractica) -> Unit,
    onCancelar: () -> Unit,
) {
    var mano by remember { mutableStateOf(actual.mano) }
    var conTramo by remember { mutableStateOf(actual.tramo != null) }
    var desde by remember { mutableIntStateOf(actual.tramo?.first ?: 1) }
    var hasta by remember { mutableIntStateOf(actual.tramo?.last ?: minOf(4, totalCompases)) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.opciones_titulo)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Espacio.l),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                    Text(stringResource(R.string.opciones_mano), color = PianoColors.Texto, style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ManoPractica.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = mano == m,
                                onClick = { mano = m },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = ManoPractica.entries.size),
                            ) { Text(stringResource(nombreMano(m)), maxLines = 1) }
                        }
                    }
                    if (mano != ManoPractica.AMBAS) {
                        Text(stringResource(R.string.opciones_mano_acompanamiento), color = PianoColors.TextoSecundario, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = Espacio.l)) {
                        Text(stringResource(R.string.opciones_tramo), color = PianoColors.Texto, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.opciones_tramo_desc), color = PianoColors.TextoSecundario, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = conTramo, onCheckedChange = { conTramo = it }, enabled = totalCompases > 0)
                }
                if (conTramo) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.l)) {
                        SelectorCompas(
                            etiqueta = stringResource(R.string.opciones_desde),
                            valor = desde,
                            rango = 1..totalCompases,
                            onCambiar = {
                                desde = it
                                if (hasta < it) hasta = it
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SelectorCompas(
                            etiqueta = stringResource(R.string.opciones_hasta),
                            valor = hasta,
                            rango = desde..totalCompases,
                            onCambiar = { hasta = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAplicar(SeleccionPractica(mano = mano, tramo = if (conTramo) desde..hasta else null))
                },
                modifier = Modifier.pulsable(),
            ) { Text(stringResource(R.string.opciones_aplicar)) }
        },
        dismissButton = { TextButton(onClick = onCancelar, modifier = Modifier.pulsable()) { Text(stringResource(R.string.accion_cancelar)) } },
        modifier = Modifier.widthIn(max = 520.dp),
    )
}

@Composable
private fun SelectorCompas(etiqueta: String, valor: Int, rango: IntRange, onCambiar: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(etiqueta, color = PianoColors.TextoSecundario, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onCambiar(valor - 1) }, enabled = valor > rango.first, modifier = Modifier.pulsable(habilitado = valor > rango.first, escala = ESCALA_ICONO)) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.opciones_compas_menos, etiqueta))
            }
            Text(
                valor.toString(),
                color = PianoColors.Texto,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 40.dp),
            )
            FilledTonalIconButton(onClick = { onCambiar(valor + 1) }, enabled = valor < rango.last, modifier = Modifier.pulsable(habilitado = valor < rango.last, escala = ESCALA_ICONO)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.opciones_compas_mas, etiqueta))
            }
        }
    }
}

fun nombreMano(m: ManoPractica) = when (m) {
    ManoPractica.AMBAS -> R.string.opciones_mano_ambas
    ManoPractica.IZQUIERDA -> R.string.opciones_mano_izquierda
    ManoPractica.DERECHA -> R.string.opciones_mano_derecha
}
