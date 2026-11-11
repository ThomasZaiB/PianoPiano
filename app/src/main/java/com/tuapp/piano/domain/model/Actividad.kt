package com.tuapp.piano.domain.model

/** Lo que el usuario ha ido haciendo, para que el inicio le ofrezca seguir donde lo dejó. */
data class Actividad(
    /** Canciones abiertas, la más reciente primero (sin repetidos). */
    val recientes: List<Long> = emptyList(),
    /** Modo en que se abrió la canción más reciente (reproducir o practicar). */
    val ultimoModo: ModoPiano? = null,
    /** Mejor precisión de práctica (%) por canción. */
    val mejores: Map<Long, Int> = emptyMap(),
) {
    /** Registra que se abrió [id] en [modo]: pasa al frente de las recientes. */
    fun conApertura(id: Long, modo: ModoPiano): Actividad =
        copy(recientes = (listOf(id) + recientes.filterNot { it == id }).take(MAX_RECIENTES), ultimoModo = modo)

    /** Guarda [precision] solo si mejora la marca anterior de la canción. */
    fun conResultado(id: Long, precision: Int): Actividad {
        val anterior = mejores[id]
        return if (anterior != null && anterior >= precision) this else copy(mejores = mejores + (id to precision))
    }

    companion object {
        const val MAX_RECIENTES = 10
    }
}
