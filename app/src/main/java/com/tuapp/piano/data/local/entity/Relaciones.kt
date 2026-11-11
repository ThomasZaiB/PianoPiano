package com.tuapp.piano.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/** Canción con todas sus notas (consulta @Transaction en el DAO). */
data class CancionConNotas(
    @Embedded val cancion: CancionEntity,
    @Relation(parentColumn = "id", entityColumn = "cancionId")
    val notas: List<NotaEntity>,
)

/** Fila de la biblioteca: la canción más dos agregados calculados en SQL. */
data class CancionResumenFila(
    @Embedded val cancion: CancionEntity,
    val numNotas: Int,
    val duracionTicks: Long,
)
