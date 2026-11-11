package com.tuapp.piano.domain.model

data class Cancion(
    val id: Long = 0,
    val titulo: String,
    val bpm: Double = 120.0,
    val compas: Compas = Compas(),
    /** Se guardan ordenadas por tickInicio: la cascada y el reproductor lo asumen. */
    val notas: List<Nota>,
    val cuantizacion: Cuantizacion = Cuantizacion.SEMICORCHEA,
    val cuantizacionHumana: Boolean = true,
    val origen: OrigenCancion = OrigenCancion.CREADA,
    val fechaCreacion: Long = 0L,
) {
    init {
        require(notas.zipWithNext().all { (a, b) -> a.tickInicio <= b.tickInicio }) {
            "Las notas deben venir ordenadas por tickInicio"
        }
    }

    val duracionTicks: Long get() = notas.maxOfOrNull { it.tickFin } ?: 0L

    companion object {
        /** Ordena notas desordenadas (p. ej. tras grabar) antes de construir la canción. */
        fun desdeNotas(titulo: String, notas: List<Nota>, bpm: Double = 120.0, compas: Compas = Compas()) =
            Cancion(titulo = titulo, bpm = bpm, compas = compas, notas = notas.sortedBy { it.tickInicio })
    }
}
