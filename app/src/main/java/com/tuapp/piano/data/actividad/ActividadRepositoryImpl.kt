package com.tuapp.piano.data.actividad

import android.content.Context
import androidx.core.content.edit
import com.tuapp.piano.domain.model.Actividad
import com.tuapp.piano.domain.model.ModoPiano
import com.tuapp.piano.domain.repository.ActividadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Actividad en SharedPreferences, como los ajustes: pocos datos y se leen una vez.
 * Formato: recientes = "12,5,9"; mejores = "12:87,5:100". Un valor ilegible se ignora.
 */
@Singleton
class ActividadRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : ActividadRepository {

    private val prefs = context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    private val _actividad = MutableStateFlow(leer())
    override val actividad: StateFlow<Actividad> = _actividad.asStateFlow()

    override fun registrarApertura(cancionId: Long, modo: ModoPiano) = actualizar { it.conApertura(cancionId, modo) }

    override fun registrarResultado(cancionId: Long, precision: Int) =
        actualizar { it.conResultado(cancionId, precision.coerceIn(0, 100)) }

    private fun actualizar(cambio: (Actividad) -> Actividad) {
        _actividad.update { cambio(it).also(::guardar) }
    }

    private fun leer() = Actividad(
        recientes = prefs.getString(K_RECIENTES, null).orEmpty()
            .split(',').mapNotNull { it.trim().toLongOrNull() }.distinct().take(Actividad.MAX_RECIENTES),
        ultimoModo = ModoPiano.entries.firstOrNull { it.name == prefs.getString(K_MODO, null) },
        mejores = prefs.getString(K_MEJORES, null).orEmpty()
            .split(',').mapNotNull { par ->
                val (id, prec) = par.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val clave = id.toLongOrNull() ?: return@mapNotNull null
                val valor = prec.toIntOrNull()?.coerceIn(0, 100) ?: return@mapNotNull null
                clave to valor
            }.toMap(),
    )

    private fun guardar(a: Actividad) = prefs.edit {
        putString(K_RECIENTES, a.recientes.joinToString(","))
        putString(K_MODO, a.ultimoModo?.name)
        putString(K_MEJORES, a.mejores.entries.joinToString(",") { "${it.key}:${it.value}" })
    }

    private companion object {
        const val ARCHIVO = "actividad"
        const val K_RECIENTES = "recientes"
        const val K_MODO = "ultimo_modo"
        const val K_MEJORES = "mejores"
    }
}
