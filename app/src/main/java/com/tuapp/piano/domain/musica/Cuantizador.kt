package com.tuapp.piano.domain.musica

import com.tuapp.piano.domain.model.Cuantizacion
import com.tuapp.piano.domain.model.Nota
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Cuantización PPQ: acerca el inicio y el final de cada nota a la rejilla elegida.
 *
 *  - Perfecta: redondeo estricto al punto de rejilla más cercano.
 *  - Humana:   la nota se mueve hacia la rejilla pero conserva el [FACTOR_HUMANO]
 *              de su desviación original (un "swing" natural, no robótico).
 *
 * Tras cuantizar se corrigen los choques: dos notas de la misma tecla que quedaron
 * en el mismo tick se funden, y si una se solapa con la siguiente se recorta.
 * Así el resultado siempre es exportable a MIDI sin Note On/Off cruzados.
 */
object Cuantizador {

    /** Fracción de la desviación que se conserva en modo humano (0 = perfecto, 1 = sin cambios). */
    const val FACTOR_HUMANO = 0.35

    fun cuantizar(notas: List<Nota>, rejilla: Cuantizacion, humana: Boolean): List<Nota> {
        val g = rejilla.ticks.toLong()
        val ajustadas = if (g <= 0) {
            notas
        } else {
            val duracionMinima = if (humana) max(1L, g / 2) else g
            notas.map { n ->
                val inicio = max(0L, ajustar(n.tickInicio, g, humana))
                var fin = ajustar(n.tickFin, g, humana)
                if (fin - inicio < duracionMinima) fin = inicio + duracionMinima
                n.copy(tickInicio = inicio, duracionTicks = fin - inicio)
            }
        }
        return resolverChoques(ajustadas)
    }

    internal fun ajustar(tick: Long, g: Long, humana: Boolean): Long {
        val punto = Math.floorDiv(tick + g / 2, g) * g // punto de rejilla más cercano
        return if (!humana) punto else punto + ((tick - punto) * FACTOR_HUMANO).roundToLong()
    }

    /** Elimina duplicados y solapes por tecla. Devuelve la lista ordenada por inicio. */
    internal fun resolverChoques(notas: List<Nota>): List<Nota> {
        val resultado = ArrayList<Nota>(notas.size)
        notas.groupBy { it.nota }.values.forEach { deUnaTecla ->
            val ordenadas = deUnaTecla.sortedWith(compareBy<Nota> { it.tickInicio }.thenByDescending { it.duracionTicks })
            var previa: Nota? = null
            for (n in ordenadas) {
                val p = previa
                when {
                    p == null -> previa = n
                    n.tickInicio == p.tickInicio -> Unit // misma tecla, mismo instante: se queda la más larga
                    p.tickFin > n.tickInicio -> {
                        resultado += p.copy(duracionTicks = n.tickInicio - p.tickInicio)
                        previa = n
                    }
                    else -> {
                        resultado += p
                        previa = n
                    }
                }
            }
            previa?.let { resultado += it }
        }
        return resultado.sortedWith(compareBy<Nota> { it.tickInicio }.thenBy { it.nota })
    }
}
