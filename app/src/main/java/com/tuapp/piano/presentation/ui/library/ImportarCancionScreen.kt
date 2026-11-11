package com.tuapp.piano.presentation.ui.library

import androidx.compose.ui.res.stringResource
import com.tuapp.piano.presentation.ui.common.pulsable
import com.tuapp.piano.presentation.ui.common.ESCALA_ICONO
import com.tuapp.piano.R
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.piano.domain.musica.FiguraPaso
import com.tuapp.piano.presentation.ui.theme.EstiloMono
import com.tuapp.piano.presentation.ui.common.textoPasos
import com.tuapp.piano.presentation.ui.common.textoNotas
import com.tuapp.piano.presentation.ui.theme.Espacio
import com.tuapp.piano.presentation.ui.theme.PianoColors
import com.tuapp.piano.presentation.viewmodel.COMPASES_IMPORTAR
import com.tuapp.piano.presentation.viewmodel.EventoImportar
import com.tuapp.piano.presentation.viewmodel.ImportarViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Importa una canción escrita en notación de texto ([C4E4] D4 E4 …): pegándola o
 * abriendo un .txt. Muestra en vivo cuántas notas se reconocieron o el error exacto.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportarCancionScreen(
    onVolver: () -> Unit,
    onGuardada: () -> Unit,
    vm: ImportarViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val alGuardar by rememberUpdatedState(onGuardada)
    val errorLeer = stringResource(R.string.importar_error_leer)

    LaunchedEffect(Unit) {
        vm.eventos.collect { e ->
            when (e) {
                is EventoImportar.Guardada -> alGuardar()
                is EventoImportar.Mensaje -> snackbar.showSnackbar(e.texto)
            }
        }
    }

    val abrirTxt = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val leido = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = contexto.contentResolver
                    val nombre = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    val texto = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    texto to nombre
                }
            }
            leido.onSuccess { (texto, nombre) -> vm.cargarArchivo(texto, nombre) }
                .onFailure { snackbar.showSnackbar(errorLeer) }
        }
    }

    Scaffold(
        containerColor = PianoColors.Fondo,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.importar_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onVolver, modifier = Modifier.pulsable(escala = ESCALA_ICONO)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accion_volver))
                    }
                },
                actions = {
                    TextButton(onClick = { abrirTxt.launch(arrayOf("text/plain")) }, modifier = Modifier.pulsable()) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Espacio.s))
                        Text(stringResource(R.string.importar_abrir_txt))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PianoColors.Fondo,
                    titleContentColor = PianoColors.Texto,
                    navigationIconContentColor = PianoColors.Texto,
                    actionIconContentColor = PianoColors.ManoDerecha,
                ),
            )
        },
        bottomBar = {
            Button(
                onClick = vm::guardar,
                enabled = ui.vista?.valida == true && !ui.guardando,
                colors = ButtonDefaults.buttonColors(containerColor = PianoColors.ManoDerecha, contentColor = PianoColors.SobreAcento),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Espacio.margenPantalla)
                    .height(56.dp)
                    .pulsable(habilitado = ui.vista?.valida == true && !ui.guardando),
            ) {
                if (ui.guardando) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = PianoColors.SobreAcento)
                } else {
                    Text(stringResource(R.string.importar_guardar), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.margenPantalla),
            verticalArrangement = Arrangement.spacedBy(Espacio.xl),
        ) {
            OutlinedTextField(
                value = ui.titulo,
                onValueChange = vm::cambiarTitulo,
                label = { Text(stringResource(R.string.importar_campo_titulo)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Rotulo(stringResource(R.string.importar_notacion))
                OutlinedTextField(
                    value = ui.texto,
                    onValueChange = vm::cambiarTexto,
                    placeholder = { Text(stringResource(R.string.importar_placeholder), fontFamily = FontFamily.Monospace) },
                    textStyle = EstiloMono.copy(color = PianoColors.Texto),
                    minLines = 6,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.importar_ayuda),
                    color = PianoColors.TextoSecundario,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Espacio.s),
                )
            }

            VistaPreviaCard(ui.vista?.let { v ->
                if (v.error != null) {
                    v.error to PianoColors.Error
                } else {
                    val s = v.segundos.roundToInt()
                    stringResource(R.string.importar_resumen, textoNotas(v.notas), textoPasos(v.pasos), "%d:%02d".format(s / 60, s % 60)) to PianoColors.ManoDerecha
                }
            })

            Column {
                Rotulo(stringResource(R.string.rotulo_tempo))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { vm.cambiarBpm(ui.bpm - 5) }, modifier = Modifier.pulsable(escala = ESCALA_ICONO)) {
                        Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.bpm_menos))
                    }
                    Text(
                        stringResource(R.string.bpm_valor, ui.bpm),
                        color = PianoColors.Texto,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(onClick = { vm.cambiarBpm(ui.bpm + 5) }, modifier = Modifier.pulsable(escala = ESCALA_ICONO)) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.bpm_mas))
                    }
                }
            }

            Column {
                Rotulo(stringResource(R.string.importar_duracion_paso))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                    FiguraPaso.entries.forEach { f ->
                        FilterChip(selected = ui.figura == f, onClick = { vm.cambiarFigura(f) }, label = { Text(f.etiqueta) }, modifier = Modifier.pulsable())
                    }
                }
            }

            Column {
                Rotulo(stringResource(R.string.rotulo_compas))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                    COMPASES_IMPORTAR.forEach { c ->
                        FilterChip(selected = ui.compas == c, onClick = { vm.cambiarCompas(c) }, label = { Text(c.toString()) }, modifier = Modifier.pulsable())
                    }
                }
            }
            Spacer(Modifier.height(Espacio.s))
        }
    }
}

@Composable
private fun VistaPreviaCard(contenido: Pair<String, Color>?) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = PianoColors.Superficie),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            contenido?.first ?: stringResource(R.string.importar_vista_vacia),
            color = contenido?.second ?: PianoColors.TextoSecundario,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Espacio.l),
        )
    }
}

@Composable
private fun Rotulo(texto: String) {
    Text(
        texto.uppercase(),
        color = PianoColors.ManoDerecha,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(bottom = Espacio.s),
    )
}
