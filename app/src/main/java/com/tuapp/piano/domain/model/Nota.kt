package com.tuapp.piano.domain.model

/**
 * Una nota de la partitura, en tiempo musical (ticks PPQ), no en milisegundos:
 * así cambiar el tempo no obliga a recalcular nada.
 */
data class Nota(
    /** Número MIDI: 21 = La0 … 60 = Do central … 108 = Do8. */
    val nota: Int,
    val tickInicio: Long,
    val duracionTicks: Long,
    val mano: Mano,
    val velocidad: Int = 90,
) {
    init {
        require(nota in NOTA_MIN..NOTA_MAX) { "Nota fuera del piano: $nota" }
        require(tickInicio >= 0 && duracionTicks > 0)
        require(velocidad in 1..127)
    }

    val tickFin: Long get() = tickInicio + duracionTicks

    companion object {
        const val NOTA_MIN = 21  // La0, primera tecla
        const val NOTA_MAX = 108 // Do8, tecla 88
    }
}
