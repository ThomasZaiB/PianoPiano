package com.tuapp.piano.domain.practica

import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.Nota
import kotlin.math.max

/** Qué mano practica el usuario. La otra (si la hay) suena sola como acompañamiento. */
enum class ManoPractica { AMBAS, IZQUIERDA, DERECHA }

/**
 * Qué parte de la canción se practica.
 * [tramo]: compases (empezando en 1, ambos incluidos) que se repiten en bucle; null = toda la canción.
 */
data class SeleccionPractica(
    val mano: ManoPractica = ManoPractica.AMBAS,
    val tramo: IntRange? = null,
) {
    /** Notas que el usuario debe tocar. */
    fun notasAEvaluar(cancion: Cancion): List<Nota> =
        cancion.notas.filter { esDeLaMano(it) && dentroDelTramo(it, cancion) }

    /** Notas que suenan solas mientras el usuario practica la otra mano. */
    fun notasDeAcompanamiento(cancion: Cancion): List<Nota> =
        if (mano == ManoPractica.AMBAS) emptyList() else cancion.notas.filterNot(::esDeLaMano)

    /** Tick donde empieza la práctica: un compás antes del tramo, para prepararse. */
    fun tickInicio(cancion: Cancion): Long {
        val t = tramo ?: return 0L
        return max(0L, inicioTramo(t, cancion) - cancion.compas.ticksPorCompas)
    }

    /** Tick donde termina (o vuelve a empezar, si hay tramo). */
    fun tickFin(cancion: Cancion): Long {
        val t = tramo ?: return cancion.duracionTicks
        return (t.last.toLong()) * cancion.compas.ticksPorCompas
    }

    private fun esDeLaMano(n: Nota) = when (mano) {
        ManoPractica.AMBAS -> true
        ManoPractica.IZQUIERDA -> n.mano == Mano.IZQUIERDA
        ManoPractica.DERECHA -> n.mano == Mano.DERECHA
    }

    private fun dentroDelTramo(n: Nota, cancion: Cancion): Boolean {
        val t = tramo ?: return true
        return n.tickInicio >= inicioTramo(t, cancion) && n.tickInicio < tickFin(cancion)
    }

    private fun inicioTramo(t: IntRange, cancion: Cancion) = (t.first - 1L) * cancion.compas.ticksPorCompas

    companion object {
        /** Compases de la canción (al menos 1). */
        fun totalCompases(cancion: Cancion): Int {
            val porCompas = cancion.compas.ticksPorCompas
            return max(1L, (cancion.duracionTicks + porCompas - 1) / porCompas).toInt()
        }
    }
}
