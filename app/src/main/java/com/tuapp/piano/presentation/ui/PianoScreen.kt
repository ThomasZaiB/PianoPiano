package com.tuapp.piano.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.piano.presentation.ui.common.ModoInstrumento
import com.tuapp.piano.presentation.ui.common.pulsable
import com.tuapp.piano.presentation.ui.common.ESCALA_ICONO
import com.tuapp.piano.R
import com.tuapp.piano.domain.model.EstadoMotor
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.ModoPiano
import com.tuapp.piano.domain.practica.ManoPractica
import com.tuapp.piano.domain.practica.SeleccionPractica
import com.tuapp.piano.domain.model.PPQ
import com.tuapp.piano.domain.model.RelojSnapshot
import com.tuapp.piano.presentation.ui.common.textoNotas
import com.tuapp.piano.presentation.ui.piano.PianoSurfaceView
import com.tuapp.piano.presentation.ui.piano.TecladoGeometria
import com.tuapp.piano.presentation.ui.practica.DialogoOpcionesPractica
import com.tuapp.piano.presentation.ui.practica.DialogoResultadoPractica
import com.tuapp.piano.presentation.ui.theme.Espacio
import com.tuapp.piano.presentation.ui.theme.EstiloMono
import com.tuapp.piano.presentation.ui.theme.PianoColors
import com.tuapp.piano.presentation.viewmodel.EstadoGrabacion
import com.tuapp.piano.presentation.viewmodel.EstadoPractica
import com.tuapp.piano.presentation.viewmodel.EventoPiano
import com.tuapp.piano.presentation.viewmodel.PianoUiState
import com.tuapp.piano.presentation.viewmodel.PianoViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Pantalla del piano para los 4 modos. Barra superior en Compose (cambia según el modo)
 * + PianoSurfaceView sin modificar (teclado y cascada).
 */
@Composable
fun PianoScreen(
    onVolver: () -> Unit,
    onIrABiblioteca: () -> Unit,
    vm: PianoViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmarSalida by remember { mutableStateOf(false) }
    val irABiblioteca by rememberUpdatedState(onIrABiblioteca)
    val textoGuardada = stringResource(R.string.piano_guardada)

    // Pantalla siempre encendida y barras del sistema ocultas mientras se toca.
    ModoInstrumento()

    // El stream de audio solo existe con esta pantalla visible.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }

    // Referencia al teclado para los destellos de práctica (que no pasan por el estado de Compose).
    var teclado by remember { mutableStateOf<PianoSurfaceView?>(null) }
    LaunchedEffect(teclado) {
        val vista = teclado ?: return@LaunchedEffect
        vm.resultadosTecla.collect { vista.marcarResultado(it.nota, it.acierto) }
    }

    LaunchedEffect(Unit) {
        vm.eventos.collect { evento ->
            when (evento) {
                is EventoPiano.Mensaje -> snackbar.showSnackbar(evento.texto)
                is EventoPiano.Guardada ->
                    if (evento.volverABiblioteca) irABiblioteca() else snackbar.showSnackbar(textoGuardada)
            }
        }
    }

    // Atrás con una toma sin guardar: preguntar antes de perderla.
    BackHandler(enabled = ui.hayGrabacionSinGuardar) { confirmarSalida = true }
    fun volver() {
        if (ui.hayGrabacionSinGuardar) {
            confirmarSalida = true
        } else {
            onVolver()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(PianoColors.Fondo),
    ) {
        Column(Modifier.fillMaxSize()) {
            BarraSuperior(ui, vm, onVolver = { volver() })
            if (ui.modo == ModoPiano.REPRODUCIR || ui.modo == ModoPiano.PRACTICA) BarraProgreso(ui, vm)
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    PianoSurfaceView(ctx).apply {
                        listener = object : PianoSurfaceView.Listener {
                            override fun onNoteOn(nota: Int, velocidad: Int, mano: Mano) = vm.noteOn(nota, mano, velocidad)
                            override fun onNoteOff(nota: Int, mano: Mano) = vm.noteOff(nota, mano)
                        }
                        fuenteReloj = vm::leerReloj
                    }.also { teclado = it }
                },
                update = { vista ->
                    vista.setCancion(ui.cancion)
                    vista.latenciaAudioMs = ui.latenciaMs ?: 0.0
                    vista.teclasVisibles = ui.teclasVisibles
                    vista.notaDivision = ui.ajustes.notaDivision
                    vista.dinamicaTactil = ui.ajustes.dinamicaTactil
                    vista.compasesVisibles = ui.ajustes.velocidadCascada.compasesVisibles
                    vista.etiquetas = TecladoGeometria.etiquetas(ui.ajustes.notacion, ui.ajustes.etiquetas)
                },
            )
        }

        if (ui.cargandoCancion || ui.estadoMotor == EstadoMotor.CARGANDO) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = PianoColors.ManoDerecha)
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp),
        )
    }

    if (ui.dialogoGuardar) {
        DialogoGuardar(
            sugerido = ui.tituloSugerido,
            esOverdub = ui.modo == ModoPiano.GRABAR && ui.cancion != null,
            numNotas = ui.notasGrabadas,
            guardando = ui.guardando,
            onGuardar = vm::guardar,
            onDescartar = vm::descartarGrabacion,
            onSeguir = vm::seguirGrabando,
        )
    }

    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            title = { Text(stringResource(R.string.piano_salir_titulo)) },
            text = { Text(stringResource(R.string.piano_salir_texto, textoNotas(ui.notasGrabadas))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarSalida = false
                        vm.descartarGrabacion()
                        onVolver()
                    },
                    modifier = Modifier.pulsable(),
                ) { Text(stringResource(R.string.piano_salir), color = PianoColors.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmarSalida = false }, modifier = Modifier.pulsable()) { Text(stringResource(R.string.piano_quedarme)) } },
        )
    }

    if (ui.modo == ModoPiano.PRACTICA && ui.practica.estado == EstadoPractica.TERMINADA) {
        DialogoResultadoPractica(
            precision = ui.practica.precision,
            aciertos = ui.practica.aciertos,
            errores = ui.practica.errores,
            mejorRacha = ui.practica.mejorRacha,
            onRepetir = {
                vm.cerrarResultadoPractica()
                vm.alternarPractica()
            },
            onCerrar = vm::cerrarResultadoPractica,
            onVolver = {
                vm.cerrarResultadoPractica()
                onVolver()
            },
        )
    }
}

// =====================================================================================
// Barra superior
//
//   ← Título……   [ transporte ]  estado   [ − 100 BPM + ⏱ ]  Pedal  ⋮
//
// El título cede espacio primero; zoom e información de audio van en el menú ⋮.
// =====================================================================================

@Composable
private fun BarraSuperior(ui: PianoUiState, vm: PianoViewModel, onVolver: () -> Unit) {
    val listo = ui.motorListo
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(PianoColors.Superficie)
            .padding(horizontal = Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        IconButton(onClick = onVolver, modifier = Modifier.pulsable(escala = ESCALA_ICONO)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accion_volver), tint = PianoColors.Texto)
        }
        Text(
            ui.cancion?.titulo ?: tituloModo(ui.modo),
            color = PianoColors.Texto,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        when (ui.modo) {
            ModoPiano.REPRODUCIR -> ControlesReproducir(ui, vm, listo)
            ModoPiano.PRACTICA -> ControlesPractica(ui, vm, listo)
            ModoPiano.GRABAR -> ControlesGrabar(ui, vm, listo)
            ModoPiano.LIBRE ->
                if (ui.grabacion != EstadoGrabacion.INACTIVA) {
                    ControlesGrabar(ui, vm, listo)
                } else if (listo) {
                    BotonGrabarRapido(onClick = vm::grabarRapido)
                }
        }

        if (ui.estadoMotor == EstadoMotor.ERROR) {
            Text(
                ui.error ?: stringResource(R.string.piano_error_audio),
                color = PianoColors.Error,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }

        FilterChip(
            selected = ui.pedalAbajo,
            onClick = { vm.setPedal(!ui.pedalAbajo) },
            enabled = listo,
            label = { Text(stringResource(R.string.piano_pedal)) },
            modifier = Modifier.pulsable(habilitado = listo),
        )
        MenuVista(ui, vm)
    }
}

@Composable
private fun ControlesReproducir(ui: PianoUiState, vm: PianoViewModel, listo: Boolean) {
    Grupo {
        BotonPrincipal(
            icono = if (ui.reproduciendo) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            descripcion = if (ui.reproduciendo) stringResource(R.string.piano_pausa) else stringResource(R.string.piano_reproducir),
            color = PianoColors.ManoDerecha,
            habilitado = listo && ui.cancion != null,
            onClick = vm::alternarReproduccion,
        )
        BotonIcono(Icons.Filled.SkipPrevious, stringResource(R.string.piano_volver_inicio), habilitado = listo, onClick = vm::volverAlInicio)
    }
    GrupoTempo(ui, vm, listo)
}

@Composable
private fun ControlesGrabar(ui: PianoUiState, vm: PianoViewModel, listo: Boolean) {
    val g = ui.grabacion
    Grupo {
        // 🔴 Grabar (también reanuda tras una pausa)
        BotonPrincipal(
            icono = Icons.Filled.FiberManualRecord,
            descripcion = if (g == EstadoGrabacion.PAUSADA) stringResource(R.string.piano_continuar_grabando) else stringResource(R.string.piano_grabar),
            color = PianoColors.Grabar,
            habilitado = listo && (g == EstadoGrabacion.LISTA || g == EstadoGrabacion.PAUSADA),
            onClick = vm::grabar,
        )
        // ⏸ Pausa
        BotonIcono(Icons.Filled.Pause, stringResource(R.string.piano_pausa), habilitado = g == EstadoGrabacion.GRABANDO, onClick = vm::pausarGrabacion)
        // ⏹ Detener (abre el diálogo para poner nombre)
        BotonIcono(
            Icons.Filled.Stop,
            stringResource(R.string.piano_detener),
            habilitado = g == EstadoGrabacion.GRABANDO || g == EstadoGrabacion.PAUSADA,
            onClick = vm::detenerGrabacion,
        )
    }

    IndicadorGrabacion(g, ui.notasGrabadas)

    // El tempo solo se cambia antes de empezar la toma.
    GrupoTempo(ui, vm, listo && g == EstadoGrabacion.LISTA) {
        IconToggleButton(checked = ui.metronomo, onCheckedChange = { vm.alternarMetronomo() }, modifier = Modifier.pulsable(escala = ESCALA_ICONO)) {
            Icon(
                Icons.Filled.Timer,
                contentDescription = stringResource(R.string.piano_metronomo),
                tint = if (ui.metronomo) PianoColors.ManoDerecha else PianoColors.TextoSecundario,
            )
        }
    }
}

@Composable
private fun ControlesPractica(ui: PianoUiState, vm: PianoViewModel, listo: Boolean) {
    val p = ui.practica
    val corriendo = p.estado == EstadoPractica.EN_CURSO || p.estado == EstadoPractica.ESPERANDO
    Grupo {
        BotonPrincipal(
            icono = if (corriendo) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            descripcion = if (corriendo) stringResource(R.string.piano_pausa) else stringResource(R.string.piano_practicar),
            color = PianoColors.ManoDerecha,
            habilitado = listo && ui.cancion != null,
            onClick = vm::alternarPractica,
        )
        BotonIcono(Icons.Filled.SkipPrevious, stringResource(R.string.piano_reiniciar), habilitado = listo, onClick = vm::reiniciarPractica)
        BotonOpcionesPractica(ui, vm)
    }

    // Destello rojo en cada error (idError cambia aunque el texto sea el mismo).
    val destello = remember { Animatable(0f) }
    LaunchedEffect(p.idError) {
        if (p.idError > 0) {
            destello.snapTo(1f)
            destello.animateTo(0f)
        }
    }
    val (texto, color) = when {
        p.ultimoError != null -> p.ultimoError to PianoColors.Error
        p.estado == EstadoPractica.ESPERANDO -> stringResource(R.string.piano_practica_toca, p.pendientes) to PianoColors.ManoDerecha
        p.estado == EstadoPractica.LISTA -> stringResource(R.string.piano_practica_lista) to PianoColors.TextoSecundario
        p.estado == EstadoPractica.PAUSADA -> stringResource(R.string.piano_practica_pausa) to PianoColors.TextoSecundario
        else -> stringResource(R.string.piano_practica_siguiente, p.pendientes) to PianoColors.Texto
    }
    Column(Modifier.widthIn(max = 240.dp)) {
        Text(
            texto,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(PianoColors.Error.copy(alpha = 0.35f * destello.value))
                .padding(horizontal = Espacio.xs, vertical = Espacio.xxs),
        )
        Row(
            Modifier.padding(horizontal = Espacio.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            Text(
                stringResource(R.string.piano_practica_marcador, p.aciertos, p.errores, p.precision),
                color = PianoColors.TextoSecundario,
                style = MaterialTheme.typography.labelSmall,
            )
            ResumenSeleccion(ui)
            ContadorRacha(p.racha)
        }
    }
    GrupoTempo(ui, vm, listo)
}

/** Abre el diálogo de mano y tramo. Se resalta en cian si hay algo distinto de "toda la canción, ambas manos". */
@Composable
private fun BotonOpcionesPractica(ui: PianoUiState, vm: PianoViewModel) {
    var abierto by remember { mutableStateOf(false) }
    val personalizada = ui.seleccionPractica != SeleccionPractica()
    IconButton(onClick = { abierto = true }, enabled = ui.cancion != null, modifier = Modifier.pulsable(habilitado = ui.cancion != null, escala = ESCALA_ICONO)) {
        Icon(
            Icons.Filled.Tune,
            contentDescription = stringResource(R.string.opciones_titulo),
            tint = if (personalizada) PianoColors.ManoDerecha else PianoColors.Texto,
        )
    }
    if (abierto) {
        DialogoOpcionesPractica(
            actual = ui.seleccionPractica,
            totalCompases = ui.totalCompases,
            onAplicar = {
                vm.cambiarSeleccionPractica(it)
                abierto = false
            },
            onCancelar = { abierto = false },
        )
    }
}

/** "Der. · c. 3–6 · vuelta 2": qué se está practicando, solo si no es la canción entera. */
@Composable
private fun ResumenSeleccion(ui: PianoUiState) {
    val s = ui.seleccionPractica
    val partes = listOfNotNull(
        when (s.mano) {
            ManoPractica.AMBAS -> null
            ManoPractica.IZQUIERDA -> stringResource(R.string.practica_mano_izq_corta)
            ManoPractica.DERECHA -> stringResource(R.string.practica_mano_der_corta)
        },
        s.tramo?.let { stringResource(R.string.practica_tramo_corto, it.first, it.last) },
        s.tramo?.let { stringResource(R.string.practica_vuelta, ui.practica.vuelta) },
    )
    if (partes.isEmpty()) return
    Text(
        partes.joinToString(" · "),
        color = PianoColors.ManoDerecha,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}

/** Racha de aciertos seguidos: aparece desde 2 y da un pequeño salto con cada nota. */
@Composable
private fun ContadorRacha(racha: Int) {
    if (racha < 2) return
    val salto = remember { Animatable(1f) }
    LaunchedEffect(racha) {
        salto.snapTo(1.35f)
        salto.animateTo(1f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer {
            scaleX = salto.value
            scaleY = salto.value
        },
    ) {
        Icon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = PianoColors.Aviso,
            modifier = Modifier.size(14.dp),
        )
        Text(
            stringResource(R.string.piano_racha, racha),
            color = PianoColors.Aviso,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Menú ⋮: ajustes de vista que no se tocan mientras se toca, e info técnica del audio. */
@Composable
private fun MenuVista(ui: PianoUiState, vm: PianoViewModel) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        BotonIcono(Icons.Filled.MoreVert, stringResource(R.string.piano_mas_opciones)) { abierto = true }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            // El menú queda abierto al hacer zoom: se suele pulsar varias veces seguidas.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.piano_acercar)) },
                leadingIcon = { Icon(Icons.Filled.ZoomIn, contentDescription = null) },
                onClick = { vm.cambiarZoom(-4) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.piano_alejar)) },
                leadingIcon = { Icon(Icons.Filled.ZoomOut, contentDescription = null) },
                onClick = { vm.cambiarZoom(4) },
            )
            HorizontalDivider(Modifier.padding(vertical = Espacio.xs), color = PianoColors.Borde)
            Column(Modifier.padding(horizontal = Espacio.l, vertical = Espacio.xs)) {
                Text(
                    ui.latenciaMs?.let { stringResource(R.string.piano_menu_latencia, it.roundToInt()) }
                        ?: stringResource(R.string.piano_menu_latencia_sin_dato),
                    color = PianoColors.TextoSecundario,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    stringResource(R.string.piano_menu_cortes, ui.xRuns ?: 0),
                    color = if ((ui.xRuns ?: 0) > 0) PianoColors.Aviso else PianoColors.TextoSecundario,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

// =====================================================================================
// Barra de progreso (REPRODUCIR y PRACTICA)
// =====================================================================================

/**
 * Posición en la canción. Lee el reloj del motor 10 veces por segundo (no por frame:
 * es una barra, no la cascada). En REPRODUCIR se puede arrastrar para saltar.
 */
@Composable
private fun BarraProgreso(ui: PianoUiState, vm: PianoViewModel) {
    val cancion = ui.cancion ?: return
    val duracion = cancion.duracionTicks.toDouble()
    if (duracion <= 0.0) return

    val reloj = remember { RelojSnapshot() }
    var tick by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(cancion) {
        while (isActive) {
            vm.leerReloj(reloj)
            tick = reloj.tickAt(System.nanoTime()).coerceIn(0.0, duracion)
            delay(100)
        }
    }
    // Mientras se arrastra, la barra sigue al dedo y no al reloj.
    var arrastre by remember { mutableStateOf<Float?>(null) }
    val fraccion = arrastre ?: (tick / duracion).toFloat()
    val ticksPorSegundo = ui.bpm / 60.0 * PPQ
    val descripcionPosicion = stringResource(R.string.piano_posicion)
    val puedeSaltar = ui.modo == ModoPiano.REPRODUCIR && ui.motorListo

    Row(
        Modifier
            .fillMaxWidth()
            .background(PianoColors.Superficie)
            .padding(horizontal = Espacio.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        TextoTiempo(fraccion * duracion / ticksPorSegundo)
        Slider(
            value = fraccion,
            onValueChange = { arrastre = it },
            onValueChangeFinished = {
                arrastre?.let { vm.irATick(it * duracion) }
                arrastre = null
            },
            enabled = puedeSaltar,
            colors = SliderDefaults.colors(
                disabledThumbColor = PianoColors.ManoDerecha,
                disabledActiveTrackColor = PianoColors.ManoDerecha,
                disabledInactiveTrackColor = PianoColors.SuperficieAlta,
            ),
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .semantics { contentDescription = descripcionPosicion },
        )
        TextoTiempo(duracion / ticksPorSegundo)
    }
}

@Composable
private fun TextoTiempo(segundos: Double) {
    val s = segundos.roundToInt().coerceAtLeast(0)
    Text(
        "%d:%02d".format(s / 60, s % 60),
        color = PianoColors.TextoSecundario,
        style = EstiloMono.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
    )
}

// =====================================================================================
// Piezas pequeñas
// =====================================================================================

/** Contenedor redondeado que agrupa controles relacionados (transporte, tempo). */
@Composable
private fun Grupo(contenido: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(PianoColors.SuperficieAlta)
            .padding(horizontal = Espacio.xxs),
        verticalAlignment = Alignment.CenterVertically,
        content = contenido,
    )
}

@Composable
private fun GrupoTempo(ui: PianoUiState, vm: PianoViewModel, habilitado: Boolean, extra: @Composable RowScope.() -> Unit = {}) {
    Grupo {
        BotonIcono(Icons.Filled.Remove, stringResource(R.string.bpm_menos), habilitado = habilitado) { vm.cambiarBpm(-5) }
        Text(
            stringResource(R.string.bpm_valor, ui.bpm.roundToInt()),
            color = if (habilitado) PianoColors.Texto else PianoColors.TextoSecundario,
            style = MaterialTheme.typography.labelMedium,
        )
        BotonIcono(Icons.Filled.Add, stringResource(R.string.bpm_mas), habilitado = habilitado) { vm.cambiarBpm(5) }
        extra()
    }
}

@Composable
private fun BotonGrabarRapido(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = PianoColors.SuperficieAlta, contentColor = PianoColors.Texto),
        modifier = Modifier.pulsable(),
    ) {
        Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = PianoColors.Grabar, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Espacio.s))
        Text(stringResource(R.string.piano_grabar_rapido))
    }
}

@Composable
private fun IndicadorGrabacion(estado: EstadoGrabacion, notas: Int) {
    val (texto, color) = when (estado) {
        EstadoGrabacion.GRABANDO -> stringResource(R.string.piano_rec, textoNotas(notas)) to PianoColors.Grabar
        EstadoGrabacion.PAUSADA -> stringResource(R.string.piano_rec_pausa, textoNotas(notas)) to PianoColors.TextoSecundario
        else -> stringResource(R.string.piano_rec_lista) to PianoColors.TextoSecundario
    }
    Text(texto, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

@Composable
private fun BotonPrincipal(
    icono: ImageVector,
    descripcion: String,
    color: Color,
    habilitado: Boolean,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = habilitado,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = PianoColors.SobreAcento),
        modifier = Modifier.pulsable(habilitado = habilitado, escala = ESCALA_ICONO),
    ) {
        Icon(icono, contentDescription = descripcion)
    }
}

@Composable
private fun BotonIcono(icono: ImageVector, descripcion: String, habilitado: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = habilitado, modifier = Modifier.pulsable(habilitado = habilitado, escala = ESCALA_ICONO)) {
        Icon(icono, contentDescription = descripcion, tint = if (habilitado) PianoColors.Texto else PianoColors.TextoDeshabilitado)
    }
}

@Composable
private fun tituloModo(modo: ModoPiano) = when (modo) {
    ModoPiano.LIBRE -> stringResource(R.string.piano_modo_libre)
    ModoPiano.GRABAR -> stringResource(R.string.piano_modo_grabar)
    ModoPiano.REPRODUCIR -> stringResource(R.string.piano_modo_reproducir)
    ModoPiano.PRACTICA -> stringResource(R.string.piano_modo_practica)
}

// =====================================================================================

@Composable
private fun DialogoGuardar(
    sugerido: String,
    esOverdub: Boolean,
    numNotas: Int,
    guardando: Boolean,
    onGuardar: (String) -> Unit,
    onDescartar: () -> Unit,
    onSeguir: () -> Unit,
) {
    var titulo by rememberSaveable(sugerido) { mutableStateOf(sugerido) }
    AlertDialog(
        onDismissRequest = { if (!guardando) onSeguir() },
        title = { Text(if (esOverdub) stringResource(R.string.piano_guardar_overdub_titulo) else stringResource(R.string.piano_guardar_titulo)) },
        text = {
            Column {
                Text(
                    if (esOverdub) {
                        pluralStringResource(R.plurals.piano_guardar_overdub_texto, numNotas, numNotas)
                    } else {
                        pluralStringResource(R.plurals.piano_guardar_texto, numNotas, numNotas)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it.take(60) },
                    label = { Text(stringResource(R.string.piano_guardar_nombre)) },
                    singleLine = true,
                    enabled = !guardando,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(titulo) }, enabled = !guardando && titulo.isNotBlank(), modifier = Modifier.pulsable(habilitado = !guardando && titulo.isNotBlank())) {
                if (guardando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.accion_guardar))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDescartar, enabled = !guardando, modifier = Modifier.pulsable(habilitado = !guardando)) { Text(stringResource(R.string.accion_descartar), color = PianoColors.Error) }
                TextButton(onClick = onSeguir, enabled = !guardando, modifier = Modifier.pulsable(habilitado = !guardando)) { Text(stringResource(R.string.piano_seguir_grabando)) }
            }
        },
    )
}
