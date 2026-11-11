package com.tuapp.piano.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Tabla "notas". Al borrar una canción, sus notas se borran en cascada. */
@Entity(
    tableName = "notas",
    foreignKeys = [
        ForeignKey(
            entity = CancionEntity::class,
            parentColumns = ["id"],
            childColumns = ["cancionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cancionId")],
)
data class NotaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cancionId: Long,
    val tickInicio: Long,
    val duracionTicks: Long,
    val notaMidi: Int,
    val velocidad: Int,
    /** Nombre del enum Mano: "DERECHA" o "IZQUIERDA". */
    val mano: String,
)
