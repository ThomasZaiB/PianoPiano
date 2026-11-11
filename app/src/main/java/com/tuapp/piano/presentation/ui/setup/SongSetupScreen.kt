package com.tuapp.piano.presentation.ui.setup

import androidx.compose.ui.res.stringResource
import com.tuapp.piano.presentation.ui.common.pulsable
import com.tuapp.piano.presentation.ui.common.ESCALA_ICONO
import com.tuapp.piano.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tuapp.piano.domain.model.Compas
import com.tuapp.piano.domain.model.ConfigGrabacion
import com.tuapp.piano.domain.model.Cuantizacion
import com.tuapp.piano.presentation.ui.theme.Espacio
import com.tuapp.piano.presentation.ui.theme.PianoColors
import kotlin.math.roundToInt

private val COMPASES = listOf(Compas(4, 4), Compas(3, 4))
private val OPCIONES_CUANTIZACION = listOf(
    Cuantizacion.NEGRA,
    Cuantizacion.CORCHEA,
    Cuantizacion.SEMICORCHEA,
    Cuantizacion.FUSA,
    Cuantizacion.TRESILLO,
    Cuantizacion.NINGUNA,
)

/**
 * Configuración previa a grabar. El estado es local (rememberSaveable): solo hace falta
 * hasta pulsar "Comenzar a grabar", y entonces viaja en la ruta hacia el PianoViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SongSetupScreen(
    onVolver: () -> Unit,
    onComenzar: (ConfigGrabacion) -> Unit,
) {
    var bpm by rememberSaveable { mutableIntStateOf(100) }
    var indiceCompas by rememberSaveable { mutableIntStateOf(0) }
    var cuantizacion by rememberSaveable { mutableStateOf(Cuantizacion.SEMICORCHEA) }
    var humana by rememberSaveable { mutableStateOf(true) }
    var metronomo by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        containerColor = PianoColors.Fondo,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onVolver, modifier = Modifier.pulsable(escala = ESCALA_ICONO)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accion_volver))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PianoColors.Fondo,
                    titleContentColor = PianoColors.Texto,
                    navigationIconContentColor = PianoColors.Texto,
                ),
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    onComenzar(
                        ConfigGrabacion(
                            bpm = bpm,
                            compas = COMPASES[indiceCompas],
                            cuantizacion = cuantizacion,
                            cuantizacionHumana = humana,
                            metronomo = metronomo,
                        ),
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PianoColors.ManoIzquierda),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Espacio.margenPantalla)
                    .height(56.dp)
                    .pulsable(),
            ) {
                Icon(Icons.Filled.FiberManualRecord, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(Espacio.s))
                Text(stringResource(R.string.setup_comenzar), style = MaterialTheme.typography.labelLarge)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.margenPantalla),
            verticalArrangement = Arrangement.spacedBy(Espacio.xxl),
        ) {
            // ---- Tempo ----
            Seccion(stringResource(R.string.rotulo_tempo)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { bpm = (bpm - 1).coerceAtLeast(40) }, modifier = Modifier.pulsable(escala = ESCALA_ICONO)) {
                        Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.bpm_menos))
                    }
                    Text(
                        stringResource(R.string.bpm_valor, bpm),
                        color = PianoColors.Texto,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(onClick = { bpm = (bpm + 1).coerceAtMost(240) }, modifier = Modifier.pulsable(escala = ESCALA_ICONO)) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.bpm_mas))
                    }
                }
                Slider(
                    value = bpm.toFloat(),
                    onValueChange = { bpm = it.roundToInt() },
                    valueRange = 40f..240f,
                )
            }

            // ---- Compás ----
            Seccion(stringResource(R.string.rotulo_compas)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    COMPASES.forEachIndexed { i, compas ->
                        SegmentedButton(
                            selected = indiceCompas == i,
                            onClick = { indiceCompas = i },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = COMPASES.size),
                        ) { Text(compas.toString()) }
                    }
                }
            }

            // ---- Cuantización ----
            Seccion(stringResource(R.string.setup_cuantizacion)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                    OPCIONES_CUANTIZACION.forEach { opcion ->
                        FilterChip(
                            selected = cuantizacion == opcion,
                            onClick = { cuantizacion = opcion },
                            label = { Text(opcion.etiqueta) },
                            modifier = Modifier.pulsable(),
                        )
                    }
                }
                Spacer(Modifier.height(Espacio.m))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !humana,
                        onClick = { humana = false },
                        enabled = cuantizacion != Cuantizacion.NINGUNA,
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.setup_perfecta)) }
                    SegmentedButton(
                        selected = humana,
                        onClick = { humana = true },
                        enabled = cuantizacion != Cuantizacion.NINGUNA,
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.setup_humana)) }
                }
                Text(
                    when {
                        cuantizacion == Cuantizacion.NINGUNA -> stringResource(R.string.setup_ayuda_ninguna)
                        humana -> stringResource(R.string.setup_ayuda_humana, cuantizacion.etiqueta)
                        else -> stringResource(R.string.setup_ayuda_perfecta, cuantizacion.etiqueta)
                    },
                    color = PianoColors.TextoSecundario,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Espacio.s),
                )
            }

            // ---- Metrónomo ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.setup_metronomo), color = PianoColors.Texto, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.setup_metronomo_desc), color = PianoColors.TextoSecundario, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = metronomo, onCheckedChange = { metronomo = it })
            }
            Spacer(Modifier.height(Espacio.s))
        }
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Column {
        Text(
            titulo.uppercase(),
            color = PianoColors.ManoDerecha,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = Espacio.s),
        )
        contenido()
    }
}
