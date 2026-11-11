package com.tuapp.piano.domain.grabacion

import com.tuapp.piano.domain.model.Compas
import com.tuapp.piano.domain.model.Cuantizacion
import com.tuapp.piano.domain.model.Nota
import com.tuapp.piano.domain.musica.Cuantizador

/** Pasos puros que se aplican a una toma antes de guardarla. */
object PreparadorGrabacion {

    /**
     * 1) Cuantiza.
     * 2) Solo para canciones nuevas: quita el silencio inicial dejando exactamente un
     *    compás vacío antes de la primera nota (para que en la cascada "caiga" desde arriba),
     *    conservando la posición de la nota dentro de su compás. Si la primera nota cae en
     *    el compás 0, se desplaza todo un compás hacia delante para dejar ese hueco.
     *    En un overdub no se mueve nada: las notas nuevas deben cuadrar con las existentes.
     */
    fun preparar(
        notas: List<Nota>,
        compas: Compas,
        rejilla: Cuantizacion,
        humana: Boolean,
        esCancionNueva: Boolean,
    ): List<Nota> {
        val cuantizadas = Cuantizador.cuantizar(notas, rejilla, humana)
        if (!esCancionNueva || cuantizadas.isEmpty()) return cuantizadas
        val ticksPorCompas = compas.ticksPorCompas.toLong()
        val compasPrimeraNota = cuantizadas.first().tickInicio / ticksPorCompas
        val desplazamiento = (compasPrimeraNota - 1) * ticksPorCompas
        // Primera nota ya en el compás 0 (sin cuenta de entrada): se añade el compás vacío.
        return if (desplazamiento != 0L) {
            cuantizadas.map { it.copy(tickInicio = it.tickInicio - desplazamiento) }
        } else {
            cuantizadas
        }
    }
}
