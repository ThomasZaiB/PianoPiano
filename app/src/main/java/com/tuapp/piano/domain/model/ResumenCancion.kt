package com.tuapp.piano.domain.model

/** Lo que muestra cada tarjeta de la biblioteca (sin cargar las notas). */
data class ResumenCancion(
    val id: Long,
    val titulo: String,
    val bpm: Double,
    val compas: Compas,
    val cuantizacion: Cuantizacion,
    val fechaCreacion: Long,
    val origen: OrigenCancion,
    val numNotas: Int,
    val duracionTicks: Long,
) {
    /** Duración aproximada en segundos al tempo de la canción. */
    val duracionSegundos: Double get() = duracionTicks * 60.0 / (bpm * PPQ)
}
