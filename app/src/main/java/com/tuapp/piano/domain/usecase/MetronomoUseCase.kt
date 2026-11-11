package com.tuapp.piano.domain.usecase

import com.tuapp.piano.domain.model.Compas
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.PPQ
import com.tuapp.piano.domain.model.RelojSnapshot
import com.tuapp.piano.domain.repository.AudioEngineRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.max

/**
 * Metrónomo provisional construido SOLO con la API existente del motor:
 * programa clics (notas agudas y cortas) con noteOnEn/noteOffEn, igual que el
 * reproductor de canciones. Suenan con precisión de muestra porque los dispara C++.
 *
 * Cuando exista el metrónomo nativo en C++ (Paso 4 original), basta con
 * reemplazar este caso de uso; el ViewModel no cambia.
 *
 * Se detiene cancelando la corrutina. Al pausar, TransportStop vacía la cola del motor.
 */
class MetronomoUseCase @Inject constructor(
    private val motor: AudioEngineRepository,
) {
    suspend operator fun invoke(compas: Compas, desdeTick: Double) {
        val ticksPorTiempo = compas.ticksPorTiempo.toLong()
        var siguiente = ceil(max(0.0, desdeTick) / ticksPorTiempo).toLong() * ticksPorTiempo
        val reloj = RelojSnapshot()

        while (true) {
            coroutineContext.ensureActive()
            motor.leerReloj(reloj)
            val ahora = if (!reloj.running) desdeTick else max(reloj.tickAt(System.nanoTime()), desdeTick)
            val horizonte = ahora + reloj.bpm * PPQ / 60.0 * ANTELACION_S

            while (siguiente < horizonte) {
                val esPrimero = (siguiente / ticksPorTiempo) % compas.numerador == 0L
                val nota = if (esPrimero) NOTA_ACENTO else NOTA_TIEMPO
                val velocidad = if (esPrimero) 110 else 70
                if (!motor.noteOnEn(siguiente.toDouble(), nota, velocidad, Mano.DERECHA)) break
                motor.noteOffEn((siguiente + DURACION_CLIC).toDouble(), nota, Mano.DERECHA)
                siguiente += ticksPorTiempo
            }
            delay(PERIODO_MS)
        }
    }

    private companion object {
        const val NOTA_ACENTO = 108 // Do8: primer tiempo del compás
        const val NOTA_TIEMPO = 103 // Sol7: resto de tiempos
        const val DURACION_CLIC = 60L
        const val ANTELACION_S = 0.25
        const val PERIODO_MS = 25L
    }
}
