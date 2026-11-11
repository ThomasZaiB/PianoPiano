package com.tuapp.piano.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.piano.domain.audio.GestorMotorAudio
import com.tuapp.piano.domain.grabacion.GrabadorNotas
import com.tuapp.piano.domain.model.Ajustes
import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.Compas
import com.tuapp.piano.domain.model.ConfigGrabacion
import com.tuapp.piano.domain.model.Cuantizacion
import com.tuapp.piano.domain.model.EstadoMotor
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.ModoPiano
import com.tuapp.piano.domain.model.NombresNotas
import com.tuapp.piano.domain.model.PPQ
import com.tuapp.piano.domain.model.RelojSnapshot
import com.tuapp.piano.domain.practica.EvaluadorPractica
import com.tuapp.piano.domain.practica.SeleccionPractica
import com.tuapp.piano.domain.repository.ActividadRepository
import com.tuapp.piano.domain.repository.AjustesRepository
import com.tuapp.piano.domain.repository.AudioEngineRepository
import com.tuapp.piano.domain.repository.CancionRepository
import com.tuapp.piano.domain.usecase.GuardarGrabacionUseCase
import com.tuapp.piano.domain.usecase.MetronomoUseCase
import com.tuapp.piano.domain.usecase.ReproducirCancionUseCase
import com.tuapp.piano.presentation.navigation.Rutas
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// =====================================================================================
// Estado de la pantalla
// =====================================================================================

enum class EstadoGrabacion {
    /** Sin grabación en curso (LIBRE antes de "Grabar rápido", REPRODUCIR, PRACTICA). */
    INACTIVA,
    LISTA,
    GRABANDO,
    PAUSADA,
}

enum class EstadoPractica { LISTA, EN_CURSO, ESPERANDO, PAUSADA, TERMINADA }

data class PracticaUi(
    val estado: EstadoPractica = EstadoPractica.LISTA,
    /** Notas que el usuario debe tocar ahora mismo (p. ej. "Do4 · Mi4"). */
    val pendientes: String = "",
    val aciertos: Int = 0,
    val errores: Int = 0,
    val precision: Int = 100,
    val racha: Int = 0,
    val mejorRacha: Int = 0,
    /** Vuelta actual cuando se repite un tramo en bucle (empieza en 1). */
    val vuelta: Int = 1,
    val ultimoError: String? = null,
    /** Cambia con cada error para que la UI pueda hacer un destello aunque el texto se repita. */
    val idError: Int = 0,
)

data class PianoUiState(
    val modo: ModoPiano = ModoPiano.LIBRE,
    // Motor
    val estadoMotor: EstadoMotor = EstadoMotor.CARGANDO,
    val motorListo: Boolean = false,
    val soundFont: String? = null,
    val error: String? = null,
    val latenciaMs: Double? = null,
    val xRuns: Int? = null,
    // Canción y vista
    val cargandoCancion: Boolean = false,
    val cancion: Cancion? = null,
    /** Única fuente del tempo: la UI, la barra de progreso y el motor leen este valor (puede tener decimales: MIDI importado). */
    val bpm: Double = 100.0,
    val compas: Compas = Compas(),
    val pedalAbajo: Boolean = false,
    val teclasVisibles: Int = 21,
    val ajustes: Ajustes = Ajustes(),
    // REPRODUCIR
    val reproduciendo: Boolean = false,
    // GRABAR / Grabar rápido
    val grabacion: EstadoGrabacion = EstadoGrabacion.INACTIVA,
    val notasGrabadas: Int = 0,
    val metronomo: Boolean = true,
    val dialogoGuardar: Boolean = false,
    val tituloSugerido: String = "",
    val guardando: Boolean = false,
    // PRACTICA
    val practica: PracticaUi = PracticaUi(),
    val seleccionPractica: SeleccionPractica = SeleccionPractica(),
    val totalCompases: Int = 1,
) {
    val hayGrabacionSinGuardar: Boolean
        get() = notasGrabadas > 0 && grabacion in setOf(EstadoGrabacion.GRABANDO, EstadoGrabacion.PAUSADA)
}

/** Resultado de una pulsación en práctica: el teclado lo muestra como un destello verde o rojo. */
data class ResultadoTecla(val nota: Int, val acierto: Boolean)

sealed interface EventoPiano {
    data class Mensaje(val texto: String) : EventoPiano

    /** [volverABiblioteca]: en modo GRABAR se navega a la biblioteca; en "Grabar rápido" no. */
    data class Guardada(val id: Long, val volverABiblioteca: Boolean) : EventoPiano
}

// =====================================================================================

/**
 * ViewModel de la pantalla del piano para los 4 modos.
 *
 * Reglas que respeta:
 *  - Solo habla con el motor a través de AudioEngineRepository (AudioBridge no cambia).
 *  - El ciclo de vida del stream lo lleva GestorMotorAudio (compartido entre pantallas).
 *  - Todo el estado mutable se toca desde el hilo principal: allí llegan los toques del
 *    teclado, los callbacks de las corrutinas (Dispatchers.Main) y los ciclos de vida.
 */
@HiltViewModel
class PianoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val motor: AudioEngineRepository,
    private val gestor: GestorMotorAudio,
    private val repositorio: CancionRepository,
    private val reproducirCancion: ReproducirCancionUseCase,
    private val metronomoUseCase: MetronomoUseCase,
    private val guardarGrabacion: GuardarGrabacionUseCase,
    private val ajustesRepo: AjustesRepository,
    private val actividad: ActividadRepository,
) : ViewModel() {

    // ---- Argumentos de navegación (SavedStateHandle) ----
    val modo: ModoPiano = ModoPiano.desde(savedStateHandle[Rutas.ARG_MODO])
    private val cancionId: Long = savedStateHandle.get<Long>(Rutas.ARG_CANCION_ID) ?: 0L

    /** Config de grabación. En overdub se sustituye por la de la canción al cargarla. */
    private var config = ConfigGrabacion(
        bpm = (savedStateHandle.get<Int>(Rutas.ARG_BPM) ?: 100).coerceIn(40, 240),
        compas = runCatching {
            Compas(savedStateHandle.get<Int>(Rutas.ARG_NUMERADOR) ?: 4, savedStateHandle.get<Int>(Rutas.ARG_DENOMINADOR) ?: 4)
        }.getOrDefault(Compas()),
        cuantizacion = Cuantizacion.desde(savedStateHandle[Rutas.ARG_CUANTIZACION]),
        cuantizacionHumana = savedStateHandle.get<Boolean>(Rutas.ARG_HUMANA) ?: true,
        // En "Tocar libremente" el Grabar rápido empieza sin metrónomo.
        metronomo = modo != ModoPiano.LIBRE && (savedStateHandle.get<Boolean>(Rutas.ARG_METRONOMO) ?: true),
    )

    private val _ui = MutableStateFlow(
        PianoUiState(
            modo = modo,
            bpm = config.bpm.toDouble(),
            compas = config.compas,
            metronomo = config.metronomo,
            cargandoCancion = cancionId > 0,
            grabacion = if (modo == ModoPiano.GRABAR) EstadoGrabacion.LISTA else EstadoGrabacion.INACTIVA,
        ),
    )
    val ui: StateFlow<PianoUiState> = _ui.asStateFlow()

    private val _eventos = Channel<EventoPiano>(Channel.BUFFERED)
    val eventos: Flow<EventoPiano> = _eventos.receiveAsFlow()

    // Sin reintentos: si la UI va atrasada se pierde un destello, nunca se bloquea el toque.
    private val _resultadosTecla = MutableSharedFlow<ResultadoTecla>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val resultadosTecla: SharedFlow<ResultadoTecla> = _resultadosTecla.asSharedFlow()

    // ---- Estado interno ----
    private var adquirido = false
    private var adquiriendo = false
    private var enPrimerPlano = false
    private var limpiado = false
    private var primeraPreparacion = true

    private val reloj = RelojSnapshot()
    private val grabador = GrabadorNotas()
    private var evaluador: EvaluadorPractica? = null
    private var seleccion = SeleccionPractica()
    /** Notas de la mano que no se practica; null si se practican las dos. */
    private var cancionAcompanamiento: Cancion? = null

    private var trabajoMetronomo: Job? = null
    private var trabajoMonitorPractica: Job? = null
    private var trabajoDiagnostico: Job? = null

    // ---- Transporte: un único dueño de "la operación en curso" ----
    /**
     * Ámbito de la operación de transporte en curso: su arranque (que espera a que el motor
     * aplique un Seek) y todo lo que lanza (reproducción, metrónomo, acompañamiento, espera
     * al final). Cada comando nuevo la cancela ENTERA antes de empezar la suya, así que una
     * corrutina atrasada nunca arranca nada para un estado que ya cambió.
     */
    private var operacion: CoroutineScope? = null

    /** true desde que este ViewModel arranca el reloj del motor hasta que lo para. */
    private var transporteCorriendo = false

    /**
     * Seek pedido al motor y aún no confirmado por su reloj. Mientras exista es la posición
     * real: el reloj puede seguir mostrando la vieja durante un callback de audio.
     */
    private var destinoSalto: Double? = null

    init {
        viewModelScope.launch {
            combine(gestor.estado, gestor.soundFont, gestor.error) { estado, sf, error -> Triple(estado, sf, error) }
                .collect { (estado, sf, error) -> _ui.update { it.copy(estadoMotor = estado, soundFont = sf, error = error) } }
        }
        viewModelScope.launch {
            ajustesRepo.ajustes.collect { a ->
                val reverbAntes = _ui.value.ajustes.reverberacion
                _ui.update { it.copy(ajustes = a, teclasVisibles = a.teclasVisibles) }
                // Cambiada en Ajustes con el piano abierto (o al volver): el motor se entera ya.
                if (adquirido && a.reverberacion != reverbAntes) motor.reverberacion(a.reverberacion)
            }
        }
        viewModelScope.launch {
            // Llamada entrante u otra app de música: se pausa todo, como al ir a segundo plano.
            gestor.focoPerdido.collect { if (adquirido) detenerTodo() }
        }
        if (cancionId > 0) cargarCancion()
    }

    // =================================================================================
    // Ciclo de vida (PianoScreen llama a onStart/onStop; onCleared al salir de la ruta)
    // =================================================================================

    fun onStart() {
        enPrimerPlano = true
        if (adquirido || adquiriendo) return
        adquiriendo = true
        viewModelScope.launch {
            // No cancelable: si el ViewModel se destruye a mitad, igualmente hay que
            // devolver el stream al gestor para que su contador no quede descuadrado.
            val listo = withContext(NonCancellable) {
                val ok = gestor.adquirir()
                adquiriendo = false
                when {
                    !ok -> false
                    !enPrimerPlano || limpiado -> { gestor.liberar(); false }
                    else -> { adquirido = true; true }
                }
            }
            if (listo) prepararMotor()
        }
    }

    fun onStop() {
        enPrimerPlano = false
        detenerTodo()
        if (adquirido) {
            adquirido = false
            gestor.liberar()
        }
        _ui.update { it.copy(motorListo = false) }
    }

    override fun onCleared() {
        limpiado = true
        detenerTodo()
        if (adquirido) {
            adquirido = false
            gestor.liberar()
        }
    }

    private suspend fun prepararMotor() {
        motor.setTempo(_ui.value.bpm)
        motor.reverberacion(_ui.value.ajustes.reverberacion)
        if (primeraPreparacion) {
            // El motor es compartido: otra pantalla pudo dejarlo en cualquier posición.
            primeraPreparacion = false
            pararMotorTransporte()
            pedirSalto(0.0)
            esperarSalto(0.0)
        }
        _ui.update { it.copy(motorListo = true) }
        if (trabajoDiagnostico?.isActive != true) {
            trabajoDiagnostico = viewModelScope.launch {
                while (isActive) {
                    if (adquirido) {
                        val (lat, xr) = withContext(Dispatchers.IO) {
                            // Si el stream se cayó sin reabrirse, el gestor reintenta o pasa a ERROR.
                            gestor.vigilarStream()
                            motor.latenciaMs() to motor.xRuns()
                        }
                        _ui.update { it.copy(latenciaMs = lat.takeIf { v -> v >= 0 }, xRuns = xr.takeIf { v -> v >= 0 }) }
                    }
                    delay(1_000)
                }
            }
        }
    }

    /** Pausa todo lo que esté corriendo (al salir de la pantalla o ir a segundo plano). */
    private fun detenerTodo() {
        cancelarOperacion()
        trabajoMonitorPractica?.cancel()
        // Antes de parar: el reloj aún da la posición en la que el usuario soltó.
        if (_ui.value.grabacion == EstadoGrabacion.GRABANDO) {
            grabador.cerrarTodas(tickActual().toLong())
        }
        pararMotorTransporte()
        motor.todasLasNotasOff()
        // El motor es compartido y conserva el sustain por canal: si no se suelta aquí, la
        // próxima pantalla sonaría sostenida con el chip "Pedal" apagado.
        motor.pedal(false)
        _ui.update { s ->
            s.copy(
                reproduciendo = false,
                pedalAbajo = false,
                grabacion = if (s.grabacion == EstadoGrabacion.GRABANDO) EstadoGrabacion.PAUSADA else s.grabacion,
                notasGrabadas = grabador.cantidad,
                practica = if (s.practica.estado == EstadoPractica.EN_CURSO) {
                    s.practica.copy(estado = EstadoPractica.PAUSADA)
                } else {
                    s.practica
                },
            )
        }
    }

    private fun cargarCancion() {
        viewModelScope.launch {
            val cancion = runCatching { repositorio.obtener(cancionId) }.getOrNull()
            if (cancion == null) {
                _ui.update { it.copy(cargandoCancion = false) }
                _eventos.send(EventoPiano.Mensaje("No se encontró la canción"))
                return@launch
            }
            if (modo == ModoPiano.GRABAR) {
                // Overdub: las notas nuevas se cuantizan con la rejilla de la canción.
                config = config.copy(
                    bpm = cancion.bpm.roundToInt(),
                    compas = cancion.compas,
                    cuantizacion = cancion.cuantizacion,
                    cuantizacionHumana = cancion.cuantizacionHumana,
                )
            }
            _ui.update {
                it.copy(
                    cargandoCancion = false,
                    cancion = cancion,
                    bpm = cancion.bpm,
                    compas = cancion.compas,
                    tituloSugerido = cancion.titulo,
                    totalCompases = SeleccionPractica.totalCompases(cancion),
                )
            }
            if (modo == ModoPiano.PRACTICA) aplicarSeleccion()
            // Para que el inicio ofrezca seguir con esta canción (editarla no cuenta como "tocarla").
            if (modo == ModoPiano.REPRODUCIR || modo == ModoPiano.PRACTICA) actividad.registrarApertura(cancion.id, modo)
            actualizarPracticaUi()
            // Se envía el mismo valor que guarda la UI: prepararMotor también lo lee de ahí.
            if (adquirido) motor.setTempo(_ui.value.bpm)
            // Eventos ordenados listos antes del primer Play (en práctica se prepara el acompañamiento).
            if (modo != ModoPiano.PRACTICA) reproducirCancion.preparar(cancion)
        }
    }

    // =================================================================================
    // Teclado (llamado desde el listener de PianoSurfaceView, hilo principal)
    // =================================================================================

    fun noteOn(nota: Int, mano: Mano, velocidad: Int) {
        motor.noteOn(nota, velocidad, mano) // primero el sonido: es lo más urgente
        // Solo con el reloj ya en marcha: en los ms de arranque su tick aún sería el viejo.
        if (grabandoDeVerdad()) {
            grabador.notaOn(nota, velocidad, mano, tickPulsacion().toLong())
            _ui.update { it.copy(notasGrabadas = grabador.cantidad) }
        }
        if (modo == ModoPiano.PRACTICA) evaluarPulsacion(nota)
    }

    fun noteOff(nota: Int, mano: Mano) {
        motor.noteOff(nota, mano)
        if (grabandoDeVerdad()) {
            grabador.notaOff(nota, tickPulsacion().toLong())
        }
    }

    fun setPedal(abajo: Boolean) {
        motor.pedal(abajo)
        _ui.update { it.copy(pedalAbajo = abajo) }
    }

    fun cambiarZoom(delta: Int) {
        // El zoom se recuerda entre sesiones; la UI lo recibe por el flujo de ajustes.
        ajustesRepo.actualizar { it.copy(teclasVisibles = it.teclasVisibles + delta) }
    }

    /** Zoom elegido con un pellizco sobre la cascada; se guarda como el del menú. */
    fun fijarZoom(teclas: Int) = ajustesRepo.actualizar { it.copy(teclasVisibles = teclas) }

    fun cambiarBpm(delta: Int) {
        // Un cambio manual deja el tempo en un entero (92,3 + 5 → 97).
        val bpm = (_ui.value.bpm.roundToInt() + delta).coerceIn(40, 240).toDouble()
        motor.setTempo(bpm)
        _ui.update { it.copy(bpm = bpm) }
    }

    /** Usado por la cascada (hilo de render) en cada frame. AudioBridge.leerReloj es thread-safe. */
    fun leerReloj(destino: RelojSnapshot) = motor.leerReloj(destino)

    // =================================================================================
    // Modo REPRODUCIR
    // =================================================================================

    fun alternarReproduccion() {
        if (_ui.value.reproduciendo) pausarReproduccion() else iniciarReproduccion()
    }

    private fun iniciarReproduccion() {
        val cancion = _ui.value.cancion ?: return
        if (!_ui.value.motorListo) return
        // La UI refleja la intención al instante: un segundo toque ya es "Pausa".
        _ui.update { it.copy(reproduciendo = true) }
        val op = nuevaOperacion()
        op.launch {
            // Un salto aún sin confirmar (barra de progreso) manda sobre el reloj.
            destinoSalto?.let { esperarSalto(it) }
            var desde = tickActual()
            if (desde >= cancion.duracionTicks) {
                pedirSalto(0.0)
                esperarSalto(0.0)
                desde = 0.0
            }
            reproducirDesde(cancion, desde)
        }
    }

    private fun pausarReproduccion() {
        cancelarOperacion()
        pararMotorTransporte() // el motor vacía su cola y apaga las notas de la canción
        _ui.update { it.copy(reproduciendo = false) }
    }

    fun volverAlInicio() = irATick(0.0)

    /** Salta a [tick] (barra de progreso). Si estaba sonando, sigue sonando desde ahí. */
    fun irATick(tick: Double) {
        val cancion = _ui.value.cancion
        val destino = tick.coerceIn(0.0, (cancion?.duracionTicks ?: 0L).toDouble())
        // Intención en este instante; si después se pausa, la pausa cancela esta operación.
        val sonando = _ui.value.reproduciendo
        val op = nuevaOperacion()
        pararMotorTransporte()
        pedirSalto(destino)
        op.launch {
            esperarSalto(destino)
            if (sonando && cancion != null) reproducirDesde(cancion, destino)
        }
    }

    /**
     * Dentro de la operación actual: programa [cancion] desde [desde], arranca el reloj y,
     * cuando termina de sonar, vuelve al principio.
     */
    private fun CoroutineScope.reproducirDesde(cancion: Cancion, desde: Double) {
        launch { reproducirCancion(cancion, desde) }
        arrancarMotorTransporte()
        launch {
            while (tickActual() < cancion.duracionTicks + cancion.compas.ticksPorTiempo) delay(50)
            pararMotorTransporte()
            _ui.update { it.copy(reproduciendo = false) }
            pedirSalto(0.0)
        }
    }

    // =================================================================================
    // Modo GRABAR (y "Grabar rápido" en LIBRE)
    // =================================================================================

    /** Botón flotante de LIBRE: prepara una toma y empieza a grabar al instante. */
    fun grabarRapido() {
        if (_ui.value.grabacion != EstadoGrabacion.INACTIVA) return
        config = config.copy(bpm = _ui.value.bpm.roundToInt())
        _ui.update { it.copy(grabacion = EstadoGrabacion.LISTA) }
        grabar()
    }

    fun grabar() {
        val s = _ui.value
        if (!s.motorListo) return
        when (s.grabacion) {
            EstadoGrabacion.LISTA -> {
                grabador.limpiar()
                val op = nuevaOperacion()
                pararMotorTransporte()
                pedirSalto(0.0)
                op.launch {
                    esperarSalto(0.0)
                    arrancarToma(0.0)
                }
            }
            EstadoGrabacion.PAUSADA -> nuevaOperacion().arrancarToma(posicionActual())
            else -> return
        }
        // GRABANDO desde ya: Pausa y Detener funcionan también durante el arranque.
        _ui.update { it.copy(grabacion = EstadoGrabacion.GRABANDO, notasGrabadas = grabador.cantidad) }
    }

    /** Acompañamiento (canción existente en overdub + metrónomo) y arranque del reloj. */
    private fun CoroutineScope.arrancarToma(desde: Double) {
        _ui.value.cancion?.let { c -> launch { reproducirCancion(c, desde) } }
        if (_ui.value.metronomo) lanzarMetronomo(desde)
        arrancarMotorTransporte()
    }

    private fun CoroutineScope.lanzarMetronomo(desde: Double) {
        trabajoMetronomo?.cancel()
        trabajoMetronomo = launch { metronomoUseCase(_ui.value.compas, desde) }
    }

    fun alternarMetronomo() {
        val activo = !_ui.value.metronomo
        _ui.update { it.copy(metronomo = activo) }
        // Si la toma aún está arrancando, arrancarToma leerá el valor nuevo.
        if (!grabandoDeVerdad()) return
        if (activo) operacion?.lanzarMetronomo(tickActual()) else trabajoMetronomo?.cancel()
    }

    fun pausarGrabacion() {
        if (_ui.value.grabacion != EstadoGrabacion.GRABANDO) return
        cancelarOperacion()
        grabador.cerrarTodas(tickActual().toLong())
        pararMotorTransporte()
        _ui.update { it.copy(grabacion = EstadoGrabacion.PAUSADA, notasGrabadas = grabador.cantidad) }
    }

    fun detenerGrabacion() {
        pausarGrabacion()
        if (_ui.value.grabacion != EstadoGrabacion.PAUSADA) return
        if (grabador.estaVacio) {
            terminarToma()
            viewModelScope.launch { _eventos.send(EventoPiano.Mensaje("No se grabó ninguna nota")) }
            return
        }
        val sugerido = _ui.value.cancion?.titulo
            ?: "Mi canción " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("d/M HH:mm"))
        _ui.update { it.copy(dialogoGuardar = true, tituloSugerido = sugerido) }
    }

    /** Cierra el diálogo sin guardar ni descartar: la toma sigue en pausa. */
    fun seguirGrabando() {
        _ui.update { it.copy(dialogoGuardar = false) }
    }

    fun descartarGrabacion() {
        grabador.limpiar()
        terminarToma()
    }

    fun guardar(titulo: String) {
        if (_ui.value.guardando) return
        val notas = grabador.notas()
        val existente = _ui.value.cancion?.id?.takeIf { modo == ModoPiano.GRABAR && it > 0 }
        _ui.update { it.copy(guardando = true) }
        viewModelScope.launch {
            runCatching {
                guardarGrabacion(
                    titulo = titulo,
                    notasGrabadas = notas,
                    config = config.copy(bpm = _ui.value.bpm.roundToInt(), compas = _ui.value.compas),
                    cancionExistenteId = existente,
                )
            }.onSuccess { id ->
                grabador.limpiar()
                terminarToma()
                if (existente != null) cargarCancionTrasOverdub(id)
                _eventos.send(EventoPiano.Guardada(id, volverABiblioteca = modo == ModoPiano.GRABAR))
            }.onFailure { e ->
                _ui.update { it.copy(guardando = false) }
                _eventos.send(EventoPiano.Mensaje("No se pudo guardar: ${e.message}"))
            }
        }
    }

    private fun terminarToma() {
        cancelarOperacion()
        pararMotorTransporte()
        pedirSalto(0.0)
        _ui.update {
            it.copy(
                dialogoGuardar = false,
                guardando = false,
                notasGrabadas = 0,
                grabacion = if (modo == ModoPiano.LIBRE) EstadoGrabacion.INACTIVA else EstadoGrabacion.LISTA,
            )
        }
    }

    private suspend fun cargarCancionTrasOverdub(id: Long) {
        repositorio.obtener(id)?.let { c -> _ui.update { it.copy(cancion = c, tituloSugerido = c.titulo) } }
    }

    // =================================================================================
    // Modo PRACTICA (modo espera)
    // =================================================================================

    fun alternarPractica() {
        val ev = evaluador ?: return
        if (!_ui.value.motorListo) return
        when (_ui.value.practica.estado) {
            EstadoPractica.LISTA, EstadoPractica.TERMINADA -> arrancarPractica(ev)
            EstadoPractica.EN_CURSO, EstadoPractica.ESPERANDO -> {
                pararTransporte()
                actualizarPracticaUi(EstadoPractica.PAUSADA)
            }
            EstadoPractica.PAUSADA -> {
                if (ev.esperando) {
                    actualizarPracticaUi(EstadoPractica.ESPERANDO)
                } else {
                    // posicionActual: si se pausó durante el arranque, el Seek inicial manda.
                    reanudarTransporte(posicionActual())
                    actualizarPracticaUi(EstadoPractica.EN_CURSO)
                }
                if (trabajoMonitorPractica?.isActive != true) lanzarMonitorPractica()
            }
        }
    }

    /**
     * Empieza desde el principio del tramo. El estado pasa a EN_CURSO en el acto (un doble
     * toque ya es "Pausa" y cancela esta operación); el reloj arranca cuando el Seek se aplica.
     */
    private fun arrancarPractica(ev: EvaluadorPractica) {
        trabajoMonitorPractica?.cancel()
        ev.reiniciar()
        _ui.update { it.copy(practica = it.practica.copy(vuelta = 1)) }
        val desde = tickInicioPractica()
        val op = nuevaOperacion()
        pararMotorTransporte()
        pedirSalto(desde)
        actualizarPracticaUi(EstadoPractica.EN_CURSO, limpiarError = true)
        op.launch {
            esperarSalto(desde)
            reanudarTransporte(desde, op)
            lanzarMonitorPractica()
        }
    }

    fun reiniciarPractica() {
        trabajoMonitorPractica?.cancel()
        evaluador?.reiniciar()
        pararTransporte()
        pedirSalto(tickInicioPractica())
        _ui.update { it.copy(practica = it.practica.copy(vuelta = 1)) }
        actualizarPracticaUi(EstadoPractica.LISTA, limpiarError = true)
    }

    /** Mano y tramo a practicar. Cambiarlos reinicia la práctica. */
    fun cambiarSeleccionPractica(nueva: SeleccionPractica) {
        val total = _ui.value.totalCompases
        val tramo = nueva.tramo?.let { t ->
            val desde = t.first.coerceIn(1, total)
            desde..t.last.coerceIn(desde, total)
        }
        seleccion = nueva.copy(tramo = tramo)
        aplicarSeleccion()
        reiniciarPractica()
    }

    private fun aplicarSeleccion() {
        val cancion = _ui.value.cancion ?: return
        evaluador = EvaluadorPractica(seleccion.notasAEvaluar(cancion), toleranciaTicks = TOLERANCIA_PRACTICA)
        cancionAcompanamiento = seleccion.notasDeAcompanamiento(cancion)
            .takeIf { it.isNotEmpty() }
            ?.let { cancion.copy(notas = it) }
        cancionAcompanamiento?.let { c -> viewModelScope.launch { reproducirCancion.preparar(c) } }
        _ui.update { it.copy(seleccionPractica = seleccion) }
    }

    private fun tickInicioPractica(): Double = _ui.value.cancion?.let { seleccion.tickInicio(it).toDouble() } ?: 0.0

    /**
     * Arranca el reloj y, si se practica una sola mano, el acompañamiento desde [desde].
     * Sin [op] abre una operación nueva (reanudar tras una espera o una pausa).
     */
    private fun reanudarTransporte(desde: Double, op: CoroutineScope = nuevaOperacion()) {
        cancionAcompanamiento?.let { c -> op.launch { reproducirCancion(c, desde) } }
        arrancarMotorTransporte()
    }

    /** Cancela la operación en curso (acompañamiento incluido) y para el reloj. */
    private fun pararTransporte() {
        cancelarOperacion()
        pararMotorTransporte()
    }

    /** Fin del tramo en bucle: vuelve al principio (con su compás de preparación) sin borrar el marcador. */
    private suspend fun nuevaVuelta() {
        evaluador?.reiniciarVuelta()
        val op = nuevaOperacion()
        pararMotorTransporte()
        _ui.update { it.copy(practica = it.practica.copy(vuelta = it.practica.vuelta + 1)) }
        val desde = tickInicioPractica()
        pedirSalto(desde)
        esperarSalto(desde)
        // El monitor no pertenece a la operación: si mientras tanto llegó otro comando, op ya no vale.
        if (op.isActive && _ui.value.practica.estado == EstadoPractica.EN_CURSO) reanudarTransporte(desde, op)
    }

    fun cerrarResultadoPractica() {
        reiniciarPractica()
    }

    /** Vigila el reloj: si una nota llega a la línea y el usuario no la tocó, se para. */
    private fun lanzarMonitorPractica() {
        trabajoMonitorPractica?.cancel()
        trabajoMonitorPractica = viewModelScope.launch {
            val fin = _ui.value.cancion?.let { seleccion.tickFin(it) } ?: 0L
            val enBucle = seleccion.tramo != null
            while (isActive) {
                val ev = evaluador ?: break
                if (_ui.value.practica.estado == EstadoPractica.EN_CURSO && transporteCorriendo) {
                    val tick = tickActual()
                    val grupo = ev.grupoActual
                    when {
                        grupo != null && ev.debePausar(tick) -> esperarEn(grupo.tick.toDouble())
                        // nuevaVuelta suspende hasta que el salto se aplica: no se repite en bucle.
                        ev.terminado && tick >= fin -> if (enBucle) nuevaVuelta() else terminarPractica()
                    }
                }
                delay(MONITOR_MS)
            }
        }
    }

    private fun evaluarPulsacion(nota: Int) {
        val ev = evaluador ?: return
        val estado = _ui.value.practica.estado
        if (estado != EstadoPractica.EN_CURSO && estado != EstadoPractica.ESPERANDO) return
        // EN_CURSO pero arrancando: el reloj aún no está en el tramo, el tick no valdría.
        if (estado == EstadoPractica.EN_CURSO && !transporteCorriendo) return

        val estabaEsperando = ev.esperando
        val grupo = ev.grupoActual
        val tick = tickPulsacion()
        val resultado = ev.alPulsar(nota, tick)
        when (resultado) {
            EvaluadorPractica.Resultado.ACIERTO, EvaluadorPractica.Resultado.GRUPO_COMPLETO ->
                _resultadosTecla.tryEmit(ResultadoTecla(nota, acierto = true))
            EvaluadorPractica.Resultado.ERROR -> _resultadosTecla.tryEmit(ResultadoTecla(nota, acierto = false))
            EvaluadorPractica.Resultado.IGNORADA -> Unit
        }
        when (resultado) {
            EvaluadorPractica.Resultado.GRUPO_COMPLETO -> {
                if (estabaEsperando) {
                    reanudarTransporte(posicionActual())
                    actualizarPracticaUi(EstadoPractica.EN_CURSO, limpiarError = true)
                } else {
                    actualizarPracticaUi(limpiarError = true)
                }
            }
            EvaluadorPractica.Resultado.ERROR -> {
                val notacion = _ui.value.ajustes.notacion
                val esperadas = grupo?.let { NombresNotas.lista(it.notas, notacion) } ?: ""
                val texto = "${NombresNotas.nombre(nota, notacion)} ✗  (esperaba $esperadas)"
                if (!estabaEsperando && grupo != null) {
                    // Parar donde está la música (o en el grupo, si ya llegó a la línea).
                    esperarEn(min(tickActual(), grupo.tick.toDouble()))
                }
                _ui.update { it.copy(practica = it.practica.copy(ultimoError = texto, idError = it.practica.idError + 1)) }
                actualizarPracticaUi()
            }
            EvaluadorPractica.Resultado.ACIERTO -> actualizarPracticaUi()
            EvaluadorPractica.Resultado.IGNORADA -> Unit
        }
    }

    /** TransportStop + Seek: la cascada queda congelada con las notas esperadas en la línea. */
    private fun esperarEn(tick: Double) {
        pararTransporte()
        pedirSalto(tick)
        evaluador?.marcarEspera()
        actualizarPracticaUi(EstadoPractica.ESPERANDO)
    }

    private fun terminarPractica() {
        trabajoMonitorPractica?.cancel()
        pararTransporte()
        evaluador?.let { ev ->
            // Solo cuenta la canción entera con ambas manos: un tramo o una mano sola no es la marca.
            if (seleccion == SeleccionPractica()) actividad.registrarResultado(cancionId, (ev.precision * 100).roundToInt())
        }
        actualizarPracticaUi(EstadoPractica.TERMINADA)
    }

    private fun actualizarPracticaUi(nuevoEstado: EstadoPractica? = null, limpiarError: Boolean = false) {
        val ev = evaluador ?: return
        _ui.update { s ->
            s.copy(
                practica = s.practica.copy(
                    estado = nuevoEstado ?: s.practica.estado,
                    pendientes = NombresNotas.lista(ev.pendientes, s.ajustes.notacion),
                    aciertos = ev.aciertos,
                    errores = ev.errores,
                    precision = (ev.precision * 100).roundToInt(),
                    racha = ev.racha,
                    mejorRacha = ev.mejorRacha,
                    ultimoError = if (limpiarError) null else s.practica.ultimoError,
                ),
            )
        }
    }

    // =================================================================================
    // Reloj
    // =================================================================================

    private fun tickActual(): Double {
        motor.leerReloj(reloj)
        return max(0.0, reloj.tickAt(System.nanoTime()))
    }

    /**
     * Tick musical de un toque que ocurre AHORA. El usuario toca siguiendo lo que OYE,
     * y lo que oye sale del altavoz con [PianoUiState.latenciaMs] de retraso: se
     * compensa para que la nota quede donde el usuario la sintió.
     */
    private fun tickPulsacion(): Double {
        motor.leerReloj(reloj)
        val latenciaNs = ((_ui.value.latenciaMs ?: 0.0) * 1_000_000.0).toLong()
        return max(0.0, reloj.tickAt(System.nanoTime() - latenciaNs))
    }

    /**
     * Pide un Seek al motor. Hasta que su reloj lo confirme, [posicionActual] devuelve
     * este destino en vez de la posición vieja.
     */
    private fun pedirSalto(tick: Double) {
        destinoSalto = tick
        motor.irATick(tick)
    }

    /**
     * Espera a que el hilo de audio aplique el Seek a [tick] (como mucho ~60 ms). Evita que el
     * código siguiente lea del reloj una posición vieja y programe notas fuera de sitio.
     * Es un punto de suspensión: si llega otro comando, la operación que espera se cancela aquí.
     */
    private suspend fun esperarSalto(tick: Double) {
        for (intento in 0 until 12) {
            delay(5)
            motor.leerReloj(reloj)
            val margen = max(PPQ / 8.0, reloj.ticksPerSecond * 0.1)
            if (abs(reloj.tick - tick) <= margen) break
        }
        if (destinoSalto == tick) destinoSalto = null
    }

    /** Posición musical que manda ahora: un Seek pendiente o, si no hay, el reloj. */
    private fun posicionActual(): Double = destinoSalto ?: tickActual()

    // =================================================================================
    // Operación de transporte
    // =================================================================================

    /** Cancela la operación anterior (con todo lo que lanzó) y abre una nueva. */
    private fun nuevaOperacion(): CoroutineScope {
        cancelarOperacion()
        val padre = viewModelScope.coroutineContext[Job]
        return CoroutineScope(viewModelScope.coroutineContext + Job(padre)).also { operacion = it }
    }

    private fun cancelarOperacion() {
        operacion?.cancel()
        operacion = null
        trabajoMetronomo = null
    }

    private fun arrancarMotorTransporte() {
        gestor.asegurarFoco()
        motor.transporteIniciar()
        transporteCorriendo = true
        // A partir de aquí la posición la lleva el reloj del motor (el Seek, si lo había, va antes en su cola).
        destinoSalto = null
    }

    private fun pararMotorTransporte() {
        motor.transporteDetener()
        transporteCorriendo = false
    }

    /** Grabando y con el reloj ya en marcha: solo entonces un toque tiene un tick fiable. */
    private fun grabandoDeVerdad() = transporteCorriendo && _ui.value.grabacion == EstadoGrabacion.GRABANDO

    private companion object {
        /** Margen para aceptar una nota antes/después de su tiempo en práctica: una semicorchea. */
        const val TOLERANCIA_PRACTICA = PPQ / 4L
        const val MONITOR_MS = 10L
    }
}
