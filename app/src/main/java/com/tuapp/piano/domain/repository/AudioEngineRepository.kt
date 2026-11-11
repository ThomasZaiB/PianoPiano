package com.tuapp.piano.domain.repository

import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.RelojSnapshot

/**
 * Contrato del motor de audio visto desde el dominio. La implementación (AudioBridge)
 * vive en la capa de datos y habla con C++; el dominio y los ViewModels no saben de JNI.
 */
interface AudioEngineRepository {
    /** Carga el primer .sf2 encontrado en assets. Devuelve su nombre. Hilo de E/S. */
    suspend fun cargarSoundFont(): Result<String>

    /** Abre el stream de audio. Bloqueante (ms): llamar fuera del hilo principal. */
    fun iniciar(): Boolean

    /** Cierra el stream. Bloqueante (ms): llamar fuera del hilo principal. */
    fun detener()

    // Eventos en tiempo real: baratos, se pueden llamar desde el hilo de UI.
    fun noteOn(nota: Int, velocidad: Int, mano: Mano): Boolean
    fun noteOff(nota: Int, mano: Mano): Boolean

    /** Programadas: el motor las dispara en la muestra exacta de ese tick (reproducción). */
    fun noteOnEn(tick: Double, nota: Int, velocidad: Int, mano: Mano): Boolean
    fun noteOffEn(tick: Double, nota: Int, mano: Mano): Boolean
    fun todasLasNotasOff(): Boolean
    fun pedal(abajo: Boolean, mano: Mano? = null): Boolean

    // Transporte / reloj maestro
    fun setTempo(bpm: Double): Boolean
    fun transporteIniciar(): Boolean
    fun transporteDetener(): Boolean
    fun irATick(tick: Double): Boolean
    fun leerReloj(destino: RelojSnapshot)

    // Diagnóstico
    fun latenciaMs(): Double
    fun xRuns(): Int
}
