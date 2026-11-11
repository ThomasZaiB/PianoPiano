package com.tuapp.piano.data.local

import com.tuapp.piano.data.local.entity.CancionConNotas
import com.tuapp.piano.data.local.entity.CancionEntity
import com.tuapp.piano.data.local.entity.CancionResumenFila
import com.tuapp.piano.data.local.entity.NotaEntity
import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.Compas
import com.tuapp.piano.domain.model.Cuantizacion
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.Nota
import com.tuapp.piano.domain.model.OrigenCancion
import com.tuapp.piano.domain.model.ResumenCancion

// ---- Dominio -> Room ----

fun Compas.aTexto(): String = "$numerador/$denominador"

fun Cancion.aEntidad(): CancionEntity = CancionEntity(
    id = id,
    titulo = titulo,
    bpm = bpm,
    compas = compas.aTexto(),
    cuantizacion = cuantizacion.name,
    cuantizacionHumana = cuantizacionHumana,
    fechaCreacion = fechaCreacion,
    origen = origen.name,
)

fun Nota.aEntidad(cancionId: Long): NotaEntity = NotaEntity(
    cancionId = cancionId,
    tickInicio = tickInicio,
    duracionTicks = duracionTicks,
    notaMidi = nota,
    velocidad = velocidad,
    mano = mano.name,
)

// ---- Room -> Dominio ----

fun compasDesdeTexto(texto: String): Compas {
    val partes = texto.split('/')
    val num = partes.getOrNull(0)?.toIntOrNull() ?: 4
    val den = partes.getOrNull(1)?.toIntOrNull() ?: 4
    return runCatching { Compas(num, den) }.getOrDefault(Compas())
}

private fun origenDesde(texto: String) =
    OrigenCancion.entries.firstOrNull { it.name == texto } ?: OrigenCancion.CREADA

private fun manoDesde(texto: String) = Mano.entries.firstOrNull { it.name == texto } ?: Mano.DERECHA

fun NotaEntity.aDominio(): Nota = Nota(
    nota = notaMidi,
    tickInicio = tickInicio,
    duracionTicks = duracionTicks,
    mano = manoDesde(mano),
    velocidad = velocidad,
)

fun CancionConNotas.aDominio(): Cancion = Cancion(
    id = cancion.id,
    titulo = cancion.titulo,
    bpm = cancion.bpm,
    compas = compasDesdeTexto(cancion.compas),
    notas = notas.map { it.aDominio() }.sortedWith(compareBy<Nota> { it.tickInicio }.thenBy { it.nota }),
    cuantizacion = Cuantizacion.desde(cancion.cuantizacion),
    cuantizacionHumana = cancion.cuantizacionHumana,
    origen = origenDesde(cancion.origen),
    fechaCreacion = cancion.fechaCreacion,
)

fun CancionResumenFila.aDominio(): ResumenCancion = ResumenCancion(
    id = cancion.id,
    titulo = cancion.titulo,
    bpm = cancion.bpm,
    compas = compasDesdeTexto(cancion.compas),
    cuantizacion = Cuantizacion.desde(cancion.cuantizacion),
    fechaCreacion = cancion.fechaCreacion,
    origen = origenDesde(cancion.origen),
    numNotas = numNotas,
    duracionTicks = duracionTicks,
)
