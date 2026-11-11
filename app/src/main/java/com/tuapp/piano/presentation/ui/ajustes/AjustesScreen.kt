package com.tuapp.piano.presentation.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.piano.presentation.ui.common.pulsable
import com.tuapp.piano.presentation.ui.common.ESCALA_ICONO
import com.tuapp.piano.R
import com.tuapp.piano.domain.model.Ajustes
import com.tuapp.piano.domain.model.EtiquetasTeclado
import com.tuapp.piano.domain.model.NombresNotas
import com.tuapp.piano.domain.model.Notacion
import com.tuapp.piano.domain.model.Reverberacion
import com.tuapp.piano.domain.model.SensibilidadToque
import com.tuapp.piano.domain.model.VelocidadCascada
import com.tuapp.piano.presentation.ui.theme.Espacio
import com.tuapp.piano.presentation.ui.theme.PianoColors
import com.tuapp.piano.presentation.viewmodel.AjustesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    onVolver: () -> Unit,
    vm: AjustesViewModel = hiltViewModel(),
) {
    val a by vm.ajustes.collectAsStateWithLifecycle()
    var confirmarRestablecer by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PianoColors.Fondo,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ajustes_titulo)) },
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
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.margenPantalla),
            verticalArrangement = Arrangement.spacedBy(Espacio.xxl),
        ) {
            Seccion(stringResource(R.string.ajustes_seccion_teclado)) {
                Opcion(stringResource(R.string.ajustes_notacion)) {
                    Segmentos(
                        opciones = Notacion.entries,
                        seleccionada = a.notacion,
                        etiqueta = { stringResource(if (it == Notacion.LATINA) R.string.ajustes_notacion_latina else R.string.ajustes_notacion_inglesa) },
                        onElegir = vm::cambiarNotacion,
                    )
                }
                Opcion(stringResource(R.string.ajustes_etiquetas)) {
                    Segmentos(
                        opciones = EtiquetasTeclado.entries,
                        seleccionada = a.etiquetas,
                        etiqueta = {
                            stringResource(
                                when (it) {
                                    EtiquetasTeclado.NINGUNA -> R.string.ajustes_etiquetas_ninguna
                                    EtiquetasTeclado.SOLO_DO -> if (a.notacion == Notacion.LATINA) R.string.ajustes_etiquetas_do else R.string.ajustes_etiquetas_c
                                    EtiquetasTeclado.BLANCAS -> R.string.ajustes_etiquetas_blancas
                                },
                            )
                        },
                        onElegir = vm::cambiarEtiquetas,
                    )
                }
                Opcion(
                    stringResource(R.string.ajustes_division),
                    stringResource(R.string.ajustes_division_desc, NombresNotas.nombre(a.notaDivision, a.notacion)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = { vm.moverDivision(-1) },
                            enabled = a.notaDivision > Ajustes.RANGO_DIVISION.first,
                            modifier = Modifier.pulsable(habilitado = a.notaDivision > Ajustes.RANGO_DIVISION.first, escala = ESCALA_ICONO),
                        ) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.ajustes_division_bajar)) }
                        Text(
                            NombresNotas.nombre(a.notaDivision, a.notacion),
                            color = PianoColors.ManoDerecha,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(
                            onClick = { vm.moverDivision(1) },
                            enabled = a.notaDivision < Ajustes.RANGO_DIVISION.last,
                            modifier = Modifier.pulsable(habilitado = a.notaDivision < Ajustes.RANGO_DIVISION.last, escala = ESCALA_ICONO),
                        ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ajustes_division_subir)) }
                    }
                }
                Interruptor(
                    titulo = stringResource(R.string.ajustes_dinamica),
                    descripcion = stringResource(
                        if (a.dinamicaTactil) R.string.ajustes_dinamica_si else R.string.ajustes_dinamica_no,
                    ),
                    activo = a.dinamicaTactil,
                    onCambiar = vm::cambiarDinamica,
                )
                if (a.dinamicaTactil) {
                    Opcion(stringResource(R.string.ajustes_sensibilidad), stringResource(R.string.ajustes_sensibilidad_desc)) {
                        Segmentos(
                            opciones = SensibilidadToque.entries,
                            seleccionada = a.sensibilidad,
                            etiqueta = {
                                stringResource(
                                    when (it) {
                                        SensibilidadToque.SUAVE -> R.string.ajustes_sensibilidad_suave
                                        SensibilidadToque.NORMAL -> R.string.ajustes_sensibilidad_normal
                                        SensibilidadToque.FUERTE -> R.string.ajustes_sensibilidad_fuerte
                                    },
                                )
                            },
                            onElegir = vm::cambiarSensibilidad,
                        )
                    }
                }
                Interruptor(
                    titulo = stringResource(R.string.ajustes_vibrar_tocar),
                    descripcion = stringResource(R.string.ajustes_vibrar_tocar_desc),
                    activo = a.vibrarAlTocar,
                    onCambiar = vm::cambiarVibrarAlTocar,
                )
            }

            Seccion(stringResource(R.string.ajustes_seccion_sonido)) {
                Opcion(stringResource(R.string.ajustes_reverb), stringResource(R.string.ajustes_reverb_desc)) {
                    Segmentos(
                        opciones = Reverberacion.entries,
                        seleccionada = a.reverberacion,
                        etiqueta = {
                            stringResource(
                                when (it) {
                                    Reverberacion.APAGADA -> R.string.ajustes_reverb_apagada
                                    Reverberacion.SALA -> R.string.ajustes_reverb_sala
                                    Reverberacion.AUDITORIO -> R.string.ajustes_reverb_auditorio
                                },
                            )
                        },
                        onElegir = vm::cambiarReverberacion,
                    )
                }
            }

            Seccion(stringResource(R.string.ajustes_seccion_cascada)) {
                Opcion(stringResource(R.string.ajustes_velocidad), stringResource(R.string.ajustes_velocidad_desc)) {
                    Segmentos(
                        opciones = VelocidadCascada.entries,
                        seleccionada = a.velocidadCascada,
                        etiqueta = {
                            stringResource(
                                when (it) {
                                    VelocidadCascada.LENTA -> R.string.ajustes_velocidad_lenta
                                    VelocidadCascada.NORMAL -> R.string.ajustes_velocidad_normal
                                    VelocidadCascada.RAPIDA -> R.string.ajustes_velocidad_rapida
                                },
                            )
                        },
                        onElegir = vm::cambiarVelocidad,
                    )
                }
            }

            Seccion(stringResource(R.string.ajustes_seccion_practica)) {
                Interruptor(
                    titulo = stringResource(R.string.ajustes_vibrar_fallar),
                    descripcion = stringResource(R.string.ajustes_vibrar_fallar_desc),
                    activo = a.vibrarAlFallar,
                    onCambiar = vm::cambiarVibrarAlFallar,
                )
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { confirmarRestablecer = true }, modifier = Modifier.pulsable()) {
                    Text(stringResource(R.string.ajustes_restablecer), color = PianoColors.Error)
                }
                Text(
                    stringResource(R.string.ajustes_version, versionApp()),
                    color = PianoColors.TextoDeshabilitado,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(Espacio.xl))
            }
        }
    }

    if (confirmarRestablecer) {
        AlertDialog(
            onDismissRequest = { confirmarRestablecer = false },
            title = { Text(stringResource(R.string.ajustes_restablecer_titulo)) },
            text = { Text(stringResource(R.string.ajustes_restablecer_texto)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.restablecer()
                        confirmarRestablecer = false
                    },
                    modifier = Modifier.pulsable(),
                ) { Text(stringResource(R.string.ajustes_restablecer_confirmar), color = PianoColors.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmarRestablecer = false }, modifier = Modifier.pulsable()) { Text(stringResource(R.string.accion_cancelar)) } },
        )
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.l)) {
        Text(titulo.uppercase(), color = PianoColors.ManoDerecha, style = MaterialTheme.typography.labelLarge)
        contenido()
    }
}

@Composable
private fun Opcion(titulo: String, descripcion: String? = null, control: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Column {
            Text(titulo, color = PianoColors.Texto, style = MaterialTheme.typography.titleMedium)
            if (descripcion != null) {
                Text(descripcion, color = PianoColors.TextoSecundario, style = MaterialTheme.typography.bodySmall)
            }
        }
        control()
    }
}

@Composable
private fun Interruptor(titulo: String, descripcion: String, activo: Boolean, onCambiar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Espacio.l)) {
            Text(titulo, color = PianoColors.Texto, style = MaterialTheme.typography.titleMedium)
            Text(descripcion, color = PianoColors.TextoSecundario, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = activo, onCheckedChange = onCambiar)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmentos(
    opciones: List<T>,
    seleccionada: T,
    etiqueta: @Composable (T) -> String,
    onElegir: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        opciones.forEachIndexed { i, opcion ->
            SegmentedButton(
                selected = opcion == seleccionada,
                onClick = { onElegir(opcion) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = opciones.size),
            ) { Text(etiqueta(opcion), maxLines = 1) }
        }
    }
}

@Composable
private fun versionApp(): String {
    val contexto = LocalContext.current
    return remember {
        runCatching { contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}
