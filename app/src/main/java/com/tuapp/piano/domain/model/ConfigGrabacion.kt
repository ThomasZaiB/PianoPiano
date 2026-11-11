package com.tuapp.piano.domain.model

/** Parámetros elegidos en SongSetupScreen (llegan al PianoViewModel por SavedStateHandle). */
data class ConfigGrabacion(
    val bpm: Int = 100,
    val compas: Compas = Compas(4, 4),
    val cuantizacion: Cuantizacion = Cuantizacion.SEMICORCHEA,
    /** true = "Humana": acerca las notas a la rejilla pero conserva parte de la variación. */
    val cuantizacionHumana: Boolean = true,
    val metronomo: Boolean = true,
)
