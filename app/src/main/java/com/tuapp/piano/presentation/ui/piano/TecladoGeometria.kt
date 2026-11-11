package com.tuapp.piano.presentation.ui.piano

import com.tuapp.piano.domain.model.EtiquetasTeclado
import com.tuapp.piano.domain.model.NombresNotas
import com.tuapp.piano.domain.model.Notacion
import com.tuapp.piano.domain.model.Nota

/**
 * Datos fijos del teclado de 88 teclas (La0 = 21 … Do8 = 108).
 * Las coordenadas X se expresan en "unidades de tecla blanca": la vista las
 * multiplica por el ancho actual de tecla blanca (que depende del zoom).
 */
internal object TecladoGeometria {
    const val PRIMERA = Nota.NOTA_MIN
    const val ULTIMA = Nota.NOTA_MAX
    const val TOTAL_BLANCAS = 52

    /** Ancho de una negra respecto a una blanca, y largo respecto al teclado. */
    const val PROPORCION_ANCHO_NEGRA = 0.6f
    const val PROPORCION_ALTO_NEGRA = 0.62f

    private val NEGRAS_EN_OCTAVA = intArrayOf(1, 3, 6, 8, 10)

    val esNegra = BooleanArray(128) { n -> (n % 12) in NEGRAS_EN_OCTAVA }

    /** Para blancas: su índice 0..51. Para negras: el índice de la blanca a su izquierda. */
    val indiceBlanca = IntArray(128)

    /** Nota MIDI de cada tecla blanca (índice 0..51). */
    val notaBlanca = IntArray(TOTAL_BLANCAS)


    init {
        var w = -1
        for (n in PRIMERA..ULTIMA) {
            if (!esNegra[n]) {
                w++
                notaBlanca[w] = n
            }
            indiceBlanca[n] = w
        }
        check(w == TOTAL_BLANCAS - 1)
    }

    private val SIN_ETIQUETAS = arrayOfNulls<String>(128)

    /** Una tabla por notación y modo, precalculada: el render no crea Strings al dibujar. */
    private val tablasEtiquetas = Notacion.entries.associateWith { notacion ->
        EtiquetasTeclado.entries.associateWith { modo ->
            Array(128) { n ->
                val visible = n in PRIMERA..ULTIMA && when (modo) {
                    EtiquetasTeclado.NINGUNA -> false
                    EtiquetasTeclado.SOLO_DO -> n % 12 == 0
                    EtiquetasTeclado.BLANCAS -> !esNegra[n]
                }
                if (visible) NombresNotas.nombre(n, notacion) else null
            }
        }
    }

    /** Texto de cada tecla (null = sin etiqueta) según los ajustes. */
    fun etiquetas(notacion: Notacion, modo: EtiquetasTeclado): Array<String?> =
        tablasEtiquetas[notacion]?.get(modo) ?: SIN_ETIQUETAS

    /** Borde izquierdo de la tecla, en unidades de tecla blanca. */
    fun xUnidades(nota: Int): Float =
        if (esNegra[nota]) indiceBlanca[nota] + 1f - PROPORCION_ANCHO_NEGRA / 2f
        else indiceBlanca[nota].toFloat()

    fun anchoUnidades(nota: Int): Float = if (esNegra[nota]) PROPORCION_ANCHO_NEGRA else 1f
}
