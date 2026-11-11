package com.tuapp.piano.domain.model

/** Resolución musical del proyecto (debe coincidir con kPPQ en MasterClock.h). */
const val PPQ = 960

/**
 * Foto del reloj maestro del motor C++. Es mutable a propósito: la UI reutiliza
 * una sola instancia por frame para no generar basura a 60 FPS.
 */
class RelojSnapshot {
    var framePosition: Long = 0
    var hostTimeNs: Long = 0      // misma base que System.nanoTime()
    var tick: Double = 0.0
    var ticksPerSecond: Double = 0.0
    var bpm: Double = 120.0
    var sampleRate: Int = 0
    var running: Boolean = false

    /** Posición musical extrapolada a un instante dado (p. ej. el vsync del frame). */
    fun tickAt(nanoTime: Long): Double =
        if (!running) tick else tick + (nanoTime - hostTimeNs) * ticksPerSecond / 1_000_000_000.0
}
