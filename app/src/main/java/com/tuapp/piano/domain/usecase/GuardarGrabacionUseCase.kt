package com.tuapp.piano.domain.usecase

import com.tuapp.piano.domain.grabacion.PreparadorGrabacion
import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.ConfigGrabacion
import com.tuapp.piano.domain.model.Nota
import com.tuapp.piano.domain.repository.CancionRepository
import javax.inject.Inject

/** Cuantiza una toma y la guarda: canción nueva o overdub sobre una existente. */
class GuardarGrabacionUseCase @Inject constructor(
    private val repositorio: CancionRepository,
) {
    /** Devuelve el id de la canción guardada. */
    suspend operator fun invoke(
        titulo: String,
        notasGrabadas: List<Nota>,
        config: ConfigGrabacion,
        cancionExistenteId: Long?,
        ahoraMillis: Long = System.currentTimeMillis(),
    ): Long {
        val nombre = titulo.trim().ifEmpty { "Sin título" }
        val notas = PreparadorGrabacion.preparar(
            notas = notasGrabadas,
            compas = config.compas,
            rejilla = config.cuantizacion,
            humana = config.cuantizacionHumana,
            esCancionNueva = cancionExistenteId == null,
        )
        return if (cancionExistenteId != null) {
            repositorio.agregarNotas(cancionExistenteId, notas, nombre)
            cancionExistenteId
        } else {
            repositorio.crear(
                Cancion(
                    titulo = nombre,
                    bpm = config.bpm.toDouble(),
                    compas = config.compas,
                    notas = notas,
                    cuantizacion = config.cuantizacion,
                    cuantizacionHumana = config.cuantizacionHumana,
                    fechaCreacion = ahoraMillis,
                ),
            )
        }
    }
}
