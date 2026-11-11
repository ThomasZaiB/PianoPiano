package com.tuapp.piano.domain.usecase

import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.musica.LectorMidi
import com.tuapp.piano.domain.repository.CancionRepository
import javax.inject.Inject

/** Crea una canción a partir de un archivo .mid y la guarda en la biblioteca. */
class ImportarMidiUseCase @Inject constructor(
    private val repositorio: CancionRepository,
) {
    data class Resultado(val id: Long, val titulo: String, val notas: Int, val descartadas: Int)

    /** Lanza [com.tuapp.piano.domain.musica.ErrorMidi] si el archivo no es válido. */
    suspend operator fun invoke(
        bytes: ByteArray,
        nombreArchivo: String?,
        ahoraMillis: Long = System.currentTimeMillis(),
    ): Resultado {
        val leido = LectorMidi.leer(bytes)
        val cancion = Cancion(
            titulo = (leido.titulo ?: nombreArchivo?.substringBeforeLast('.'))
                ?.trim()?.take(60)?.ifEmpty { null } ?: "Canción importada",
            bpm = leido.bpm,
            compas = leido.compas,
            notas = leido.notas,
            fechaCreacion = ahoraMillis,
        )
        val id = repositorio.crear(cancion)
        return Resultado(id, cancion.titulo, cancion.notas.size, leido.descartadas)
    }
}
