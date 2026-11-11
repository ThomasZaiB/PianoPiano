package com.tuapp.piano.domain.model

import kotlin.math.pow

/** Cómo se escriben las notas: Do4 (latina) o C4 (inglesa). */
enum class Notacion { LATINA, INGLESA }

/** Qué teclas muestran su nombre en el teclado. */
enum class EtiquetasTeclado { NINGUNA, SOLO_DO, BLANCAS }

/** Rapidez con la que caen las notas: cuántos compases caben en la cascada. */
enum class VelocidadCascada(val compasesVisibles: Int) { LENTA(4), NORMAL(2), RAPIDA(1) }

/** Reverberación del motor. [codigo] es el preset de C++ (Reverberacion::Preset). */
enum class Reverberacion(val codigo: Int) { APAGADA(0), SALA(1), AUDITORIO(2) }

/**
 * Curva de respuesta al toque: cómo se traduce la altura de la pulsación en fuerza.
 * [exponente] se aplica a la posición relativa (0 = borde superior, 1 = borde frontal).
 */
enum class SensibilidadToque(val exponente: Float) {
    /** Hay que tocar más abajo para sonar fuerte: más control en piano. */
    SUAVE(1.6f),

    /** Reparto lineal: el comportamiento original. */
    NORMAL(1f),

    /** Suena fuerte con poco recorrido. */
    FUERTE(0.6f);

    /** Velocidad MIDI para una pulsación en [relativa] (0..1). NORMAL = 45 + rel × 82, como siempre. */
    fun velocidad(relativa: Float): Int {
        val r = relativa.coerceIn(0f, 1f)
        val curva = if (this == NORMAL) r else r.pow(exponente)
        return (VELOCIDAD_MIN + curva * (127 - VELOCIDAD_MIN)).toInt()
    }

    companion object {
        const val VELOCIDAD_MIN = 45
    }
}

/** Preferencias del usuario. Los valores por defecto son el comportamiento original de la app. */
data class Ajustes(
    val notacion: Notacion = Notacion.LATINA,
    val etiquetas: EtiquetasTeclado = EtiquetasTeclado.SOLO_DO,
    /** Primera nota de la mano derecha; las de abajo cuentan como mano izquierda. */
    val notaDivision: Int = 60,
    /** true: la fuerza depende de dónde se toca la tecla (más abajo = más fuerte). */
    val dinamicaTactil: Boolean = true,
    val sensibilidad: SensibilidadToque = SensibilidadToque.NORMAL,
    val velocidadCascada: VelocidadCascada = VelocidadCascada.NORMAL,
    /** Zoom del teclado: teclas blancas visibles. Se recuerda entre sesiones. */
    val teclasVisibles: Int = 21,
    val reverberacion: Reverberacion = Reverberacion.SALA,
    /** Vibración corta en cada tecla. Apagada: el motor de vibración añade ruido. */
    val vibrarAlTocar: Boolean = false,
    /** En práctica, vibración de rechazo al fallar una nota. */
    val vibrarAlFallar: Boolean = true,
) {
    companion object {
        /** Rango ofrecido para la división de manos: Do2 … Do6. */
        val RANGO_DIVISION = 36..84
        val RANGO_TECLAS_VISIBLES = 10..52
    }
}
