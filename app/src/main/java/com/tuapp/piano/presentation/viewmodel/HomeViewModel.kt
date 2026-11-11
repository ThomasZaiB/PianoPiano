package com.tuapp.piano.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.piano.domain.inicio.Inicio
import com.tuapp.piano.domain.inicio.armarInicio
import com.tuapp.piano.domain.repository.ActividadRepository
import com.tuapp.piano.domain.repository.CancionRepository
import com.tuapp.piano.domain.usecase.ImportarMidiUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val cargando: Boolean = true,
    val inicio: Inicio = Inicio(),
)

/** Inicio: tarjeta "Continuar", recientes y accesos. Se actualiza solo al cambiar la biblioteca. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    repositorio: CancionRepository,
    actividad: ActividadRepository,
    private val importarMidiUseCase: ImportarMidiUseCase,
) : ViewModel() {

    val ui: StateFlow<HomeUiState> =
        combine(repositorio.observarResumenes(), actividad.actividad) { canciones, a -> armarInicio(canciones, a) }
            .map { HomeUiState(cargando = false, inicio = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = _mensajes.receiveAsFlow()

    fun importarMidi(bytes: ByteArray, nombreArchivo: String?) {
        viewModelScope.launch { _mensajes.send(importarMidiConMensaje(importarMidiUseCase, bytes, nombreArchivo)) }
    }
}
