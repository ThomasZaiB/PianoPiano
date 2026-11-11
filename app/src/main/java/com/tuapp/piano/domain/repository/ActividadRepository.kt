package com.tuapp.piano.domain.repository

import com.tuapp.piano.domain.model.Actividad
import com.tuapp.piano.domain.model.ModoPiano
import kotlinx.coroutines.flow.StateFlow

/** Historial ligero del usuario (canciones recientes y mejores resultados), persistente. */
interface ActividadRepository {
    val actividad: StateFlow<Actividad>

    fun registrarApertura(cancionId: Long, modo: ModoPiano)

    fun registrarResultado(cancionId: Long, precision: Int)
}
