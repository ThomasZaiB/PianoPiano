package com.tuapp.piano.domain.audio

import com.tuapp.piano.domain.model.ErrorMotor
import com.tuapp.piano.domain.model.EstadoMotor
import com.tuapp.piano.domain.repository.AudioEngineRepository
import com.tuapp.piano.domain.repository.FocoAudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dueño ÚNICO del ciclo de vida del stream de audio (y del foco de audio, que va con él).
 * Kotlin puro: usa solo interfaces de dominio.
 *
 * Por qué hace falta con navegación: al ir de la Biblioteca a una canción y volver
 * a otra, durante la animación conviven dos PianoViewModel. Si cada uno abriera y
 * cerrara el stream por su cuenta, el segundo podría intentar cargar el SoundFont
 * con el stream abierto (el motor lo rechaza) o el primero cerrarlo en mitad de
 * la reproducción del segundo. Aquí se cuenta cuántas pantallas lo usan:
 *  - el SoundFont se carga una sola vez por proceso;
 *  - el stream se abre con el primer usuario y se cierra con el último.
 */
@Singleton
class GestorMotorAudio @Inject constructor(
    private val motor: AudioEngineRepository,
    private val foco: FocoAudioRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var soundFontCargado = false
    @Volatile private var usuarios = 0

    private val _estado = MutableStateFlow(EstadoMotor.CARGANDO)
    val estado: StateFlow<EstadoMotor> = _estado.asStateFlow()

    private val _soundFont = MutableStateFlow<String?>(null)
    val soundFont: StateFlow<String?> = _soundFont.asStateFlow()

    private val _error = MutableStateFlow<ErrorMotor?>(null)
    val error: StateFlow<ErrorMotor?> = _error.asStateFlow()

    private val _focoPerdido = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emite cuando una llamada u otra app se queda con el audio: hay que pausar. */
    val focoPerdido: SharedFlow<Unit> = _focoPerdido.asSharedFlow()

    private val alPerderFoco: () -> Unit = { _focoPerdido.tryEmit(Unit) }

    /**
     * Devuelve true si el stream quedó sonando. Cada `true` debe emparejarse con [liberar].
     * No cancelable: si se cancelara a mitad de motor.iniciar(), el stream quedaría
     * abierto sin nadie que lo cuente.
     */
    suspend fun adquirir(): Boolean = withContext(NonCancellable) { adquirirInterno() }

    private suspend fun adquirirInterno(): Boolean = mutex.withLock {
        if (!soundFontCargado) {
            val resultado = motor.cargarSoundFont()
            val nombre = resultado.getOrNull()
            if (nombre == null) {
                _error.value = ErrorMotor.SOUNDFONT
                _estado.value = EstadoMotor.ERROR
                return@withLock false
            }
            soundFontCargado = true
            _soundFont.value = nombre
        }
        if (usuarios == 0) {
            val ok = withContext(Dispatchers.IO) { motor.iniciar() }
            if (!ok) {
                _error.value = ErrorMotor.STREAM
                _estado.value = EstadoMotor.ERROR
                return@withLock false
            }
            foco.solicitar(alPerderFoco)
        }
        usuarios++
        _error.value = null
        _estado.value = EstadoMotor.SONANDO
        true
    }

    /**
     * Antes de arrancar una reproducción, grabación o práctica: si otra app se quedó con el
     * foco mientras tanto, el usuario que pulsa Play quiere volver a sonar.
     */
    fun asegurarFoco() {
        if (usuarios > 0) foco.solicitar(alPerderFoco)
    }

    /**
     * Comprobación periódica (la hace el ViewModel con su sondeo de diagnóstico): si el
     * stream se cayó y el motor no pudo reabrirlo solo (p. ej. tras desconectar unos
     * auriculares), se reintenta; si tampoco se puede, el estado pasa a ERROR en vez de
     * quedarse en SONANDO con silencio. Hilo de E/S.
     */
    suspend fun vigilarStream() = mutex.withLock {
        if (usuarios == 0 || motor.streamAbierto()) return@withLock
        if (motor.iniciar()) {
            _error.value = null
            _estado.value = EstadoMotor.SONANDO
        } else {
            _error.value = ErrorMotor.SALIDA_PERDIDA
            _estado.value = EstadoMotor.ERROR
        }
    }

    /**
     * No suspende: se puede llamar desde ViewModel.onCleared(), cuando viewModelScope
     * ya está cancelado. El cierre real ocurre en el scope propio del gestor.
     */
    fun liberar() {
        scope.launch {
            mutex.withLock {
                if (usuarios == 0) return@withLock
                usuarios--
                if (usuarios == 0) {
                    motor.transporteDetener()
                    motor.todasLasNotasOff()
                    withContext(Dispatchers.IO) { motor.detener() }
                    foco.abandonar()
                    _estado.value = EstadoMotor.LISTO
                }
            }
        }
    }
}
