package com.tuapp.piano.data.repository

import com.tuapp.piano.data.local.aDominio
import com.tuapp.piano.data.local.aEntidad
import com.tuapp.piano.data.local.dao.CancionDao
import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.Nota
import com.tuapp.piano.domain.model.OrigenCancion
import com.tuapp.piano.domain.model.ResumenCancion
import com.tuapp.piano.domain.repository.CancionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Room ya ejecuta las funciones suspend fuera del hilo principal. */
@Singleton
class CancionRepositoryImpl @Inject constructor(
    private val dao: CancionDao,
) : CancionRepository {

    override fun observarResumenes(): Flow<List<ResumenCancion>> =
        dao.observarResumenes().map { filas -> filas.map { it.aDominio() } }

    override suspend fun obtener(id: Long): Cancion? = dao.obtenerConNotas(id)?.aDominio()

    override suspend fun crear(cancion: Cancion): Long =
        dao.insertarCancionConNotas(
            cancion.copy(id = 0).aEntidad(),
            cancion.notas.map { it.aEntidad(cancionId = 0) },
        )

    override suspend fun agregarNotas(cancionId: Long, notas: List<Nota>, nuevoTitulo: String?) =
        dao.agregarNotas(cancionId, notas.map { it.aEntidad(cancionId) }, nuevoTitulo)

    override suspend fun borrar(id: Long) = dao.borrar(id)

    override suspend fun marcarExportada(id: Long) = dao.cambiarOrigen(id, OrigenCancion.EXPORTADA.name)
}
