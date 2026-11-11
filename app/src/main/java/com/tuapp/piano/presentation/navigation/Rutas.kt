package com.tuapp.piano.presentation.navigation

import com.tuapp.piano.domain.model.ConfigGrabacion
import com.tuapp.piano.domain.model.ModoPiano

/** Rutas del NavGraph. Los nombres de argumento son las claves que lee el SavedStateHandle. */
object Rutas {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SONG_SETUP = "song_setup"
    const val IMPORTAR = "import"
    const val AJUSTES = "settings"

    const val ARG_MODO = "modo"
    const val ARG_CANCION_ID = "cancionId"
    const val ARG_BPM = "bpm"
    const val ARG_NUMERADOR = "num"
    const val ARG_DENOMINADOR = "den"
    const val ARG_CUANTIZACION = "cuant"
    const val ARG_HUMANA = "humana"
    const val ARG_METRONOMO = "metro"

    /** piano/{modo}/{cancionId} + parámetros opcionales de grabación como query. */
    const val PIANO = "piano/{$ARG_MODO}/{$ARG_CANCION_ID}" +
        "?$ARG_BPM={$ARG_BPM}&$ARG_NUMERADOR={$ARG_NUMERADOR}&$ARG_DENOMINADOR={$ARG_DENOMINADOR}" +
        "&$ARG_CUANTIZACION={$ARG_CUANTIZACION}&$ARG_HUMANA={$ARG_HUMANA}&$ARG_METRONOMO={$ARG_METRONOMO}"

    fun piano(modo: ModoPiano, cancionId: Long = 0L, config: ConfigGrabacion? = null): String {
        val base = "piano/${modo.name}/$cancionId"
        if (config == null) return base
        return base +
            "?$ARG_BPM=${config.bpm}" +
            "&$ARG_NUMERADOR=${config.compas.numerador}&$ARG_DENOMINADOR=${config.compas.denominador}" +
            "&$ARG_CUANTIZACION=${config.cuantizacion.name}" +
            "&$ARG_HUMANA=${config.cuantizacionHumana}&$ARG_METRONOMO=${config.metronomo}"
    }
}
