package com.tuapp.piano.domain.grabacion

import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.Nota
import kotlin.math.max

/**
 * Convierte pares Note On / Note Off (con su tick del reloj del motor) en [Nota]s.
 * No es thread-safe: el ViewModel lo usa solo desde el hilo principal,
 * que es donde llegan los toques del teclado.
 */
class GrabadorNotas {

    private class Abierta(val inicio: Long, val velocidad: Int, val mano: Mano)

    private val abiertas = HashMap<Int, Abierta>()
    private val terminadas = ArrayList<Nota>()

    val cantidad: Int get() = terminadas.size + abiertas.size
    val estaVacio: Boolean get() = cantidad == 0

    fun notaOn(nota: Int, velocidad: Int, mano: Mano, tick: Long) {
        if (nota !in Nota.NOTA_MIN..Nota.NOTA_MAX) return
        // Si la tecla ya estaba abierta (dos dedos, glissando rápido) se cierra antes de reabrirla.
        cerrar(nota, tick)
        abiertas[nota] = Abierta(max(0L, tick), velocidad.coerceIn(1, 127), mano)
    }

    fun notaOff(nota: Int, tick: Long) = cerrar(nota, tick)

    /** Al pausar o detener: termina las teclas que seguían presionadas. */
    fun cerrarTodas(tick: Long) {
        abiertas.keys.toList().forEach { cerrar(it, tick) }
    }

    /** Notas terminadas, ordenadas por inicio. Las teclas aún presionadas no se incluyen. */
    fun notas(): List<Nota> = terminadas.sortedWith(compareBy<Nota> { it.tickInicio }.thenBy { it.nota })

    fun limpiar() {
        abiertas.clear()
        terminadas.clear()
    }

    private fun cerrar(nota: Int, tick: Long) {
        val a = abiertas.remove(nota) ?: return
        terminadas += Nota(
            nota = nota,
            tickInicio = a.inicio,
            duracionTicks = max(1L, tick - a.inicio),
            mano = a.mano,
            velocidad = a.velocidad,
        )
    }
}
