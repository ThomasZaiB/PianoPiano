package com.tuapp.piano.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.tuapp.piano.data.local.entity.CancionConNotas
import com.tuapp.piano.data.local.entity.CancionEntity
import com.tuapp.piano.data.local.entity.CancionResumenFila
import com.tuapp.piano.data.local.entity.NotaEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CancionDao {

    /** Se re-emite automáticamente cada vez que cambian "canciones" o "notas". */
    @Query(
        """
        SELECT c.*,
               (SELECT COUNT(*) FROM notas n WHERE n.cancionId = c.id) AS numNotas,
               (SELECT COALESCE(MAX(n.tickInicio + n.duracionTicks), 0) FROM notas n WHERE n.cancionId = c.id) AS duracionTicks
        FROM canciones c
        ORDER BY c.fechaCreacion DESC
        """,
    )
    abstract fun observarResumenes(): Flow<List<CancionResumenFila>>

    @Transaction
    @Query("SELECT * FROM canciones WHERE id = :id")
    abstract suspend fun obtenerConNotas(id: Long): CancionConNotas?

    @Insert
    abstract suspend fun insertarCancion(cancion: CancionEntity): Long

    @Insert
    abstract suspend fun insertarNotas(notas: List<NotaEntity>)

    @Query("UPDATE canciones SET titulo = :titulo WHERE id = :id")
    abstract suspend fun renombrar(id: Long, titulo: String)

    @Query("UPDATE canciones SET origen = :origen WHERE id = :id")
    abstract suspend fun cambiarOrigen(id: Long, origen: String)

    /** Las notas se borran solas por la clave foránea con CASCADE. */
    @Query("DELETE FROM canciones WHERE id = :id")
    abstract suspend fun borrar(id: Long)

    /** Canción + notas en una sola transacción: o se guarda todo o nada. */
    @Transaction
    open suspend fun insertarCancionConNotas(cancion: CancionEntity, notas: List<NotaEntity>): Long {
        val id = insertarCancion(cancion)
        insertarNotas(notas.map { it.copy(cancionId = id) })
        return id
    }

    @Transaction
    open suspend fun agregarNotas(cancionId: Long, notas: List<NotaEntity>, nuevoTitulo: String?) {
        insertarNotas(notas.map { it.copy(cancionId = cancionId) })
        if (nuevoTitulo != null) renombrar(cancionId, nuevoTitulo)
    }
}
