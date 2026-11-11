package com.tuapp.piano.data.ajustes

import android.content.Context
import androidx.core.content.edit
import com.tuapp.piano.domain.model.Ajustes
import com.tuapp.piano.domain.model.EtiquetasTeclado
import com.tuapp.piano.domain.model.Notacion
import com.tuapp.piano.domain.model.VelocidadCascada
import com.tuapp.piano.domain.repository.AjustesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ajustes en SharedPreferences: son pocos valores simples y se leen una sola vez al
 * arrancar. `apply()` escribe en disco en segundo plano, así que actualizar no bloquea.
 */
@Singleton
class AjustesRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : AjustesRepository {

    private val prefs = context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    private val _ajustes = MutableStateFlow(leer())
    override val ajustes: StateFlow<Ajustes> = _ajustes.asStateFlow()

    override fun actualizar(cambio: (Ajustes) -> Ajustes) {
        _ajustes.update { actual -> normalizar(cambio(actual)).also(::guardar) }
    }

    private fun leer(): Ajustes {
        val d = Ajustes()
        return normalizar(
            Ajustes(
                notacion = enumDe(prefs.getString(K_NOTACION, null), d.notacion),
                etiquetas = enumDe(prefs.getString(K_ETIQUETAS, null), d.etiquetas),
                notaDivision = prefs.getInt(K_DIVISION, d.notaDivision),
                dinamicaTactil = prefs.getBoolean(K_DINAMICA, d.dinamicaTactil),
                velocidadCascada = enumDe(prefs.getString(K_CASCADA, null), d.velocidadCascada),
                teclasVisibles = prefs.getInt(K_ZOOM, d.teclasVisibles),
            ),
        )
    }

    private fun guardar(a: Ajustes) = prefs.edit {
        putString(K_NOTACION, a.notacion.name)
        putString(K_ETIQUETAS, a.etiquetas.name)
        putInt(K_DIVISION, a.notaDivision)
        putBoolean(K_DINAMICA, a.dinamicaTactil)
        putString(K_CASCADA, a.velocidadCascada.name)
        putInt(K_ZOOM, a.teclasVisibles)
    }

    private fun normalizar(a: Ajustes) = a.copy(
        notaDivision = a.notaDivision.coerceIn(Ajustes.RANGO_DIVISION),
        teclasVisibles = a.teclasVisibles.coerceIn(Ajustes.RANGO_TECLAS_VISIBLES),
    )

    private companion object {
        const val ARCHIVO = "ajustes"
        const val K_NOTACION = "notacion"
        const val K_ETIQUETAS = "etiquetas"
        const val K_DIVISION = "nota_division"
        const val K_DINAMICA = "dinamica_tactil"
        const val K_CASCADA = "velocidad_cascada"
        const val K_ZOOM = "teclas_visibles"

        /** Un valor guardado que ya no existe (enum renombrado) vuelve al valor por defecto. */
        inline fun <reified T : Enum<T>> enumDe(nombre: String?, porDefecto: T): T =
            enumValues<T>().firstOrNull { it.name == nombre } ?: porDefecto
    }
}
