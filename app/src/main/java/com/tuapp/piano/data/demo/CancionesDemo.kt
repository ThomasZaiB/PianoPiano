package com.tuapp.piano.data.demo

import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.Compas
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.Nota
import com.tuapp.piano.domain.model.PPQ

/**
 * Canción de demostración para probar la cascada y el secuenciador antes de que
 * existan la grabación (Paso 4) y Room (Paso 5).
 * Himno de la Alegría (Beethoven, Sinfonía n.º 9, 1824): dominio público.
 */
object CancionesDemo {

    private const val CORCHEA = PPQ / 2L

    // Duraciones en corcheas: 1 = corchea, 2 = negra, 3 = negra con puntillo, 4 = blanca.
    private val FRASE_A = listOf(64 to 2, 64 to 2, 65 to 2, 67 to 2, 67 to 2, 65 to 2, 64 to 2, 62 to 2,
                                 60 to 2, 60 to 2, 62 to 2, 64 to 2)
    private val FINAL_1 = listOf(64 to 3, 62 to 1, 62 to 4)
    private val FINAL_2 = listOf(62 to 3, 60 to 1, 60 to 4)
    private val PUENTE = listOf(62 to 2, 62 to 2, 64 to 2, 60 to 2,
                                62 to 2, 64 to 1, 65 to 1, 64 to 2, 60 to 2,
                                62 to 2, 64 to 1, 65 to 1, 64 to 2, 62 to 2,
                                60 to 2, 62 to 2, 55 to 4)

    private val MELODIA = FRASE_A + FINAL_1 + FRASE_A + FINAL_2 + PUENTE + FRASE_A + FINAL_2

    /** Armonía de cada uno de los 16 compases: 'C' = Do mayor, 'G' = Sol mayor. */
    private const val ARMONIA = "CGCG" + "CGCC" + "GCGG" + "CGCC"

    fun himnoDeLaAlegria(): Cancion {
        val compas = Compas(4, 4)
        val entrada = compas.ticksPorCompas.toLong() // un compás vacío para que las notas "caigan"
        val notas = mutableListOf<Nota>()

        // Mano derecha: melodía, ligeramente más corta que su valor para que se vean separadas.
        var tick = entrada
        for ((nota, corcheas) in MELODIA) {
            val duracion = corcheas * CORCHEA
            notas += Nota(nota, tick, duracion - 30, Mano.DERECHA, velocidad = 96)
            tick += duracion
        }

        // Mano izquierda: fundamental y quinta en blancas.
        ARMONIA.forEachIndexed { i, acorde ->
            val (fundamental, quinta) = if (acorde == 'C') 48 to 55 else 43 to 50
            val inicio = entrada + i * compas.ticksPorCompas.toLong()
            val blanca = 2L * PPQ
            notas += Nota(fundamental, inicio, blanca - 30, Mano.IZQUIERDA, velocidad = 70)
            notas += Nota(quinta, inicio + blanca, blanca - 30, Mano.IZQUIERDA, velocidad = 64)
        }

        return Cancion.desdeNotas("Himno de la Alegría (demo)", notas, bpm = 100.0, compas = compas)
    }
}
