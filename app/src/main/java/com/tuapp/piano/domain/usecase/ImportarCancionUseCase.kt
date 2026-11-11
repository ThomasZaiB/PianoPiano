package com.tuapp.piano.domain.usecase

import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.Compas
import com.tuapp.piano.domain.model.Cuantizacion
import com.tuapp.piano.domain.musica.FiguraPaso
import com.tuapp.piano.domain.musica.NotacionTeclado
import com.tuapp.piano.domain.repository.CancionRepository
import javax.inject.Inject

/** Crea una canción a partir de notación de texto (ver [NotacionTeclado]) y la guarda. */
class ImportarCancionUseCase @Inject constructor(
    private val repositorio: CancionRepository,
) {
    suspend operator fun invoke(
        titulo: String,
        notacion: String,
        bpm: Int,
        compas: Compas,
        figura: FiguraPaso,
        ahoraMillis: Long = System.currentTimeMillis(),
    ): Long = repositorio.crear(construir(titulo, notacion, bpm, compas, figura, ahoraMillis))

    companion object {
        /**
         * Sin efectos: la usan también la vista previa del importador y la semilla de
         * canciones incluidas. Lanza [com.tuapp.piano.domain.musica.ErrorNotacion] si el texto no es válido.
         */
        fun construir(
            titulo: String,
            notacion: String,
            bpm: Int,
            compas: Compas,
            figura: FiguraPaso,
            ahoraMillis: Long,
        ): Cancion {
            // Un compás vacío al principio, igual que la demo: las notas "caen" desde arriba.
            val notas = NotacionTeclado.parsear(notacion, figura.ticks, tickInicial = compas.ticksPorCompas.toLong())
            require(notas.isNotEmpty()) { "El texto no tiene ninguna nota" }
            return Cancion(
                titulo = titulo.trim().ifEmpty { "Canción importada" },
                bpm = bpm.coerceIn(40, 240).toDouble(),
                compas = compas,
                notas = notas,
                // Para el overdub: las notas nuevas se alinean a la misma rejilla que la canción.
                cuantizacion = when (figura) {
                    FiguraPaso.NEGRA -> Cuantizacion.NEGRA
                    FiguraPaso.CORCHEA -> Cuantizacion.CORCHEA
                    FiguraPaso.TRESILLO -> Cuantizacion.TRESILLO
                    FiguraPaso.SEMICORCHEA -> Cuantizacion.SEMICORCHEA
                },
                cuantizacionHumana = false,
                fechaCreacion = ahoraMillis,
            )
        }
    }
}
