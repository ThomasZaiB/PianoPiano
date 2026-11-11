package com.tuapp.piano.domain.repository

import com.tuapp.piano.domain.model.Ajustes
import kotlinx.coroutines.flow.StateFlow

/** Preferencias del usuario, persistentes y observables desde cualquier pantalla. */
interface AjustesRepository {
    val ajustes: StateFlow<Ajustes>

    /** Aplica [cambio] sobre los ajustes actuales y lo guarda. */
    fun actualizar(cambio: (Ajustes) -> Ajustes)
}
