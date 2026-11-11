package com.tuapp.piano.domain.musica

import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.Nota
import com.tuapp.piano.domain.model.PPQ
import kotlin.math.min

/** Duración de cada paso de la notación (la notación de texto no trae ritmo propio). */
enum class FiguraPaso(val ticks: Int, val etiqueta: String) {
    NEGRA(PPQ, "1/4"),
    CORCHEA(PPQ / 2, "1/8"),
    TRESILLO(PPQ / 3, "Tresillo"),
    SEMICORCHEA(PPQ / 4, "1/16");

    companion object {
        fun desde(texto: String?): FiguraPaso? = entries.firstOrNull {
            it.name.equals(texto?.trim(), ignoreCase = true) || it.etiqueta == texto?.trim()
        }
    }
}

class ErrorNotacion(val token: String, val posicion: Int, mensaje: String) : IllegalArgumentException(mensaje)

/**
 * Lector de la notación de texto típica de partituras para "piano virtual":
 *
 *     [C5E5] [D5F5] B2 E3 [G#3E4] - A4
 *
 *  - Cada elemento separado por espacios es un PASO de tiempo (todos duran lo mismo).
 *  - `[...]` = notas que suenan juntas (acorde). Fuera de corchetes, una nota por paso.
 *  - Nota = letra (A-G) + alteración opcional (`#` o `b`) + octava. C4 = Do central (MIDI 60).
 *  - `-`, `_` o `.` = silencio de un paso. `|` y saltos de línea se ignoran.
 *
 * Como el texto no dice qué mano toca cada nota ni cuánto dura, se deduce:
 *  - Mano izquierda: notas por debajo de C4, y también las de hasta E4 que van en el
 *    mismo acorde que un bajo (el acompañamiento tipo [A3C4E4] queda entero a la izquierda).
 *  - Duración: cada nota suena hasta el siguiente paso de su misma mano (legato),
 *    como mucho [LEGATO_MAX_PASOS] pasos. Un silencio corta ambas manos.
 */
object NotacionTeclado {

    const val LEGATO_MAX_PASOS = 4
    private const val HUECO_TICKS = 20L
    private const val DIVISION = 60          // C4
    private const val TECHO_ACOMPANAMIENTO = 64 // E4

    private val NOTA = Regex("([A-Ga-g])([#b]?)(-?\\d)")
    private val CLASE = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    /** Un paso: lista de notas MIDI (vacía = silencio). */
    fun pasos(texto: String): List<List<Int>> {
        val pasos = ArrayList<List<Int>>()
        var i = 0
        while (i < texto.length) {
            val c = texto[i]
            when {
                c.isWhitespace() || c == '|' -> i++
                c == '-' || c == '_' || c == '.' -> { pasos.add(emptyList()); i++ }
                c == '[' -> {
                    val fin = texto.indexOf(']', i + 1)
                    if (fin < 0) throw ErrorNotacion(texto.substring(i, min(texto.length, i + 12)), i, "Falta cerrar el corchete")
                    val contenido = texto.substring(i + 1, fin).replace(" ", "")
                    val notas = leerNotas(contenido, i).distinct()
                    if (notas.isEmpty()) throw ErrorNotacion("[$contenido]", i, "Acorde vacío")
                    pasos.add(notas)
                    i = fin + 1
                }
                else -> {
                    var fin = i
                    while (fin < texto.length && !texto[fin].isWhitespace() && texto[fin] != '[' && texto[fin] != '|') fin++
                    // Notas sueltas pegadas ("E4A4") = pasos consecutivos.
                    leerNotas(texto.substring(i, fin), i).forEach { pasos.add(listOf(it)) }
                    i = fin
                }
            }
        }
        return pasos
    }

    /** Convierte el texto en notas, empezando en [tickInicial], con [ticksPorPaso] por paso. */
    fun parsear(texto: String, ticksPorPaso: Int, tickInicial: Long = 0L): List<Nota> {
        require(ticksPorPaso > HUECO_TICKS) { "Paso demasiado corto" }
        val pasos = pasos(texto)

        // Mano de cada nota de cada paso.
        val manos: List<List<Pair<Int, Mano>>> = pasos.map { notas ->
            val hayBajo = notas.any { it < DIVISION }
            notas.map { n ->
                val izquierda = n < DIVISION || (hayBajo && n <= TECHO_ACOMPANAMIENTO)
                n to if (izquierda) Mano.IZQUIERDA else Mano.DERECHA
            }
        }

        val resultado = ArrayList<Nota>()
        for ((i, notasPaso) in manos.withIndex()) {
            for ((midi, mano) in notasPaso) {
                // Hasta el siguiente paso con nota de la misma mano, o un silencio.
                var j = i + 1
                while (j < manos.size && j - i < LEGATO_MAX_PASOS &&
                    manos[j].isNotEmpty() && manos[j].none { it.second == mano }
                ) j++
                val pasosDuracion = min(j - i, LEGATO_MAX_PASOS)
                resultado += Nota(
                    nota = midi,
                    tickInicio = tickInicial + i.toLong() * ticksPorPaso,
                    duracionTicks = pasosDuracion.toLong() * ticksPorPaso - HUECO_TICKS,
                    mano = mano,
                    velocidad = if (mano == Mano.DERECHA) 92 else 72,
                )
            }
        }
        return resultado.sortedWith(compareBy<Nota> { it.tickInicio }.thenBy { it.nota })
    }

    private fun leerNotas(texto: String, posicion: Int): List<Int> {
        val notas = ArrayList<Int>()
        var k = 0
        while (k < texto.length) {
            val m = NOTA.matchAt(texto, k)
                ?: throw ErrorNotacion(texto, posicion, "No entiendo «$texto» (se esperaba algo como C4, F#5 o [C4E4G4])")
            val letra = m.groupValues[1][0].uppercaseChar()
            val alteracion = when (m.groupValues[2]) { "#" -> 1; "b" -> -1; else -> 0 }
            val midi = (m.groupValues[3].toInt() + 1) * 12 + CLASE.getValue(letra) + alteracion
            if (midi !in Nota.NOTA_MIN..Nota.NOTA_MAX) {
                throw ErrorNotacion(m.value, posicion, "${m.value} está fuera del piano (A0–C8)")
            }
            notas += midi
            k += m.value.length
        }
        return notas
    }
}

/**
 * Archivo de canción en texto: cabecera opcional con `# clave: valor` y luego la notación.
 *
 *     # titulo: Mi canción
 *     # bpm: 100
 *     # compas: 3/4
 *     # figura: 1/8
 *     [C4E4] D4 E4 ...
 */
data class CancionDeTexto(
    val titulo: String?,
    val bpm: Int?,
    val numerador: Int?,
    val denominador: Int?,
    val figura: FiguraPaso?,
    val notacion: String,
) {
    companion object {
        fun leer(texto: String): CancionDeTexto {
            val cabecera = HashMap<String, String>()
            val cuerpo = StringBuilder()
            texto.lineSequence().forEach { linea ->
                val l = linea.trim()
                if (l.startsWith("#")) {
                    val partes = l.removePrefix("#").split(":", limit = 2)
                    if (partes.size == 2) cabecera[partes[0].trim().lowercase()] = partes[1].trim()
                } else {
                    cuerpo.append(linea).append('\n')
                }
            }
            val compas = cabecera["compas"]?.split("/")
            return CancionDeTexto(
                titulo = cabecera["titulo"],
                bpm = cabecera["bpm"]?.toIntOrNull(),
                numerador = compas?.getOrNull(0)?.trim()?.toIntOrNull(),
                denominador = compas?.getOrNull(1)?.trim()?.toIntOrNull(),
                figura = FiguraPaso.desde(cabecera["figura"]),
                notacion = cuerpo.toString(),
            )
        }
    }
}
