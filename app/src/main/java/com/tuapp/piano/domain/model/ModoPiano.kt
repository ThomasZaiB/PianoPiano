package com.tuapp.piano.domain.model

/** Modos de la pantalla del piano (segmento {modo} de la ruta piano/{modo}/{cancionId}). */
enum class ModoPiano {
    /** Tocar sin partitura; permite "Grabar rápido". */
    LIBRE,

    /** Grabar una canción nueva (cancionId = 0) o añadir notas a una existente (overdub). */
    GRABAR,

    /** Reproducir una canción de la biblioteca con la cascada. */
    REPRODUCIR,

    /** Modo espera: la canción se detiene hasta que el usuario toca las notas correctas. */
    PRACTICA;

    companion object {
        fun desde(texto: String?): ModoPiano = entries.firstOrNull { it.name == texto } ?: LIBRE
    }
}
