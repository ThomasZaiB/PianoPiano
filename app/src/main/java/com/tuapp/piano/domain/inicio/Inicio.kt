package com.tuapp.piano.domain.inicio

import com.tuapp.piano.domain.model.Actividad
import com.tuapp.piano.domain.model.ModoPiano
import com.tuapp.piano.domain.model.ResumenCancion

/** Una canción tal como la muestra el inicio, con su mejor resultado si lo hay. */
data class CancionInicio(val resumen: ResumenCancion, val mejorPrecision: Int?)

/** Lo que muestra la pantalla de inicio. */
data class Inicio(
    /** Canción de la tarjeta grande; null si la biblioteca está vacía. */
    val continuar: CancionInicio? = null,
    /** Modo en que la tarjeta grande la abre. */
    val modoContinuar: ModoPiano = ModoPiano.REPRODUCIR,
    /** true: el usuario ya la tocó ("Continuar"); false: es la más nueva de la biblioteca. */
    val yaTocada: Boolean = false,
    /** Carrusel: primero las tocadas hace poco, luego el resto por fecha. */
    val recientes: List<CancionInicio> = emptyList(),
)

/**
 * Arma el inicio a partir de la biblioteca ([canciones], más nuevas primero) y la actividad.
 * Las canciones borradas que sigan en la actividad se ignoran.
 */
fun armarInicio(canciones: List<ResumenCancion>, actividad: Actividad, maxRecientes: Int = 8): Inicio {
    if (canciones.isEmpty()) return Inicio()
    val porId = canciones.associateBy { it.id }
    fun conMarca(r: ResumenCancion) = CancionInicio(r, actividad.mejores[r.id])

    val tocadas = actividad.recientes.mapNotNull { porId[it] }
    val principal = tocadas.firstOrNull() ?: canciones.first()
    val yaTocada = tocadas.isNotEmpty()
    // Si la última que se abrió fue esta, se repite su modo; si no, por defecto reproducir.
    val modo = if (yaTocada && actividad.recientes.first() == principal.id) {
        actividad.ultimoModo ?: ModoPiano.REPRODUCIR
    } else {
        ModoPiano.REPRODUCIR
    }

    val recientes = (tocadas + canciones)
        .distinctBy { it.id }
        .filter { it.id != principal.id }
        .take(maxRecientes)
        .map(::conMarca)

    return Inicio(conMarca(principal), modo, yaTocada, recientes)
}
