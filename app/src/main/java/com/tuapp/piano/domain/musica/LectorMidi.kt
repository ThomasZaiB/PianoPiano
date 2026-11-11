package com.tuapp.piano.domain.musica

import com.tuapp.piano.domain.model.Compas
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.Nota
import com.tuapp.piano.domain.model.PPQ

/** Archivo MIDI no válido o no soportado. El mensaje se muestra al usuario. */
class ErrorMidi(mensaje: String) : Exception(mensaje)

/** Resultado de leer un .mid, ya convertido a la resolución y las manos del proyecto. */
data class MidiLeido(
    val titulo: String?,
    val bpm: Double,
    val compas: Compas,
    /** Ordenadas por tickInicio, en PPQ del proyecto. */
    val notas: List<Nota>,
    /** Notas fuera de las 88 teclas o de percusión (canal 10), que no se importan. */
    val descartadas: Int,
)

/**
 * Lector de Standard MIDI File tipo 0 y 1, sin dependencias.
 *
 * Asignación de manos, en este orden:
 *  1. Nombre de pista con "izq"/"left" o "der"/"right" (así vuelven a entrar los .mid que exporta la app).
 *  2. Si las notas vienen en una sola pista y canal: por altura, debajo de Do4 = mano izquierda.
 *  3. Si vienen en varias pistas o canales: cada uno según su altura media; si todos caen del mismo
 *     lado, el más grave pasa a la mano izquierda.
 *
 * Solo se usa el primer tempo y el primer compás del archivo: los cambios posteriores no
 * afectan a las notas (están en ticks), solo a la velocidad de reproducción.
 */
object LectorMidi {

    fun leer(bytes: ByteArray): MidiLeido {
        val r = Lector(bytes)
        if (r.texto(4) != "MThd") throw ErrorMidi("No es un archivo MIDI")
        val largoCabecera = r.u32()
        val formato = r.u16()
        val numPistas = r.u16()
        val division = r.u16()
        r.saltar(largoCabecera - 6)
        if (formato == 2) throw ErrorMidi("Los MIDI de tipo 2 no están soportados")
        if (division and 0x8000 != 0) throw ErrorMidi("Este MIDI usa tiempo SMPTE, no está soportado")
        if (division == 0) throw ErrorMidi("Archivo MIDI dañado")

        val estado = Estado()
        var pista = 0
        while (pista < numPistas && r.quedan() >= 8) {
            val tipo = r.texto(4)
            val largo = r.u32()
            if (largo > r.quedan()) throw ErrorMidi("Archivo MIDI incompleto")
            if (tipo == "MTrk") leerPista(Lector(r.bytes(largo)), pista++, estado) else r.saltar(largo)
        }
        if (estado.notas.isEmpty()) throw ErrorMidi("El archivo no tiene notas de piano")

        val escala = PPQ.toDouble() / division
        val compas = estado.compas ?: Compas()
        val manos = asignarManos(estado)
        val crudas = estado.notas.filter { it.nota in Nota.NOTA_MIN..Nota.NOTA_MAX }
        if (crudas.isEmpty()) throw ErrorMidi("Ninguna nota cae dentro de las 88 teclas")

        // Igual que al importar texto: si empieza enseguida, un compás vacío delante para que
        // las notas "caigan" desde arriba. Un compás exacto no rompe la alineación con la rejilla.
        val primerTick = (crudas.minOf { it.inicio } * escala).toLong()
        val desplazamiento = if (primerTick < compas.ticksPorCompas) compas.ticksPorCompas.toLong() else 0L

        val notas = crudas.map { n ->
            val inicio = (n.inicio * escala).toLong() + desplazamiento
            val duracion = ((n.fin - n.inicio) * escala).toLong().takeIf { it > 0 } ?: DURACION_MINIMA
            Nota(n.nota, inicio, duracion, manos.getValue(n.grupo), n.velocidad.coerceIn(1, 127))
        }.sortedWith(compareBy<Nota> { it.tickInicio }.thenBy { it.nota })

        return MidiLeido(
            titulo = estado.titulo?.trim()?.takeIf { it.isNotEmpty() },
            bpm = (estado.microsPorNegra?.let { 60_000_000.0 / it } ?: 120.0).coerceIn(40.0, 240.0),
            compas = compas,
            notas = notas,
            descartadas = estado.descartadas + (estado.notas.size - crudas.size),
        )
    }

    // ------------------------------------------------------------------------------

    /** Pista + canal: la unidad que se asigna a una mano. */
    private data class Grupo(val pista: Int, val canal: Int)

    private class NotaCruda(val grupo: Grupo, val nota: Int, val inicio: Long, val fin: Long, val velocidad: Int)

    private class Estado {
        val notas = ArrayList<NotaCruda>()
        val nombresPista = HashMap<Int, String>()
        var titulo: String? = null
        var microsPorNegra: Int? = null
        var compas: Compas? = null
        var descartadas = 0
    }

    private fun leerPista(r: Lector, pista: Int, estado: Estado) {
        // Notas abiertas por (canal, nota): una pila admite la misma tecla repetida sin soltar.
        val abiertas = HashMap<Int, ArrayDeque<Pair<Long, Int>>>()
        var tick = 0L
        var estadoCorriente = 0

        fun cerrar(canal: Int, nota: Int) {
            val (inicio, vel) = abiertas[canal * 128 + nota]?.removeFirstOrNull() ?: return
            estado.notas += NotaCruda(Grupo(pista, canal), nota, inicio, tick, vel)
        }

        while (r.quedan() > 0) {
            tick += r.vlq()
            var b = r.u8()
            if (b < 0x80) { // running status: el byte leído ya es el primer dato
                if (estadoCorriente == 0) throw ErrorMidi("Archivo MIDI dañado")
                r.retroceder()
                b = estadoCorriente
            }
            when {
                b == 0xFF -> {
                    val tipo = r.u8()
                    val datos = r.bytes(r.vlq().toInt())
                    when (tipo) {
                        0x2F -> break
                        0x03 -> {
                            val nombre = String(datos, Charsets.ISO_8859_1)
                            estado.nombresPista.putIfAbsent(pista, nombre)
                            if (pista == 0 && estado.titulo == null) estado.titulo = nombre
                        }
                        0x51 -> if (estado.microsPorNegra == null && datos.size >= 3) {
                            estado.microsPorNegra = ((datos[0].toInt() and 0xFF) shl 16) or
                                ((datos[1].toInt() and 0xFF) shl 8) or (datos[2].toInt() and 0xFF)
                        }
                        0x58 -> if (estado.compas == null && datos.size >= 2) {
                            estado.compas = runCatching { Compas(datos[0].toInt(), 1 shl datos[1].toInt()) }.getOrNull()
                        }
                    }
                }
                b == 0xF0 || b == 0xF7 -> r.saltar(r.vlq().toInt()) // SysEx
                else -> {
                    estadoCorriente = b
                    val canal = b and 0x0F
                    when (b and 0xF0) {
                        0x90, 0x80 -> {
                            val nota = r.u8() and 0x7F
                            val vel = r.u8() and 0x7F
                            when {
                                canal == CANAL_PERCUSION -> if (b and 0xF0 == 0x90 && vel > 0) estado.descartadas++
                                b and 0xF0 == 0x90 && vel > 0 ->
                                    abiertas.getOrPut(canal * 128 + nota) { ArrayDeque() }.addLast(tick to vel)
                                else -> cerrar(canal, nota)
                            }
                        }
                        0xA0, 0xB0, 0xE0 -> r.saltar(2)
                        0xC0, 0xD0 -> r.saltar(1)
                        else -> throw ErrorMidi("Archivo MIDI dañado")
                    }
                }
            }
        }
        // Notas sin Note Off: terminan donde termina la pista.
        for ((clave, pila) in abiertas) {
            while (pila.isNotEmpty()) cerrar(clave / 128, clave % 128)
        }
    }

    private fun asignarManos(estado: Estado): Map<Grupo, Mano> {
        val porGrupo = estado.notas.groupBy { it.grupo }
        val resultado = HashMap<Grupo, Mano>()
        val sinNombre = ArrayList<Grupo>()
        for (g in porGrupo.keys) {
            val nombre = estado.nombresPista[g.pista]?.lowercase().orEmpty()
            when {
                PALABRAS_IZQUIERDA.any { it in nombre } -> resultado[g] = Mano.IZQUIERDA
                PALABRAS_DERECHA.any { it in nombre } -> resultado[g] = Mano.DERECHA
                else -> sinNombre += g
            }
        }
        if (sinNombre.isEmpty()) return resultado

        // Una sola pista/canal sin nombre y nada más: se reparte nota a nota por altura.
        if (porGrupo.size == 1) {
            val g = sinNombre.single()
            estado.notasPorAltura()
            return mapOf(g to Mano.DERECHA, g.copy(canal = CANAL_IZQUIERDA_VIRTUAL) to Mano.IZQUIERDA)
        }

        val media = sinNombre.associateWith { g -> porGrupo.getValue(g).map { it.nota }.average() }
        sinNombre.forEach { g -> resultado[g] = if (media.getValue(g) < DO_CENTRAL) Mano.IZQUIERDA else Mano.DERECHA }
        if (resultado.values.toSet().size == 1 && sinNombre.size >= 2) {
            resultado[media.minBy { it.value }.key] = Mano.IZQUIERDA
            if (resultado.values.all { it == Mano.IZQUIERDA }) resultado[media.maxBy { it.value }.key] = Mano.DERECHA
        }
        return resultado
    }

    /** Pasa las notas graves del único grupo a un grupo virtual de mano izquierda. */
    private fun Estado.notasPorAltura() {
        val g = notas.first().grupo
        val izquierda = g.copy(canal = CANAL_IZQUIERDA_VIRTUAL)
        for (i in notas.indices) {
            val n = notas[i]
            if (n.nota < DO_CENTRAL) notas[i] = NotaCruda(izquierda, n.nota, n.inicio, n.fin, n.velocidad)
        }
    }

    private const val CANAL_PERCUSION = 9
    /** No es un canal MIDI real (0–15): marca las notas graves al repartir una sola pista. */
    private const val CANAL_IZQUIERDA_VIRTUAL = -1
    private const val DO_CENTRAL = 60
    private const val DURACION_MINIMA = PPQ / 16L
    private val PALABRAS_IZQUIERDA = listOf("izq", "left", "l.h", "lh ", "bajo", "bass")
    private val PALABRAS_DERECHA = listOf("der", "right", "r.h", "rh ", "melod")

    /** Lectura big-endian sobre un arreglo, con errores claros si el archivo se corta. */
    private class Lector(private val datos: ByteArray) {
        private var pos = 0
        fun quedan() = datos.size - pos
        fun u8(): Int {
            if (pos >= datos.size) throw ErrorMidi("Archivo MIDI incompleto")
            return datos[pos++].toInt() and 0xFF
        }
        fun u16() = (u8() shl 8) or u8()
        fun u32() = (u16() shl 16) or u16()
        fun retroceder() { pos-- }
        fun saltar(n: Int) {
            if (n < 0 || n > quedan()) throw ErrorMidi("Archivo MIDI incompleto")
            pos += n
        }
        fun bytes(n: Int): ByteArray {
            if (n < 0 || n > quedan()) throw ErrorMidi("Archivo MIDI incompleto")
            return datos.copyOfRange(pos, pos + n).also { pos += n }
        }
        fun texto(n: Int) = String(bytes(n), Charsets.US_ASCII)
        fun vlq(): Long {
            var v = 0L
            repeat(4) {
                val b = u8()
                v = (v shl 7) or (b and 0x7F).toLong()
                if (b and 0x80 == 0) return v
            }
            throw ErrorMidi("Archivo MIDI dañado")
        }
    }
}
