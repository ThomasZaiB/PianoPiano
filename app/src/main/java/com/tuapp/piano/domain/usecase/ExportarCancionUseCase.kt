package com.tuapp.piano.domain.usecase

import com.tuapp.piano.domain.repository.CancionRepository
import com.tuapp.piano.domain.repository.ExportadorMidi
import javax.inject.Inject

class ExportarCancionUseCase @Inject constructor(
    private val repositorio: CancionRepository,
    private val exportador: ExportadorMidi,
) {
    /** Lanza excepción si la canción no existe o no se pudo escribir el archivo. */
    suspend operator fun invoke(cancionId: Long, destinoUri: String) {
        val cancion = repositorio.obtener(cancionId)
            ?: throw IllegalArgumentException("La canción ya no existe")
        exportador.exportar(cancion, destinoUri)
        repositorio.marcarExportada(cancionId)
    }
}
