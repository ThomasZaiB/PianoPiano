package com.tuapp.piano.domain.model

/** Compás musical (4/4, 3/4, 6/8…). Todo se expresa en ticks PPQ. */
data class Compas(
    val numerador: Int = 4,
    val denominador: Int = 4,
) {
    init {
        require(numerador in 1..32) { "Numerador inválido: $numerador" }
        require(denominador in setOf(1, 2, 4, 8, 16, 32)) { "Denominador inválido: $denominador" }
    }

    /** Ticks de una unidad de pulso (en 4/4 = una negra = PPQ). */
    val ticksPorTiempo: Int get() = PPQ * 4 / denominador

    val ticksPorCompas: Int get() = ticksPorTiempo * numerador

    override fun toString() = "$numerador/$denominador"
}
