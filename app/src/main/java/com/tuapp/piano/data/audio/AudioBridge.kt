package com.tuapp.piano.data.audio

import android.content.Context
import android.content.res.AssetManager
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.Reverberacion
import com.tuapp.piano.domain.model.RelojSnapshot
import com.tuapp.piano.domain.repository.AudioEngineRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puente JNI hacia el motor C++ (native-lib.cpp).
 * Los eventos de nota viajan a un ring buffer lock-free que el hilo de audio drena
 * en cada callback; aquí nunca se espera al hilo de audio.
 */
@Singleton
class AudioBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioEngineRepository {

    // Arrays reutilizados para leer el reloj sin crear objetos en cada frame.
    private val relojLongs = LongArray(2)
    private val relojDoubles = DoubleArray(5)

    val version: String get() = nativeVersion()

    override suspend fun cargarSoundFont(): Result<String> = withContext(Dispatchers.IO) {
        // Prioriza el .sf2 que pongas tú; "demo-*.sf2" (el incluido) es solo el respaldo.
        val archivo = context.assets.list("")
            ?.filter { it.endsWith(".sf2", ignoreCase = true) }
            ?.sortedBy { it.startsWith("demo-") }
            ?.firstOrNull()
            ?: return@withContext Result.failure(
                IllegalStateException("No hay ningún .sf2 en app/src/main/assets/")
            )
        if (nativeLoadSoundFont(context.assets, archivo)) {
            Result.success(archivo)
        } else {
            Result.failure(IllegalStateException("No se pudo cargar $archivo (¿.sf2 válido?)"))
        }
    }

    override fun iniciar(): Boolean = nativeStart()
    override fun detener() = nativeStop()

    // valor < 0 = inmediata; valor >= 0 = tick programado (ver EngineEvent.h)
    override fun noteOn(nota: Int, velocidad: Int, mano: Mano) =
        post(EV_NOTE_ON, mano.canal, nota, velocidad, valor = INMEDIATO)

    override fun noteOff(nota: Int, mano: Mano) = post(EV_NOTE_OFF, mano.canal, nota, valor = INMEDIATO)

    override fun noteOnEn(tick: Double, nota: Int, velocidad: Int, mano: Mano) =
        post(EV_NOTE_ON, mano.canal, nota, velocidad, valor = tick.coerceAtLeast(0.0))

    override fun noteOffEn(tick: Double, nota: Int, mano: Mano) =
        post(EV_NOTE_OFF, mano.canal, nota, valor = tick.coerceAtLeast(0.0))

    override fun todasLasNotasOff() = post(EV_ALL_NOTES_OFF)

    override fun pedal(abajo: Boolean, mano: Mano?): Boolean {
        val valor = if (abajo) 1.0 else 0.0
        return if (mano != null) {
            post(EV_SUSTAIN, mano.canal, valor = valor)
        } else {
            Mano.entries.map { post(EV_SUSTAIN, it.canal, valor = valor) }.all { it }
        }
    }

    override fun setTempo(bpm: Double) = post(EV_SET_TEMPO, valor = bpm)
    override fun transporteIniciar() = post(EV_TRANSPORT_START)
    override fun transporteDetener() = post(EV_TRANSPORT_STOP)
    override fun irATick(tick: Double) = post(EV_SEEK, valor = tick)

    override fun metronomo(activo: Boolean, numerador: Int, denominador: Int) =
        post(EV_METRONOMO, nota = numerador, velocidad = denominador, valor = if (activo) 1.0 else 0.0)

    override fun reverberacion(preset: Reverberacion) = post(EV_REVERB, nota = preset.codigo)

    /** Sincronizado solo para proteger los arrays reutilizados (contención ~nula). */
    @Synchronized
    override fun leerReloj(destino: RelojSnapshot) {
        nativeReadClock(relojLongs, relojDoubles)
        destino.framePosition = relojLongs[0]
        destino.hostTimeNs = relojLongs[1]
        destino.tick = relojDoubles[0]
        destino.ticksPerSecond = relojDoubles[1]
        destino.bpm = relojDoubles[2]
        destino.sampleRate = relojDoubles[3].toInt()
        destino.running = relojDoubles[4] != 0.0
    }

    override fun latenciaMs(): Double = nativeGetLatencyMillis()
    override fun xRuns(): Int = nativeGetXRunCount()
    override fun streamAbierto(): Boolean = nativeIsStreamOpen()

    private fun post(tipo: Int, canal: Int = 0, nota: Int = 0, velocidad: Int = 1, valor: Double = 0.0) =
        nativePostEvent(tipo, canal, nota, velocidad, valor)

    // ---- JNI (nombres ligados a Java_com_tuapp_piano_data_audio_AudioBridge_*) ----
    private external fun nativeVersion(): String
    private external fun nativeLoadSoundFont(assetManager: AssetManager, path: String): Boolean
    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativePostEvent(type: Int, channel: Int, key: Int, velocity: Int, value: Double): Boolean
    private external fun nativeReadClock(longs: LongArray, doubles: DoubleArray)
    private external fun nativeGetLatencyMillis(): Double
    private external fun nativeGetXRunCount(): Int
    private external fun nativeIsStreamOpen(): Boolean

    companion object {
        // Deben coincidir con enum class EventType en EngineEvent.h
        private const val EV_NOTE_ON = 0
        private const val EV_NOTE_OFF = 1
        private const val EV_ALL_NOTES_OFF = 2
        private const val EV_SUSTAIN = 3
        private const val EV_SET_TEMPO = 4
        private const val EV_TRANSPORT_START = 5
        private const val EV_TRANSPORT_STOP = 6
        private const val EV_SEEK = 7
        private const val EV_METRONOMO = 8
        private const val EV_REVERB = 9
        private const val INMEDIATO = -1.0

        init {
            System.loadLibrary("pianoengine")
        }
    }
}
