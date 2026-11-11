package com.tuapp.piano.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tabla "canciones". Los nombres de columna coinciden con los de las propiedades;
 * SemillaDemoCallback los usa tal cual en SQL, así que si cambias uno, cámbialo allí también.
 */
@Entity(tableName = "canciones")
data class CancionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val bpm: Double,
    /** Formato "numerador/denominador", p. ej. "4/4". */
    val compas: String,
    /** Nombre del enum Cuantizacion, p. ej. "SEMICORCHEA". */
    val cuantizacion: String,
    val cuantizacionHumana: Boolean,
    /** Epoch en milisegundos. */
    val fechaCreacion: Long,
    /** Nombre del enum OrigenCancion: "CREADA" o "EXPORTADA". */
    val origen: String,
)
