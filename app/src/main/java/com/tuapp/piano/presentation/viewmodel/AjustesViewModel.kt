package com.tuapp.piano.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.tuapp.piano.domain.model.Ajustes
import com.tuapp.piano.domain.model.EtiquetasTeclado
import com.tuapp.piano.domain.model.Notacion
import com.tuapp.piano.domain.model.Reverberacion
import com.tuapp.piano.domain.model.SensibilidadToque
import com.tuapp.piano.domain.model.VelocidadCascada
import com.tuapp.piano.domain.repository.AjustesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Pantalla de ajustes: cada cambio se guarda al momento, no hay botón de "Guardar". */
@HiltViewModel
class AjustesViewModel @Inject constructor(
    private val repositorio: AjustesRepository,
) : ViewModel() {

    val ajustes: StateFlow<Ajustes> = repositorio.ajustes

    fun cambiarNotacion(n: Notacion) = repositorio.actualizar { it.copy(notacion = n) }

    fun cambiarEtiquetas(e: EtiquetasTeclado) = repositorio.actualizar { it.copy(etiquetas = e) }

    fun moverDivision(semitonos: Int) = repositorio.actualizar { it.copy(notaDivision = it.notaDivision + semitonos) }

    fun cambiarDinamica(activa: Boolean) = repositorio.actualizar { it.copy(dinamicaTactil = activa) }

    fun cambiarVelocidad(v: VelocidadCascada) = repositorio.actualizar { it.copy(velocidadCascada = v) }

    fun cambiarSensibilidad(s: SensibilidadToque) = repositorio.actualizar { it.copy(sensibilidad = s) }

    fun cambiarReverberacion(r: Reverberacion) = repositorio.actualizar { it.copy(reverberacion = r) }

    fun cambiarVibrarAlTocar(activa: Boolean) = repositorio.actualizar { it.copy(vibrarAlTocar = activa) }

    fun cambiarVibrarAlFallar(activa: Boolean) = repositorio.actualizar { it.copy(vibrarAlFallar = activa) }

    /** Vuelve a los valores de fábrica, salvo el zoom (no es un ajuste de esta pantalla). */
    fun restablecer() = repositorio.actualizar { Ajustes(teclasVisibles = it.teclasVisibles) }
}
