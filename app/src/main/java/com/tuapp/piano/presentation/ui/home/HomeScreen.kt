package com.tuapp.piano.presentation.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.piano.R
import com.tuapp.piano.domain.inicio.Inicio
import com.tuapp.piano.domain.model.ModoPiano
import com.tuapp.piano.presentation.ui.common.ESCALA_ICONO
import com.tuapp.piano.presentation.ui.common.pulsable
import com.tuapp.piano.presentation.ui.common.rememberSelectorMidi
import com.tuapp.piano.presentation.ui.common.tocable
import com.tuapp.piano.presentation.ui.theme.Espacio
import com.tuapp.piano.presentation.ui.theme.Movimiento
import com.tuapp.piano.presentation.ui.theme.PianoColors
import com.tuapp.piano.presentation.ui.theme.movimientoReducido
import com.tuapp.piano.presentation.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Orden de entrada: encabezado, tarjeta grande, cuatro accesos y recientes. */
private const val ELEMENTOS_ENTRADA = 7

/** A partir de este ancho, y si es más ancha que alta, la pantalla se reparte en dos columnas. */
private val ANCHO_DOS_COLUMNAS = 560.dp

/**
 * Inicio: "seguir donde lo dejaste" como protagonista, accesos rápidos y canciones recientes.
 *
 * Gira con el celular: en vertical va todo en una columna; acostado, la tarjeta grande a la
 * izquierda y el resto a la derecha. Movimiento (resortes, ver [Movimiento]):
 *  - Al abrir, los bloques suben y aparecen uno tras otro (solo la primera vez; no bloquea).
 *    La tarjeta grande además crece un poco, como si "respirara" una vez.
 *  - Todo se hunde en cuanto baja el dedo; el carrusel tiene inercia y bordes elásticos.
 */
@Composable
fun HomeScreen(
    onCrearCancion: () -> Unit,
    onBiblioteca: () -> Unit,
    onTocarLibre: () -> Unit,
    onAjustes: () -> Unit,
    onImportarTexto: () -> Unit,
    onAbrir: (ModoPiano, Long) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.mensajes.collect { snackbar.showSnackbar(it) } }
    val errorLeer = stringResource(R.string.biblioteca_error_leer_midi)
    val abrirMidi = rememberSelectorMidi(
        onLeido = vm::importarMidi,
        onError = { scope.launch { snackbar.showSnackbar(errorLeer) } },
    )

    val reducido = movimientoReducido()
    var yaEntro by rememberSaveable { mutableStateOf(false) }
    val entrada = remember { List(ELEMENTOS_ENTRADA) { Animatable(if (yaEntro || reducido) 1f else 0f) } }
    LaunchedEffect(Unit) {
        if (yaEntro) return@LaunchedEffect
        yaEntro = true
        entrada.forEachIndexed { i, progreso ->
            launch {
                delay(i * Movimiento.ESCALON_MS)
                progreso.animateTo(1f, Movimiento.entrar())
            }
        }
    }

    val acciones = Acciones(onCrearCancion, onBiblioteca, onTocarLibre, onImportarTexto, abrirMidi, onAbrir)

    Scaffold(containerColor = PianoColors.Fondo, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .drawBehind(dibujarResplandor)
                .padding(padding),
        ) {
            val dosColumnas = maxWidth > maxHeight && maxWidth >= ANCHO_DOS_COLUMNAS
            if (dosColumnas) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Espacio.xxl, vertical = Espacio.l),
                    horizontalArrangement = Arrangement.spacedBy(Espacio.xxl),
                ) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Encabezado(onAjustes, Modifier.entrada(entrada[0]))
                        Spacer(Modifier.height(Espacio.l))
                        Protagonista(ui.inicio, acciones, Modifier.entrada(entrada[1], escalaInicial = 0.96f))
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Espacio.l),
                    ) {
                        Accesos(acciones, entrada, altoTarjeta = 76.dp)
                        Recientes(ui.inicio, acciones, Modifier.entrada(entrada[6]))
                    }
                }
            } else {
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 560.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Espacio.margenPantalla, vertical = Espacio.l),
                    verticalArrangement = Arrangement.spacedBy(Espacio.xl),
                ) {
                    Encabezado(onAjustes, Modifier.entrada(entrada[0]))
                    Protagonista(ui.inicio, acciones, Modifier.entrada(entrada[1], escalaInicial = 0.96f))
                    Accesos(acciones, entrada, altoTarjeta = 92.dp)
                    Recientes(ui.inicio, acciones, Modifier.entrada(entrada[6]))
                    Spacer(Modifier.height(Espacio.s))
                }
            }
        }
    }
}

/** Lo que se puede hacer desde el inicio, para no pasar seis lambdas por cada bloque. */
private class Acciones(
    val crear: () -> Unit,
    val biblioteca: () -> Unit,
    val tocarLibre: () -> Unit,
    val importarTexto: () -> Unit,
    val importarMidi: () -> Unit,
    val abrir: (ModoPiano, Long) -> Unit,
)

@Composable
private fun Encabezado(onAjustes: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.size(Espacio.m))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                color = PianoColors.Texto,
                // Texto grande: letras algo más juntas, si no se ven sueltas.
                style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.02).em),
            )
            Text(stringResource(R.string.home_lema), color = PianoColors.TextoSecundario, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onAjustes, modifier = Modifier.pulsable(escala = ESCALA_ICONO)) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_ajustes), tint = PianoColors.TextoSecundario)
        }
    }
}

/** La tarjeta grande: seguir con una canción, o empezar si la biblioteca está vacía. */
@Composable
private fun Protagonista(inicio: Inicio, acciones: Acciones, modifier: Modifier = Modifier) {
    val c = inicio.continuar
    if (c != null) {
        TarjetaContinuar(
            cancion = c,
            modo = inicio.modoContinuar,
            yaTocada = inicio.yaTocada,
            onAbrir = { modo -> acciones.abrir(modo, c.resumen.id) },
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        TarjetaEmpezar(acciones, modifier.fillMaxWidth())
    }
}

@Composable
private fun TarjetaEmpezar(acciones: Acciones, modifier: Modifier) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = PianoColors.Superficie),
        modifier = modifier,
    ) {
        Column(Modifier.padding(Espacio.xl)) {
            Icon(Icons.Filled.Piano, contentDescription = null, tint = PianoColors.ManoIzquierda, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(Espacio.m))
            Text(stringResource(R.string.home_empezar_titulo), color = PianoColors.Texto, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Espacio.xs))
            Text(stringResource(R.string.home_empezar_desc), color = PianoColors.TextoSecundario, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Espacio.l))
            Button(
                onClick = acciones.crear,
                colors = ButtonDefaults.buttonColors(containerColor = PianoColors.ManoIzquierda, contentColor = PianoColors.SobreAcento),
                modifier = Modifier.pulsable(),
            ) { Text(stringResource(R.string.biblioteca_grabar_primera)) }
        }
    }
}

/** Cuatro accesos en 2×2. Importar abre un menú que nace de su propia tarjeta. */
@Composable
private fun Accesos(acciones: Acciones, entrada: List<Animatable<Float, *>>, altoTarjeta: Dp) {
    var menuImportar by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            Acceso(
                Icons.Filled.AddCircle, stringResource(R.string.home_acceso_crear), PianoColors.ManoIzquierda, altoTarjeta, acciones.crear,
                Modifier.weight(1f).entrada(entrada[2]),
            )
            Acceso(
                Icons.Filled.LibraryMusic, stringResource(R.string.home_acceso_biblioteca), PianoColors.ManoDerecha, altoTarjeta, acciones.biblioteca,
                Modifier.weight(1f).entrada(entrada[3]),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            Acceso(
                Icons.Filled.MusicNote, stringResource(R.string.home_acceso_libre), PianoColors.Texto, altoTarjeta, acciones.tocarLibre,
                Modifier.weight(1f).entrada(entrada[4]),
            )
            Box(Modifier.weight(1f).entrada(entrada[5])) {
                Acceso(Icons.Filled.FileUpload, stringResource(R.string.home_acceso_importar), PianoColors.Aviso, altoTarjeta, { menuImportar = true })
                DropdownMenu(expanded = menuImportar, onDismissRequest = { menuImportar = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.biblioteca_importar_midi)) },
                        leadingIcon = { Icon(Icons.Filled.AudioFile, contentDescription = null) },
                        onClick = {
                            menuImportar = false
                            acciones.importarMidi()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.biblioteca_importar_texto_menu)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.TextSnippet, contentDescription = null) },
                        onClick = {
                            menuImportar = false
                            acciones.importarTexto()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Acceso(
    icono: ImageVector,
    titulo: String,
    acento: Color,
    alto: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .fillMaxWidth()
            .height(alto)
            .tocable(etiqueta = titulo, onClick = onClick, alPresionar = { presionada = it }),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = Espacio.l), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(acento.copy(alpha = if (presionada) 0.26f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icono, contentDescription = null, tint = acento, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.size(Espacio.m))
            Text(titulo, color = PianoColors.Texto, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Recientes(inicio: Inicio, acciones: Acciones, modifier: Modifier = Modifier) {
    if (inicio.recientes.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_recientes).uppercase(),
                color = PianoColors.ManoDerecha,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = acciones.biblioteca, modifier = Modifier.pulsable()) {
                Text(stringResource(R.string.home_ver_todo), color = PianoColors.TextoSecundario)
            }
        }
        CarruselRecientes(inicio.recientes, onAbrir = { id -> acciones.abrir(ModoPiano.REPRODUCIR, id) })
    }
}

/**
 * Aparición de un bloque: opacidad, subida de [desplazamiento] dp y, si se pide, escala.
 * Solo toca la capa gráfica (no el layout), así que no mueve a los demás bloques.
 */
@Composable
private fun Modifier.entrada(progreso: Animatable<Float, *>, desplazamiento: Float = 20f, escalaInicial: Float = 1f): Modifier {
    val px = with(LocalDensity.current) { desplazamiento.dp.toPx() }
    return graphicsLayer {
        val p = progreso.value
        alpha = p
        translationY = (1f - p) * px
        val escala = escalaInicial + (1f - escalaInicial) * p
        scaleX = escala
        scaleY = escala
    }
}

/** Dos resplandores fijos con los colores de las manos: dan profundidad sin moverse. */
private val dibujarResplandor: DrawScope.() -> Unit = {
    val ancho = size.width
    drawRect(
        Brush.radialGradient(
            colors = listOf(PianoColors.ManoDerecha.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(ancho * 0.15f, 0f),
            radius = ancho * 0.75f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(PianoColors.ManoIzquierda.copy(alpha = 0.07f), Color.Transparent),
            center = Offset(ancho * 0.95f, size.height * 0.12f),
            radius = ancho * 0.6f,
        ),
    )
}
