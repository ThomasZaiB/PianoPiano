package com.tuapp.piano.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.piano.domain.model.Compas
import com.tuapp.piano.domain.model.PPQ
import com.tuapp.piano.domain.musica.CancionDeTexto
import com.tuapp.piano.domain.musica.ErrorNotacion
import com.tuapp.piano.domain.musica.FiguraPaso
import com.tuapp.piano.domain.usecase.ImportarCancionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

val COMPASES_IMPORTAR = listOf(Compas(4, 4), Compas(3, 4), Compas(2, 4), Compas(6, 8))

data class VistaPrevia(
    val notas: Int = 0,
    val pasos: Int = 0,
    val segundos: Double = 0.0,
    val error: String? = null,
) {
    val valida: Boolean get() = error == null && notas > 0
}

data class ImportarUiState(
    val titulo: String = "",
    val texto: String = "",
    val bpm: Int = 100,
    val compas: Compas = Compas(4, 4),
    val figura: FiguraPaso = FiguraPaso.CORCHEA,
    val vista: VistaPrevia? = null,
    val guardando: Boolean = false,
)

sealed interface EventoImportar {
    data class Guardada(val id: Long) : EventoImportar
    data class Mensaje(val texto: String) : EventoImportar
}

@HiltViewModel
class ImportarViewModel @Inject constructor(
    private val importar: ImportarCancionUseCase,
) : ViewModel() {

    private val _ui = MutableStateFlow(ImportarUiState())
    val ui: StateFlow<ImportarUiState> = _ui.asStateFlow()

    private val _eventos = Channel<EventoImportar>(Channel.BUFFERED)
    val eventos: Flow<EventoImportar> = _eventos.receiveAsFlow()

    private var trabajoVista: Job? = null

    fun cambiarTitulo(t: String) = _ui.update { it.copy(titulo = t.take(60)) }

    fun cambiarTexto(t: String) {
        val antes = _ui.value.texto
        _ui.update { it.copy(texto = t) }
        // Pegado sobre un campo vacío: si trae cabecera (# titulo, # bpm…), se aplica.
        if (antes.isBlank() && t.isNotBlank()) aplicarCabecera(t)
        recalcular()
    }

    /** Contenido de un .txt abierto con el selector de archivos. */
    fun cargarArchivo(texto: String, nombreArchivo: String?) {
        _ui.update { it.copy(texto = texto) }
        aplicarCabecera(texto, tituloPorDefecto = nombreArchivo?.substringBeforeLast('.'))
        recalcular()
    }

    fun cambiarBpm(bpm: Int) { _ui.update { it.copy(bpm = bpm.coerceIn(40, 240)) }; recalcular() }
    fun cambiarCompas(c: Compas) { _ui.update { it.copy(compas = c) }; recalcular() }
    fun cambiarFigura(f: FiguraPaso) { _ui.update { it.copy(figura = f) }; recalcular() }

    fun guardar() {
        val s = _ui.value
        if (s.guardando || s.vista?.valida != true) return
        _ui.update { it.copy(guardando = true) }
        viewModelScope.launch {
            runCatching {
                importar(
                    titulo = s.titulo,
                    notacion = CancionDeTexto.leer(s.texto).notacion,
                    bpm = s.bpm,
                    compas = s.compas,
                    figura = s.figura,
                )
            }.onSuccess { id ->
                _eventos.send(EventoImportar.Guardada(id))
            }.onFailure { e ->
                _ui.update { it.copy(guardando = false) }
                _eventos.send(EventoImportar.Mensaje("No se pudo guardar: ${e.message}"))
            }
        }
    }

    private fun aplicarCabecera(texto: String, tituloPorDefecto: String? = null) {
        val c = CancionDeTexto.leer(texto)
        _ui.update { s ->
            s.copy(
                titulo = c.titulo ?: s.titulo.ifBlank { tituloPorDefecto ?: "" },
                bpm = c.bpm?.coerceIn(40, 240) ?: s.bpm,
                compas = COMPASES_IMPORTAR.firstOrNull { it.numerador == c.numerador && it.denominador == c.denominador } ?: s.compas,
                figura = c.figura ?: s.figura,
            )
        }
    }

    /** Vista previa con un pequeño retardo para no analizar en cada tecla pulsada. */
    private fun recalcular() {
        trabajoVista?.cancel()
        trabajoVista = viewModelScope.launch {
            delay(250)
            val s = _ui.value
            if (s.texto.isBlank()) {
                _ui.update { it.copy(vista = null) }
                return@launch
            }
            val vista = withContext(Dispatchers.Default) { calcularVista(s) }
            _ui.update { it.copy(vista = vista) }
        }
    }

    private fun calcularVista(s: ImportarUiState): VistaPrevia = try {
        val c = ImportarCancionUseCase.construir(
            titulo = s.titulo,
            notacion = CancionDeTexto.leer(s.texto).notacion,
            bpm = s.bpm,
            compas = s.compas,
            figura = s.figura,
            ahoraMillis = 0L,
        )
        val ticksMusica = c.duracionTicks - s.compas.ticksPorCompas
        VistaPrevia(
            notas = c.notas.size,
            pasos = (ticksMusica / s.figura.ticks).toInt(),
            segundos = c.duracionTicks * 60.0 / (s.bpm * PPQ),
        )
    } catch (e: ErrorNotacion) {
        VistaPrevia(error = e.message)
    } catch (e: IllegalArgumentException) {
        VistaPrevia(error = e.message)
    }
}
