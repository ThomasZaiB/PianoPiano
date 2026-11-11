package com.tuapp.piano.domain.usecase

import com.tuapp.piano.domain.model.Compas
import com.tuapp.piano.domain.repository.AudioEngineRepository
import kotlinx.coroutines.awaitCancellation
import javax.inject.Inject

/**
 * Metrónomo nativo: el clic lo sintetiza y lo dispara el propio hilo de audio en la
 * muestra exacta de cada tiempo (seno corto, más agudo y fuerte en el primero del compás).
 * Ya no ocupa teclas del piano ni suena a piano.
 *
 * La forma de uso no cambia: suena mientras la corrutina vive y calla al cancelarla.
 * Con el transporte parado el motor no da clics, así que pausar basta para silenciarlo.
 */
class MetronomoUseCase @Inject constructor(
    private val motor: AudioEngineRepository,
) {
    /**
     * Cada invocación toma un turno. Al cancelar, el `finally` de la anterior puede
     * ejecutarse DESPUÉS de que la siguiente ya encendiera el metrónomo: solo apaga si
     * sigue siendo la última. Se usa desde un solo hilo (el principal).
     */
    private var turno = 0

    /** [desdeTick] se conserva por compatibilidad: el motor calcula los tiempos con su propio reloj. */
    @Suppress("UNUSED_PARAMETER")
    suspend operator fun invoke(compas: Compas, desdeTick: Double) {
        val miTurno = ++turno
        motor.metronomo(true, compas.numerador, compas.denominador)
        try {
            awaitCancellation()
        } finally {
            if (turno == miTurno) motor.metronomo(false, compas.numerador, compas.denominador)
        }
    }
}
