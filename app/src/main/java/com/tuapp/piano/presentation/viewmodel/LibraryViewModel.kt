package com.tuapp.piano.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.piano.domain.model.OrigenCancion
import com.tuapp.piano.domain.model.ResumenCancion
import com.tuapp.piano.domain.repository.CancionRepository
import com.tuapp.piano.domain.usecase.ExportarCancionUseCase
import com.tuapp.piano.domain.usecase.ImportarMidiUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FiltroBiblioteca(val etiqueta: String) {
    TODAS("Todas"),
    CREADAS("Creadas"),
    EXPORTADAS("Exportadas"),
}

data class LibraryUiState(
    val cargando: Boolean = true,
    val filtro: FiltroBiblioteca = FiltroBiblioteca.TODAS,
    val canciones: List<ResumenCancion> = emptyList(),
    val conteo: Map<FiltroBiblioteca, Int> = emptyMap(),
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repositorio: CancionRepository,
    private val exportarCancion: ExportarCancionUseCase,
    private val importarMidi: ImportarMidiUseCase,
) : ViewModel() {

    private val filtro = MutableStateFlow(FiltroBiblioteca.TODAS)

    val ui: StateFlow<LibraryUiState> =
        combine(repositorio.observarResumenes(), filtro) { todas, f ->
            val creadas = todas.filter { it.origen == OrigenCancion.CREADA }
            val exportadas = todas.filter { it.origen == OrigenCancion.EXPORTADA }
            LibraryUiState(
                cargando = false,
                filtro = f,
                canciones = when (f) {
                    FiltroBiblioteca.TODAS -> todas
                    FiltroBiblioteca.CREADAS -> creadas
                    FiltroBiblioteca.EXPORTADAS -> exportadas
                },
                conteo = mapOf(
                    FiltroBiblioteca.TODAS to todas.size,
                    FiltroBiblioteca.CREADAS to creadas.size,
                    FiltroBiblioteca.EXPORTADAS to exportadas.size,
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = _mensajes.receiveAsFlow()

    fun cambiarFiltro(nuevo: FiltroBiblioteca) {
        filtro.value = nuevo
    }

    fun borrar(cancion: ResumenCancion) {
        viewModelScope.launch {
            runCatching { repositorio.borrar(cancion.id) }
                .onSuccess { _mensajes.send("«${cancion.titulo}» borrada") }
                .onFailure { _mensajes.send("No se pudo borrar: ${it.message}") }
        }
    }

    /** Contenido de un .mid elegido con el selector de archivos. */
    fun importarMidi(bytes: ByteArray, nombreArchivo: String?) {
        viewModelScope.launch { _mensajes.send(importarMidiConMensaje(importarMidi, bytes, nombreArchivo)) }
    }

    /** [destinoUri] es la URI que devolvió el selector de archivos (SAF). */
    fun exportar(cancionId: Long, destinoUri: String) {
        viewModelScope.launch {
            runCatching { exportarCancion(cancionId, destinoUri) }
                .onSuccess { _mensajes.send("Archivo MIDI guardado") }
                .onFailure { _mensajes.send("No se pudo exportar: ${it.message}") }
        }
    }
}
