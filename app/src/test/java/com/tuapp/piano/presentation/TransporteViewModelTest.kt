package com.tuapp.piano.presentation

import androidx.lifecycle.SavedStateHandle
import com.tuapp.piano.domain.audio.GestorMotorAudio
import com.tuapp.piano.domain.model.Actividad
import com.tuapp.piano.domain.model.Ajustes
import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.ModoPiano
import com.tuapp.piano.domain.model.Nota
import com.tuapp.piano.domain.model.RelojSnapshot
import com.tuapp.piano.domain.model.ResumenCancion
import com.tuapp.piano.domain.repository.ActividadRepository
import com.tuapp.piano.domain.repository.AjustesRepository
import com.tuapp.piano.domain.repository.AudioEngineRepository
import com.tuapp.piano.domain.repository.CancionRepository
import com.tuapp.piano.domain.repository.FocoAudioRepository
import com.tuapp.piano.domain.usecase.GuardarGrabacionUseCase
import com.tuapp.piano.domain.usecase.MetronomoUseCase
import com.tuapp.piano.domain.usecase.ReproducirCancionUseCase
import com.tuapp.piano.presentation.navigation.Rutas
import com.tuapp.piano.presentation.viewmodel.EstadoGrabacion
import com.tuapp.piano.presentation.viewmodel.EstadoPractica
import com.tuapp.piano.presentation.viewmodel.PianoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Collections

/**
 * Motor falso: aplica cada comando al instante (como si el hilo de audio respondiera en el
 * acto) y anota la secuencia. El reloj no avanza solo, así el estado final solo depende de
 * los comandos.
 */
private class MotorFalso : AudioEngineRepository {
    val registro: MutableList<String> = Collections.synchronizedList(ArrayList())
    @Volatile var tick = 0.0
    @Volatile var corriendo = false

    override suspend fun cargarSoundFont() = Result.success("falso.sf2")
    @Volatile var abierto = true
    @Volatile var puedeAbrir = true
    override fun iniciar(): Boolean { abierto = puedeAbrir; return puedeAbrir }
    override fun detener() = Unit
    override fun noteOn(nota: Int, velocidad: Int, mano: Mano) = true
    override fun noteOff(nota: Int, mano: Mano) = true
    override fun noteOnEn(tick: Double, nota: Int, velocidad: Int, mano: Mano) = registro.add("prog $tick")
    override fun noteOffEn(tick: Double, nota: Int, mano: Mano) = registro.add("progoff $tick")
    override fun todasLasNotasOff() = registro.add("todasoff")
    override fun pedal(abajo: Boolean, mano: Mano?) = registro.add("pedal $abajo")
    override fun setTempo(bpm: Double) = true
    override fun transporteIniciar(): Boolean { corriendo = true; return registro.add("start") }
    override fun transporteDetener(): Boolean { corriendo = false; return registro.add("stop") }
    override fun irATick(tick: Double): Boolean { this.tick = tick; return registro.add("seek $tick") }
    override fun leerReloj(destino: RelojSnapshot) {
        destino.tick = tick
        destino.running = corriendo
        destino.ticksPerSecond = 0.0
        destino.bpm = 100.0
        destino.hostTimeNs = System.nanoTime()
    }
    override fun latenciaMs() = 10.0
    override fun xRuns() = 0
    override fun streamAbierto() = abierto
    override fun metronomo(activo: Boolean, numerador: Int, denominador: Int) = registro.add("metro $activo")
    override fun reverberacion(preset: com.tuapp.piano.domain.model.Reverberacion) = true

    fun copia(): List<String> = synchronized(registro) { registro.toList() }
}

private val CANCION = Cancion(
    id = 1,
    titulo = "Prueba",
    bpm = 100.0,
    notas = List(40) { i -> Nota(60 + i % 12, i * 960L, 480L, Mano.DERECHA, 90) },
)

private class RepositorioFalso : CancionRepository {
    override fun observarResumenes(): Flow<List<ResumenCancion>> = flowOf(emptyList())
    override suspend fun obtener(id: Long) = CANCION.takeIf { id == it.id }
    override suspend fun crear(cancion: Cancion) = 2L
    override suspend fun agregarNotas(cancionId: Long, notas: List<Nota>, nuevoTitulo: String?) = Unit
    override suspend fun borrar(id: Long) = Unit
    override suspend fun marcarExportada(id: Long) = Unit
}

private class FocoFalso : FocoAudioRepository {
    var alPerder: (() -> Unit)? = null
    override fun solicitar(alPerder: () -> Unit): Boolean { this.alPerder = alPerder; return true }
    override fun abandonar() = Unit
}

private class AjustesFalsos : AjustesRepository {
    override val ajustes = MutableStateFlow(Ajustes())
    override fun actualizar(cambio: (Ajustes) -> Ajustes) { ajustes.value = cambio(ajustes.value) }
}

private class ActividadFalsa : ActividadRepository {
    override val actividad: StateFlow<Actividad> = MutableStateFlow(Actividad())
    override fun registrarApertura(cancionId: Long, modo: ModoPiano) = Unit
    override fun registrarResultado(cancionId: Long, precision: Int) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransporteViewModelTest {
    private val planificador = TestCoroutineScheduler()
    private val principal = StandardTestDispatcher(planificador)
    private val motor = MotorFalso()
    private val foco = FocoFalso()

    @Before fun antes() = Dispatchers.setMain(principal)

    @After fun despues() = Dispatchers.resetMain()

    private fun crear(modo: ModoPiano, cancionId: Long = 0L): PianoViewModel {
        val repo = RepositorioFalso()
        val vm = PianoViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Rutas.ARG_MODO to modo.name, Rutas.ARG_CANCION_ID to cancionId)),
            motor = motor,
            gestor = GestorMotorAudio(motor, foco),
            repositorio = repo,
            reproducirCancion = ReproducirCancionUseCase(motor),
            metronomoUseCase = MetronomoUseCase(motor),
            guardarGrabacion = GuardarGrabacionUseCase(repo),
            ajustesRepo = AjustesFalsos(),
            actividad = ActividadFalsa(),
        )
        vm.onStart()
        // Abrir el stream y preparar canciones pasa por hilos reales (IO / Default): se espera.
        repeat(600) {
            avanzar(5)
            if (vm.ui.value.motorListo && (cancionId == 0L || vm.ui.value.cancion != null)) {
                avanzar(100)
                return vm
            }
            Thread.sleep(2)
        }
        fail("El motor falso no quedó listo")
        error("inalcanzable")
    }

    /** Avanza el reloj virtual en pasos cortos, dejando volver a las corrutinas de otros hilos. */
    private fun avanzar(ms: Long) {
        var resto = ms
        while (resto > 0) {
            val paso = minOf(5L, resto)
            planificador.advanceTimeBy(paso)
            planificador.runCurrent()
            resto -= paso
        }
    }

    /** Comandos posteriores al último que empieza por [prefijo]. */
    private fun despuesDelUltimo(prefijo: String): List<String> {
        val r = motor.copia()
        val i = r.indexOfLast { it.startsWith(prefijo) }
        return if (i < 0) r else r.subList(i + 1, r.size)
    }

    @Test fun grabar_pausaAntesDeQueArranque_noArrancaNada() {
        val vm = crear(ModoPiano.GRABAR)
        motor.registro.clear()
        vm.grabar()
        assertEquals(EstadoGrabacion.GRABANDO, vm.ui.value.grabacion) // la UI refleja la intención al instante
        vm.pausarGrabacion()
        avanzar(300)
        assertEquals(EstadoGrabacion.PAUSADA, vm.ui.value.grabacion)
        assertFalse("No debe arrancar el reloj: ${motor.copia()}", "start" in motor.copia())
        assertFalse("Ni encender el metrónomo", "metro true" in motor.copia())
    }

    @Test fun grabar_pausaMientrasEsperaElSeek_noArrancaNada() {
        val vm = crear(ModoPiano.GRABAR)
        motor.registro.clear()
        vm.grabar()
        avanzar(2) // ya dentro de la espera del Seek
        vm.pausarGrabacion()
        avanzar(300)
        assertEquals(EstadoGrabacion.PAUSADA, vm.ui.value.grabacion)
        assertFalse("start" in motor.copia())
        assertFalse(motor.corriendo)
    }

    @Test fun reproducir_dosSaltosSeguidos_soloSuenaElUltimo() {
        val vm = crear(ModoPiano.REPRODUCIR, cancionId = 1)
        vm.alternarReproduccion()
        avanzar(200)
        assertTrue(vm.ui.value.reproduciendo)
        motor.registro.clear()
        vm.irATick(2_000.0)
        vm.irATick(9_600.0)
        avanzar(300)
        val tras = despuesDelUltimo("seek")
        assertEquals("seek 9600.0", motor.copia().last { it.startsWith("seek") })
        assertEquals("Un único arranque tras el último salto: $tras", 1, tras.count { it == "start" })
        val programadas = motor.copia().filter { it.startsWith("prog ") }.map { it.removePrefix("prog ").toDouble() }
        assertTrue("Ninguna nota de la posición abandonada: $programadas", programadas.all { it >= 9_600.0 })
        assertTrue(vm.ui.value.reproduciendo)
    }

    @Test fun reproducir_segundoSaltoConElPrimeroYaSonando_noDejaNotasViejasTrasElSeek() {
        val vm = crear(ModoPiano.REPRODUCIR, cancionId = 1)
        vm.alternarReproduccion()
        avanzar(200)
        vm.irATick(2_000.0)
        avanzar(60) // el primer salto ya arrancó y programó sus notas
        vm.irATick(9_600.0)
        avanzar(300)
        val tras = despuesDelUltimo("seek")
        val viejas = tras.filter { it.startsWith("prog ") && it.removePrefix("prog ").toDouble() < 9_600.0 }
        assertTrue("Tras el último Seek no se programa nada anterior: $tras", viejas.isEmpty())
        assertEquals(1, tras.count { it == "start" })
    }

    @Test fun reproducir_pausaDuranteUnSalto_quedaPausado() {
        val vm = crear(ModoPiano.REPRODUCIR, cancionId = 1)
        vm.alternarReproduccion()
        avanzar(200)
        vm.irATick(5_000.0)
        vm.alternarReproduccion() // pausa
        avanzar(300)
        assertFalse(vm.ui.value.reproduciendo)
        assertFalse("start" in despuesDelUltimo("stop"))
        assertFalse(motor.corriendo)
    }

    @Test fun practica_dobleToqueEnPlay_quedaEnPausaSinArrancar() {
        val vm = crear(ModoPiano.PRACTICA, cancionId = 1)
        vm.alternarPractica()
        vm.alternarPractica()
        avanzar(300)
        assertEquals(EstadoPractica.PAUSADA, vm.ui.value.practica.estado)
        assertFalse("start" in despuesDelUltimo("stop"))
        assertFalse(motor.corriendo)
    }

    @Test fun practica_unToque_arrancaUnaSolaVez() {
        val vm = crear(ModoPiano.PRACTICA, cancionId = 1)
        motor.registro.clear()
        vm.alternarPractica()
        avanzar(300)
        assertTrue(vm.ui.value.practica.estado in setOf(EstadoPractica.EN_CURSO, EstadoPractica.ESPERANDO))
        assertEquals(1, motor.copia().count { it == "start" })
    }

    @Test fun salir_conElPedalPisado_loSueltaEnElMotor() {
        val vm = crear(ModoPiano.LIBRE)
        vm.setPedal(true)
        vm.onStop()
        assertEquals("pedal false", motor.copia().last { it.startsWith("pedal") })
        assertFalse(vm.ui.value.pedalAbajo)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FocoYStreamTest {
    private val planificador = TestCoroutineScheduler()
    private val motor = MotorFalso()
    private val foco = FocoFalso()

    @Before fun antes() = Dispatchers.setMain(StandardTestDispatcher(planificador))

    @After fun despues() = Dispatchers.resetMain()

    @Test fun perderElFoco_pausaLaReproduccion() {
        val repo = RepositorioFalso()
        val vm = PianoViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Rutas.ARG_MODO to ModoPiano.REPRODUCIR.name, Rutas.ARG_CANCION_ID to 1L)),
            motor = motor,
            gestor = GestorMotorAudio(motor, foco),
            repositorio = repo,
            reproducirCancion = ReproducirCancionUseCase(motor),
            metronomoUseCase = MetronomoUseCase(motor),
            guardarGrabacion = GuardarGrabacionUseCase(repo),
            ajustesRepo = AjustesFalsos(),
            actividad = ActividadFalsa(),
        )
        vm.onStart()
        repeat(600) {
            planificador.advanceTimeBy(5); planificador.runCurrent()
            if (vm.ui.value.motorListo && vm.ui.value.cancion != null) return@repeat
            Thread.sleep(2)
        }
        repeat(40) { planificador.advanceTimeBy(5); planificador.runCurrent() }
        vm.alternarReproduccion()
        repeat(40) { planificador.advanceTimeBy(5); planificador.runCurrent() }
        assertTrue(vm.ui.value.reproduciendo)
        foco.alPerder!!.invoke() // llamada entrante
        repeat(10) { planificador.advanceTimeBy(5); planificador.runCurrent() }
        assertFalse(vm.ui.value.reproduciendo)
        assertFalse(motor.corriendo)
    }

    @Test fun streamCaidoSinReapertura_pasaAError() = kotlinx.coroutines.runBlocking {
        val gestor = GestorMotorAudio(motor, foco)
        assertTrue(gestor.adquirir())
        motor.abierto = false
        motor.puedeAbrir = false
        gestor.vigilarStream()
        assertEquals(com.tuapp.piano.domain.model.EstadoMotor.ERROR, gestor.estado.value)
        motor.puedeAbrir = true
        gestor.vigilarStream()
        assertEquals(com.tuapp.piano.domain.model.EstadoMotor.SONANDO, gestor.estado.value)
    }
}
