package com.tuapp.piano.domain.usecase

import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.PPQ
import com.tuapp.piano.domain.model.RelojSnapshot
import com.tuapp.piano.domain.repository.AudioEngineRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import javax.inject.Inject

/**
 * Reproduce una canción programando sus notas en el motor C++ con antelación.
 *
 * Kotlin NO decide cuándo suena cada nota (su planificación tiene jitter de varios ms):
 * solo envía, con [ANTELACION_S] de margen, "Note On en el tick X". El hilo de audio
 * las dispara en la muestra exacta. Así el ritmo es perfecto aunque la UI vaya lenta.
 *
 * Se cancela cancelando la corrutina; el motor vacía su cola al pausar o saltar.
 */
class ReproducirCancionUseCase @Inject constructor(
    private val motor: AudioEngineRepository,
) {
    private class Evento(val tick: Long, val encender: Boolean, val nota: Int, val mano: Mano, val velocidad: Int)

    /** Termina cuando todos los eventos se han enviado (no cuando terminan de sonar). */
    suspend operator fun invoke(cancion: Cancion, desdeTick: Double) {
        val eventos = cancion.notas
            .flatMap {
                listOf(
                    Evento(it.tickInicio, true, it.nota, it.mano, it.velocidad),
                    Evento(it.tickFin, false, it.nota, it.mano, 0),
                )
            }
            .sortedWith(compareBy<Evento> { it.tick }.thenBy { it.encender }) // Off antes que On

        // Las notas que empezaron antes del punto de partida no se re-atacan.
        var i = eventos.indexOfFirst { it.tick >= desdeTick && (it.encender || it.tick > desdeTick) }
        if (i < 0) return

        val reloj = RelojSnapshot()
        while (i < eventos.size) {
            coroutineContext.ensureActive()
            motor.leerReloj(reloj)
            // Con el transporte parado (justo antes de TransportStart, o tras un Seek aún no
            // procesado) el reloj puede mostrar una posición vieja: se usa el punto de partida.
            val ahora = if (!reloj.running) desdeTick else max(reloj.tickAt(System.nanoTime()), desdeTick)
            val ticksPorSegundo = max(reloj.ticksPerSecond, reloj.bpm * PPQ / 60.0)
            val horizonte = ahora + ticksPorSegundo * ANTELACION_S

            while (i < eventos.size && eventos[i].tick < horizonte) {
                val e = eventos[i]
                val enviado = if (e.encender) {
                    motor.noteOnEn(e.tick.toDouble(), e.nota, e.velocidad, e.mano)
                } else {
                    motor.noteOffEn(e.tick.toDouble(), e.nota, e.mano)
                }
                if (!enviado) break // ring buffer lleno: reintentar en la próxima vuelta
                i++
            }
            delay(PERIODO_MS)
        }
    }

    private companion object {
        /** Margen de envío: cubre de sobra el jitter del planificador de Kotlin. */
        const val ANTELACION_S = 0.25
        const val PERIODO_MS = 25L
    }
}
