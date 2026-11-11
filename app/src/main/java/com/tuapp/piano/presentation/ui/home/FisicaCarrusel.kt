package com.tuapp.piano.presentation.ui.home

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Física del carrusel de recientes, con las fórmulas que publica Apple en
 * "Designing Fluid Interfaces" (WWDC 2018). Sin dependencias de Android: se testea sola.
 *
 * Convención: `posicion` es cuánto se ha desplazado el carrusel en px (0 = primera
 * tarjeta alineada a la izquierda; crece al avanzar). La velocidad va en px/s, en el
 * mismo sentido que `posicion`.
 */
object FisicaCarrusel {

    /** Desaceleración tipo scroll de iOS. Más baja = se frena antes. */
    const val DESACELERACION = 0.998f

    /** Cuánto más avanzaría el contenido si se soltara con [velocidad] y se dejara frenar solo. */
    fun proyectar(velocidad: Float, desaceleracion: Float = DESACELERACION): Float =
        (velocidad / 1000f) * desaceleracion / (1f - desaceleracion)

    /**
     * Tarjeta en la que se detiene: la más cercana al punto PROYECTADO, no al de suelta.
     * Así un lanzamiento corto pero rápido avanza varias tarjetas, como un objeto lanzado.
     */
    fun indiceDestino(posicion: Float, velocidad: Float, paso: Float, total: Int): Int {
        if (total <= 0 || paso <= 0f) return 0
        val proyectada = posicion + proyectar(velocidad)
        return (proyectada / paso).roundToInt().coerceIn(0, total - 1)
    }

    /**
     * Resistencia elástica más allá de un borde: cuanto más se pasa, menos sigue al dedo.
     * [exceso] en px (con signo), [dimension] = ancho visible. Devuelve el desplazamiento real.
     */
    fun elastico(exceso: Float, dimension: Float, constante: Float = 0.55f): Float {
        if (dimension <= 0f) return 0f
        return (exceso * dimension * constante) / (dimension + constante * abs(exceso))
    }

    /** Aplica [elastico] fuera de [min]..[max] y deja igual lo de dentro. */
    fun conBordesElasticos(posicion: Float, min: Float, max: Float, dimension: Float): Float = when {
        posicion < min -> min + elastico(posicion - min, dimension)
        posicion > max -> max + elastico(posicion - max, dimension)
        else -> posicion
    }
}
