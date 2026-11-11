package com.tuapp.piano.data.midi

import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.PPQ
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Escritor de Standard MIDI File (SMF) tipo 1, sin dependencias.
 *
 *  - Pista 0: nombre, tempo y compás.
 *  - Pista 1: mano derecha (canal MIDI 1), piano de cola.
 *  - Pista 2: mano izquierda (canal MIDI 2), piano de cola.
 *
 * La división temporal es el mismo PPQ del proyecto (960), así que los ticks se
 * copian tal cual: no hay pérdida de precisión al exportar.
 */
object MidiFileWriter {

    fun escribir(cancion: Cancion, destino: OutputStream) {
        val pistas = listOf(
            pistaDeTempo(cancion),
            pistaDeMano(cancion, Mano.DERECHA, "Mano derecha"),
            pistaDeMano(cancion, Mano.IZQUIERDA, "Mano izquierda"),
        )
        destino.write(cabecera(pistas.size))
        pistas.forEach { destino.write(chunk("MTrk", it)) }
        destino.flush()
    }

    fun aBytes(cancion: Cancion): ByteArray =
        ByteArrayOutputStream().also { escribir(cancion, it) }.toByteArray()

    // ------------------------------------------------------------------------------

    private fun cabecera(numPistas: Int): ByteArray {
        val datos = ByteArrayOutputStream()
        datos.u16(1)          // formato 1: varias pistas simultáneas
        datos.u16(numPistas)
        datos.u16(PPQ)        // ticks por negra
        return chunk("MThd", datos.toByteArray())
    }

    private fun pistaDeTempo(cancion: Cancion): ByteArray {
        val p = ByteArrayOutputStream()
        // Latin-1: es lo que leen la mayoría de programas MIDI (tildes y ñ incluidas).
        meta(p, 0x03, cancion.titulo.toByteArray(Charsets.ISO_8859_1)) // nombre de la secuencia

        val microsPorNegra = (60_000_000.0 / cancion.bpm).roundToInt().coerceIn(1, 0xFFFFFF)
        meta(p, 0x51, byteArrayOf((microsPorNegra shr 16).toByte(), (microsPorNegra shr 8).toByte(), microsPorNegra.toByte()))

        val c = cancion.compas
        val log2Denominador = Integer.numberOfTrailingZeros(c.denominador)
        meta(p, 0x58, byteArrayOf(c.numerador.toByte(), log2Denominador.toByte(), 24, 8))

        finDePista(p, 0)
        return p.toByteArray()
    }

    private fun pistaDeMano(cancion: Cancion, mano: Mano, nombre: String): ByteArray {
        val canal = mano.canal and 0x0F
        val p = ByteArrayOutputStream()
        meta(p, 0x03, nombre.toByteArray(Charsets.ISO_8859_1))
        p.vlq(0); p.write(0xC0 or canal); p.write(0) // Program Change: 0 = Acoustic Grand Piano

        // Eventos absolutos; con el mismo tick, los Note Off van antes que los Note On.
        class Ev(val tick: Long, val on: Boolean, val nota: Int, val vel: Int)
        val eventos = cancion.notas
            .filter { it.mano == mano }
            .flatMap { listOf(Ev(it.tickInicio, true, it.nota, it.velocidad), Ev(it.tickFin, false, it.nota, 64)) }
            .sortedWith(compareBy<Ev> { it.tick }.thenBy { it.on }.thenBy { it.nota })

        var tickAnterior = 0L
        for (e in eventos) {
            p.vlq(e.tick - tickAnterior)
            tickAnterior = e.tick
            p.write((if (e.on) 0x90 else 0x80) or canal)
            p.write(e.nota and 0x7F)
            p.write(e.vel.coerceIn(if (e.on) 1 else 0, 127))
        }
        finDePista(p, 0)
        return p.toByteArray()
    }

    private fun meta(p: ByteArrayOutputStream, tipo: Int, datos: ByteArray) {
        p.vlq(0)
        p.write(0xFF)
        p.write(tipo)
        p.vlq(datos.size.toLong())
        p.write(datos)
    }

    private fun finDePista(p: ByteArrayOutputStream, delta: Long) {
        p.vlq(delta)
        p.write(0xFF); p.write(0x2F); p.write(0x00)
    }

    private fun chunk(tipo: String, datos: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(8 + datos.size)
        out.write(tipo.toByteArray(Charsets.US_ASCII))
        out.u32(datos.size.toLong())
        out.write(datos)
        return out.toByteArray()
    }

    /** Cantidad de longitud variable (7 bits por byte, bit alto = "sigue"). */
    internal fun ByteArrayOutputStream.vlq(valor: Long) {
        require(valor in 0..0x0FFFFFFF) { "Delta MIDI fuera de rango: $valor" }
        var buffer = valor and 0x7F
        var v = valor shr 7
        while (v > 0) {
            buffer = (buffer shl 8) or ((v and 0x7F) or 0x80)
            v = v shr 7
        }
        while (true) {
            write((buffer and 0xFF).toInt())
            if (buffer and 0x80 != 0L) buffer = buffer shr 8 else break
        }
    }

    private fun ByteArrayOutputStream.u16(v: Int) {
        write((v shr 8) and 0xFF); write(v and 0xFF)
    }

    private fun ByteArrayOutputStream.u32(v: Long) {
        write(((v shr 24) and 0xFF).toInt()); write(((v shr 16) and 0xFF).toInt())
        write(((v shr 8) and 0xFF).toInt()); write((v and 0xFF).toInt())
    }
}
