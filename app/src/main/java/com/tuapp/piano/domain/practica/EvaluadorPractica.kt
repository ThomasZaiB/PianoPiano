package com.tuapp.piano.domain.practica

import com.tuapp.piano.domain.model.Nota
import kotlin.math.abs

/**
 * Lógica del modo práctica ("modo espera"), sin dependencias de Android ni del motor.
 *
 * La canción se divide en grupos: notas que empiezan juntas (un acorde o una nota sola).
 * El usuario debe tocar todas las notas del grupo actual alrededor de su tick:
 *  - Tecla que no pertenece al grupo, o del grupo pero demasiado pronto -> ERROR.
 *  - Pasa el tick del grupo + tolerancia sin completarlo -> [debePausar] = true.
 * Mientras está [esperando], el reloj del motor está parado sobre el grupo y solo
 * se reanuda cuando el grupo se completa.
 *
 * El ViewModel traduce los resultados en TransportStop / Seek / TransportStart.
 */
class EvaluadorPractica(
    notas: List<Nota>,
    /** Margen (en ticks) para aceptar una nota algo antes o después de su tiempo. */
    val toleranciaTicks: Long,
) {
    class Grupo(val tick: Long, val notas: Set<Int>)

    enum class Resultado { ACIERTO, GRUPO_COMPLETO, ERROR, IGNORADA }

    val grupos: List<Grupo> = agrupar(notas)

    var indice: Int = 0
        private set
    var aciertos: Int = 0
        private set
    var errores: Int = 0
        private set
    var esperando: Boolean = false
        private set
    /** Notas correctas seguidas; un error la devuelve a 0. */
    var racha: Int = 0
        private set
    var mejorRacha: Int = 0
        private set

    private val pulsadas = HashSet<Int>()

    val grupoActual: Grupo? get() = grupos.getOrNull(indice)
    val terminado: Boolean get() = indice >= grupos.size

    /** Notas del grupo actual que aún faltan por tocar. */
    val pendientes: Set<Int> get() = grupoActual?.notas?.minus(pulsadas) ?: emptySet()

    val precision: Double
        get() = if (aciertos + errores == 0) 1.0 else aciertos.toDouble() / (aciertos + errores)

    /** true si el reloj ya pasó el grupo actual (más la tolerancia) y el usuario no lo completó. */
    fun debePausar(tick: Double): Boolean {
        val g = grupoActual ?: return false
        return !esperando && tick >= g.tick + toleranciaTicks
    }

    /** El ViewModel llama a esto tras detener el transporte sobre el grupo actual. */
    fun marcarEspera() {
        esperando = true
    }

    fun alPulsar(nota: Int, tick: Double): Resultado {
        val g = grupoActual ?: return Resultado.IGNORADA
        if (nota !in g.notas) {
            errores++
            racha = 0
            return Resultado.ERROR
        }
        if (!esperando && tick < g.tick - toleranciaTicks) {
            errores++ // tecla correcta, pero antes de tiempo
            racha = 0
            return Resultado.ERROR
        }
        if (!pulsadas.add(nota)) return Resultado.IGNORADA // ya contaba
        aciertos++
        racha++
        if (racha > mejorRacha) mejorRacha = racha
        if (pulsadas.containsAll(g.notas)) {
            indice++
            pulsadas.clear()
            esperando = false
            return Resultado.GRUPO_COMPLETO
        }
        return Resultado.ACIERTO
    }

    /** Nueva vuelta de un tramo en bucle: vuelve al primer grupo pero conserva aciertos, errores y racha. */
    fun reiniciarVuelta() {
        indice = 0
        esperando = false
        pulsadas.clear()
    }

    fun reiniciar() {
        indice = 0
        aciertos = 0
        errores = 0
        racha = 0
        mejorRacha = 0
        esperando = false
        pulsadas.clear()
    }

    private fun agrupar(notas: List<Nota>): List<Grupo> {
        val resultado = ArrayList<Grupo>()
        var tickGrupo = Long.MIN_VALUE
        var actual = LinkedHashSet<Int>()
        for (n in notas.sortedBy { it.tickInicio }) {
            if (actual.isEmpty() || abs(n.tickInicio - tickGrupo) > MARGEN_ACORDE) {
                if (actual.isNotEmpty()) resultado += Grupo(tickGrupo, actual)
                actual = LinkedHashSet()
                tickGrupo = n.tickInicio
            }
            actual += n.nota
        }
        if (actual.isNotEmpty()) resultado += Grupo(tickGrupo, actual)
        return resultado
    }

    private companion object {
        /** Notas que empiezan a menos de 1/64 de distancia se consideran el mismo acorde. */
        const val MARGEN_ACORDE = 30L
    }
}
