package com.tuapp.piano.domain.repository

import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.Nota
import com.tuapp.piano.domain.model.ResumenCancion
import kotlinx.coroutines.flow.Flow

/** Contrato de persistencia de canciones. La implementación con Room vive en data/. */
interface CancionRepository {

    /** Todas las canciones (más recientes primero); se re-emite con cada cambio en la base. */
    fun observarResumenes(): Flow<List<ResumenCancion>>

    /** Canción completa con sus notas ordenadas, o null si no existe. */
    suspend fun obtener(id: Long): Cancion?

    /** Inserta una canción nueva con sus notas. Devuelve su id. */
    suspend fun crear(cancion: Cancion): Long

    /** Overdub: añade notas a una canción existente y opcionalmente la renombra. */
    suspend fun agregarNotas(cancionId: Long, notas: List<Nota>, nuevoTitulo: String?)

    suspend fun borrar(id: Long)

    suspend fun marcarExportada(id: Long)
}
