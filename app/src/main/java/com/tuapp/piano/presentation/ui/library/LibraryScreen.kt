package com.tuapp.piano.presentation.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.piano.presentation.ui.common.pulsable
import com.tuapp.piano.presentation.ui.common.ESCALA_ICONO
import com.tuapp.piano.R
import com.tuapp.piano.domain.model.ModoPiano
import com.tuapp.piano.domain.model.OrigenCancion
import com.tuapp.piano.domain.model.ResumenCancion
import com.tuapp.piano.presentation.ui.common.textoNotas
import com.tuapp.piano.presentation.ui.common.rememberSelectorMidi
import com.tuapp.piano.presentation.ui.common.tocable
import com.tuapp.piano.presentation.ui.theme.Espacio
import com.tuapp.piano.presentation.ui.theme.Movimiento
import com.tuapp.piano.presentation.ui.theme.PianoColors
import com.tuapp.piano.presentation.viewmodel.FiltroBiblioteca
import com.tuapp.piano.presentation.viewmodel.LibraryViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val FORMATO_FECHA = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.forLanguageTag("es"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onVolver: () -> Unit,
    onNuevaCancion: () -> Unit,
    onImportar: () -> Unit,
    onAbrir: (ModoPiano, Long) -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var porBorrar by remember { mutableStateOf<ResumenCancion?>(null) }

    // Exportar: guardamos qué canción se pidió y abrimos el selector "Guardar como" del sistema (SAF).
    var idPorExportar by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectorArchivo = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/midi"),
    ) { uri ->
        val id = idPorExportar
        idPorExportar = null
        if (uri != null && id != null) vm.exportar(id, uri.toString())
    }

    LaunchedEffect(Unit) {
        vm.mensajes.collect { snackbar.showSnackbar(it) }
    }

    val scope = rememberCoroutineScope()
    val errorLeer = stringResource(R.string.biblioteca_error_leer_midi)
    val abrirMidi = rememberSelectorMidi(
        onLeido = vm::importarMidi,
        onError = { scope.launch { snackbar.showSnackbar(errorLeer) } },
    )
    var menuImportar by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PianoColors.Fondo,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.biblioteca_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onVolver, modifier = Modifier.pulsable(escala = ESCALA_ICONO)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accion_volver))
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { menuImportar = true }, modifier = Modifier.pulsable()) {
                            Text(stringResource(R.string.biblioteca_importar), color = PianoColors.ManoDerecha)
                        }
                        DropdownMenu(expanded = menuImportar, onDismissRequest = { menuImportar = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.biblioteca_importar_midi)) },
                                leadingIcon = { Icon(Icons.Filled.AudioFile, contentDescription = null) },
                                onClick = {
                                    menuImportar = false
                                    abrirMidi()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.biblioteca_importar_texto_menu)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.TextSnippet, contentDescription = null) },
                                onClick = {
                                    menuImportar = false
                                    onImportar()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PianoColors.Fondo,
                    titleContentColor = PianoColors.Texto,
                    navigationIconContentColor = PianoColors.Texto,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNuevaCancion, containerColor = PianoColors.ManoIzquierda, modifier = Modifier.pulsable()) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.biblioteca_nueva), tint = PianoColors.SobreAcento)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                Modifier.padding(horizontal = Espacio.l, vertical = Espacio.xs),
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                FiltroBiblioteca.entries.forEach { f ->
                    FilterChip(
                        selected = ui.filtro == f,
                        onClick = { vm.cambiarFiltro(f) },
                        label = { Text(stringResource(R.string.biblioteca_filtro, f.etiqueta, ui.conteo[f] ?: 0)) },
                        modifier = Modifier.pulsable(),
                    )
                }
            }

            if (!ui.cargando && ui.canciones.isEmpty()) {
                EstadoVacio(ui.filtro, onNuevaCancion, onImportar)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = Espacio.l, end = Espacio.l, top = Espacio.s, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(Espacio.m),
                ) {
                    items(ui.canciones, key = { it.id }) { cancion ->
                        FilaDeslizable(onPedirBorrar = { porBorrar = cancion }) {
                            TarjetaCancion(
                                cancion = cancion,
                                onReproducir = { onAbrir(ModoPiano.REPRODUCIR, cancion.id) },
                                onPracticar = { onAbrir(ModoPiano.PRACTICA, cancion.id) },
                                onEditar = { onAbrir(ModoPiano.GRABAR, cancion.id) },
                                onExportar = {
                                    idPorExportar = cancion.id
                                    selectorArchivo.launch(nombreArchivo(cancion.titulo))
                                },
                                onBorrar = { porBorrar = cancion },
                            )
                        }
                    }
                }
            }
        }
    }

    porBorrar?.let { cancion ->
        AlertDialog(
            onDismissRequest = { porBorrar = null },
            title = { Text(stringResource(R.string.biblioteca_borrar_titulo)) },
            text = { Text(stringResource(R.string.biblioteca_borrar_texto, cancion.titulo, textoNotas(cancion.numNotas))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.borrar(cancion)
                        porBorrar = null
                    },
                    modifier = Modifier.pulsable(),
                ) { Text(stringResource(R.string.accion_borrar), color = PianoColors.Error) }
            },
            dismissButton = { TextButton(onClick = { porBorrar = null }, modifier = Modifier.pulsable()) { Text(stringResource(R.string.accion_cancelar)) } },
        )
    }
}

/**
 * Deslizar a la izquierda NO borra directamente: pide confirmación y la tarjeta
 * vuelve a su sitio (confirmValueChange devuelve false). Si el usuario cancela,
 * no queda nada a medio borrar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilaDeslizable(onPedirBorrar: () -> Unit, contenido: @Composable () -> Unit) {
    val estado = rememberSwipeToDismissBoxState(
        confirmValueChange = { valor ->
            if (valor == SwipeToDismissBoxValue.EndToStart) onPedirBorrar()
            false
        },
    )
    SwipeToDismissBox(
        state = estado,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(PianoColors.Error.copy(alpha = 0.85f))
                    .padding(end = Espacio.xxl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.accion_borrar), tint = PianoColors.Texto)
            }
        },
    ) { contenido() }
}

/**
 * Tarjeta de canción: tocarla la reproduce. ▶ es la acción principal visible;
 * practicar, editar, exportar y borrar van en el menú ⋯.
 */
@Composable
private fun TarjetaCancion(
    cancion: ResumenCancion,
    onReproducir: () -> Unit,
    onPracticar: () -> Unit,
    onEditar: () -> Unit,
    onExportar: () -> Unit,
    onBorrar: () -> Unit,
) {
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
            .fillMaxWidth()
            .tocable(etiqueta = cancion.titulo, onClick = onReproducir, alPresionar = { presionada = it }),
    ) {
        Row(
            Modifier.padding(start = Espacio.l, end = Espacio.xs, top = Espacio.m, bottom = Espacio.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(PianoColors.ManoDerecha.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = PianoColors.ManoDerecha)
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Espacio.m),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        cancion.titulo,
                        color = PianoColors.Texto,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (cancion.origen == OrigenCancion.EXPORTADA) {
                        Text(
                            stringResource(R.string.biblioteca_etiqueta_midi),
                            color = PianoColors.ManoDerecha,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .padding(start = Espacio.s)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(PianoColors.ManoDerecha.copy(alpha = 0.15f))
                                .padding(horizontal = Espacio.xs + Espacio.xxs, vertical = Espacio.xxs),
                        )
                    }
                }
                Spacer(Modifier.height(Espacio.xxs))
                Text(
                    stringResource(
                        R.string.biblioteca_detalle,
                        cancion.bpm.roundToInt(),
                        cancion.compas,
                        duracionTexto(cancion.duracionSegundos),
                        textoNotas(cancion.numNotas),
                    ),
                    color = PianoColors.TextoSecundario,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    FORMATO_FECHA.format(Instant.ofEpochMilli(cancion.fechaCreacion).atZone(ZoneId.systemDefault())),
                    color = PianoColors.TextoDeshabilitado,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            FilledIconButton(
                onClick = onReproducir,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = PianoColors.ManoDerecha, contentColor = PianoColors.SobreAcento),
                modifier = Modifier.pulsable(escala = ESCALA_ICONO),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.biblioteca_reproducir))
            }
            MenuCancion(onPracticar, onEditar, onExportar, onBorrar)
        }
    }
}

@Composable
private fun MenuCancion(
    onPracticar: () -> Unit,
    onEditar: () -> Unit,
    onExportar: () -> Unit,
    onBorrar: () -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    fun elegir(accion: () -> Unit) {
        abierto = false
        accion()
    }
    Box {
        IconButton(onClick = { abierto = true }, modifier = Modifier.pulsable(escala = ESCALA_ICONO)) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.biblioteca_mas_acciones), tint = PianoColors.TextoSecundario)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.biblioteca_practicar)) },
                leadingIcon = { Icon(Icons.Filled.School, contentDescription = null) },
                onClick = { elegir(onPracticar) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.biblioteca_editar)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { elegir(onEditar) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.biblioteca_exportar)) },
                leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                onClick = { elegir(onExportar) },
            )
            HorizontalDivider(Modifier.padding(vertical = Espacio.xs), color = PianoColors.Borde)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.accion_borrar), color = PianoColors.Error) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = PianoColors.Error) },
                onClick = { elegir(onBorrar) },
            )
        }
    }
}

@Composable
private fun EstadoVacio(filtro: FiltroBiblioteca, onNuevaCancion: () -> Unit, onImportar: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(Espacio.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(PianoColors.Superficie),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = PianoColors.TextoSecundario, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(Espacio.l))
        Text(
            stringResource(
                if (filtro == FiltroBiblioteca.EXPORTADAS) R.string.biblioteca_vacia_exportadas else R.string.biblioteca_vacia,
            ),
            color = PianoColors.TextoSecundario,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (filtro != FiltroBiblioteca.EXPORTADAS) {
            Spacer(Modifier.height(Espacio.xl))
            Button(
                onClick = onNuevaCancion,
                colors = ButtonDefaults.buttonColors(containerColor = PianoColors.ManoIzquierda, contentColor = PianoColors.SobreAcento),
                modifier = Modifier.pulsable(),
            ) { Text(stringResource(R.string.biblioteca_grabar_primera)) }
            TextButton(onClick = onImportar, modifier = Modifier.pulsable()) { Text(stringResource(R.string.biblioteca_importar_texto), color = PianoColors.ManoDerecha) }
        }
    }
}

private fun duracionTexto(segundos: Double): String {
    val s = segundos.roundToInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/** Nombre sugerido para el archivo: sin caracteres que los gestores de archivos rechazan. */
private fun nombreArchivo(titulo: String): String =
    titulo.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "cancion" } + ".mid"
