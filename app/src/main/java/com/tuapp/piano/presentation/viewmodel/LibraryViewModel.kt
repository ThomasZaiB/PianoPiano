package com.tuapp.piano.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.piano.domain.model.OrigenCancion
import com.tuapp.piano.domain.model.ResumenCancion
import com.tuapp.piano.domain.repository.CancionRepository
import com.tuapp.piano.domain.usecase.ExportarCancionUseCase
import com.tuapp.piano.domain.usecase.ImportarMidiUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import javax.inject.Inject

/** Filtros de la biblioteca. El nombre visible lo pone la pantalla (strings.xml). */
enum class FiltroBiblioteca { TODAS, CREADAS, EXPORTADAS }

data class LibraryUiState(
    val cargando: Boolean = true,
    val filtro: FiltroBiblioteca = FiltroBiblioteca.TODAS,
    val canciones: List<ResumenCancion> = emptyList(),
    val conteo: Map<FiltroBiblioteca, Int> = emptyMap(),
    val busqueda: String = "",
    /** Solo con muchas canciones tiene sentido buscar. */
    val mostrarBusqueda: Boolean = false,
)

/** Avisos de la biblioteca; la pantalla pone el texto. */
sealed interface MensajeBiblioteca {
    /** Se muestra con "Deshacer": el borrado real espera a que el aviso se cierre. */
    data class Borrada(val id: Long, val titulo: String) : MensajeBiblioteca
    data class ErrorBorrar(val detalle: String?) : MensajeBiblioteca
    data object Exportada : MensajeBiblioteca
    data class ErrorExportar(val detalle: String?) : MensajeBiblioteca
    data class Importacion(val resultado: ResultadoImportacionMidi) : MensajeBiblioteca
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repositorio: CancionRepository,
    private val exportarCancion: ExportarCancionUseCase,
    private val importarMidi: ImportarMidiUseCase,
) : ViewModel() {

    private val filtro = MutableStateFlow(FiltroBiblioteca.TODAS)
    private val busqueda = MutableStateFlow("")

    /** Canción borrada que aún se puede recuperar: se oculta de la lista pero sigue en la base. */
    private val pendienteDeBorrar = MutableStateFlow<ResumenCancion?>(null)

    val ui: StateFlow<LibraryUiState> =
        combine(repositorio.observarResumenes(), filtro, busqueda, pendienteDeBorrar) { resumenes, f, texto, pendiente ->
            val todas = resumenes.filter { it.id != pendiente?.id }
            val creadas = todas.filter { it.origen == OrigenCancion.CREADA }
            val exportadas = todas.filter { it.origen == OrigenCancion.EXPORTADA }
            val delFiltro = when (f) {
                FiltroBiblioteca.TODAS -> todas
                FiltroBiblioteca.CREADAS -> creadas
                FiltroBiblioteca.EXPORTADAS -> exportadas
            }
            val mostrarBusqueda = todas.size > UMBRAL_BUSQUEDA
            val consulta = normalizar(texto)
            LibraryUiState(
                cargando = false,
                filtro = f,
                canciones = if (mostrarBusqueda && consulta.isNotEmpty()) {
                    delFiltro.filter { normalizar(it.titulo).contains(consulta) }
                } else {
                    delFiltro
                },
                conteo = mapOf(
                    FiltroBiblioteca.TODAS to todas.size,
                    FiltroBiblioteca.CREADAS to creadas.size,
                    FiltroBiblioteca.EXPORTADAS to exportadas.size,
                ),
                busqueda = texto,
                mostrarBusqueda = mostrarBusqueda,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private val _mensajes = Channel<MensajeBiblioteca>(Channel.BUFFERED)
    val mensajes: Flow<MensajeBiblioteca> = _mensajes.receiveAsFlow()

    fun cambiarFiltro(nuevo: FiltroBiblioteca) {
        filtro.value = nuevo
    }

    fun buscar(texto: String) {
        busqueda.value = texto.take(60)
    }

    /**
     * Oculta la canción y ofrece deshacer. Si había otra pendiente, esa se borra ya: solo
     * se puede deshacer el último borrado, como en cualquier lista con "Deshacer".
     */
    fun borrar(cancion: ResumenCancion) {
        pendienteDeBorrar.value?.let { borrarDeVerdad(it) }
        pendienteDeBorrar.value = cancion
        viewModelScope.launch { _mensajes.send(MensajeBiblioteca.Borrada(cancion.id, cancion.titulo)) }
    }

    /** Por id: si entretanto se borró otra, el aviso viejo no debe tocar la nueva. */
    fun deshacerBorrado(id: Long) {
        if (pendienteDeBorrar.value?.id != id) return
        pendienteDeBorrar.value = null
    }

    /** El aviso se cerró sin "Deshacer". */
    fun confirmarBorrado(id: Long) {
        val p = pendienteDeBorrar.value?.takeIf { it.id == id } ?: return
        borrarDeVerdad(p)
    }

    private fun borrarDeVerdad(cancion: ResumenCancion) {
        viewModelScope.launch {
            runCatching { repositorio.borrar(cancion.id) }
                .onFailure { _mensajes.send(MensajeBiblioteca.ErrorBorrar(it.message)) }
            // Se quita de pendientes cuando la base ya no la tiene: así no reaparece un instante.
            if (pendienteDeBorrar.value?.id == cancion.id) pendienteDeBorrar.value = null
        }
    }

    /** Contenido de un .mid elegido con el selector de archivos. */
    fun importarMidi(bytes: ByteArray, nombreArchivo: String?) {
        viewModelScope.launch {
            _mensajes.send(MensajeBiblioteca.Importacion(importarMidiConResultado(importarMidi, bytes, nombreArchivo)))
        }
    }

    /** [destinoUri] es la URI que devolvió el selector de archivos (SAF). */
    fun exportar(cancionId: Long, destinoUri: String) {
        viewModelScope.launch {
            runCatching { exportarCancion(cancionId, destinoUri) }
                .onSuccess { _mensajes.send(MensajeBiblioteca.Exportada) }
                .onFailure { _mensajes.send(MensajeBiblioteca.ErrorExportar(it.message)) }
        }
    }

    override fun onCleared() {
        // Se salió de la biblioteca con un borrado pendiente: se completa aunque el
        // ViewModel ya no exista (su scope está cancelado, de ahí el scope propio).
        val p = pendienteDeBorrar.value ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            withContext(NonCancellable) { runCatching { repositorio.borrar(p.id) } }
        }
    }

    private companion object {
        const val UMBRAL_BUSQUEDA = 10

        /** Minúsculas y sin tildes: "canción" encuentra "Cancion". */
        fun normalizar(texto: String): String =
            Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    }
}
