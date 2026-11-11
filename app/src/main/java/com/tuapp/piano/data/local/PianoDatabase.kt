package com.tuapp.piano.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tuapp.piano.data.demo.CancionesDemo
import com.tuapp.piano.data.local.dao.CancionDao
import com.tuapp.piano.data.local.entity.CancionEntity
import com.tuapp.piano.data.local.entity.NotaEntity
import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.Compas
import com.tuapp.piano.domain.musica.CancionDeTexto
import com.tuapp.piano.domain.musica.FiguraPaso
import com.tuapp.piano.domain.usecase.ImportarCancionUseCase

@Database(
    entities = [CancionEntity::class, NotaEntity::class],
    version = 1,
    exportSchema = true, // esquema en app/schemas/ para escribir migraciones en el futuro
)
abstract class PianoDatabase : RoomDatabase() {
    abstract fun cancionDao(): CancionDao

    companion object {
        const val NOMBRE = "piano.db"
    }
}

/**
 * Inserta las canciones incluidas con la app: la demo y cada archivo .txt de la carpeta assets/canciones.
 * (Ojo: en Kotlin no se puede escribir barra-asterisco dentro de un comentario,
 * porque abre un comentario anidado.)
 *
 * Se ejecuta al ABRIR la base (no solo al crearla), así una actualización de la app
 * que trae canciones nuevas las añade también a quien ya tenía la base creada.
 * Cada canción se inserta una sola vez: la clave queda anotada en SharedPreferences,
 * de modo que si el usuario la borra no reaparece.
 */
class SemillasCallback(private val context: Context) : RoomDatabase.Callback() {

    private val prefs = context.getSharedPreferences("semillas", Context.MODE_PRIVATE)

    override fun onOpen(db: SupportSQLiteDatabase) {
        val insertadas = prefs.getStringSet(CLAVE, null)?.toMutableSet() ?: inicializar(db)
        var cambios = false

        for ((clave, crear) in catalogo()) {
            if (clave in insertadas) continue
            val cancion = runCatching { crear() }
                .onFailure { Log.e(TAG, "Canción incluida inválida: $clave", it) }
                .getOrNull() ?: continue
            // Una semilla que no se puede insertar no debe impedir abrir la base: una excepción
            // que escapa de onOpen deja la base sin abrir en cada arranque, para siempre.
            // Se registra y se sigue; no se marca como insertada, así que se reintenta.
            try {
                db.beginTransaction()
                try {
                    insertar(db, cancion)
                    db.setTransactionSuccessful()
                    insertadas += clave
                    cambios = true
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo insertar la canción incluida $clave", e)
            }
        }
        if (cambios) prefs.edit().putStringSet(CLAVE, insertadas).apply()
    }

    /**
     * Primera vez con este sistema. Si la base ya existía (versión anterior de la app,
     * que insertaba la demo al crearla), la demo se da por insertada para no duplicarla.
     */
    private fun inicializar(db: SupportSQLiteDatabase): MutableSet<String> {
        val yaHabiaDatos = db.query("SELECT COUNT(*) FROM canciones").use { c -> c.moveToFirst() && c.getInt(0) > 0 }
        return if (yaHabiaDatos) mutableSetOf(CLAVE_DEMO) else mutableSetOf()
    }

    private fun catalogo(): List<Pair<String, () -> Cancion>> {
        val lista = ArrayList<Pair<String, () -> Cancion>>()
        lista += CLAVE_DEMO to { CancionesDemo.himnoDeLaAlegria().copy(fechaCreacion = System.currentTimeMillis()) }
        val archivos = runCatching { context.assets.list(CARPETA) }.getOrNull().orEmpty()
        archivos.filter { it.endsWith(".txt", ignoreCase = true) }.sorted().forEachIndexed { i, archivo ->
            lista += "asset:$archivo" to {
                val texto = context.assets.open("$CARPETA/$archivo").bufferedReader().use { it.readText() }
                val c = CancionDeTexto.leer(texto)
                ImportarCancionUseCase.construir(
                    titulo = c.titulo ?: archivo.substringBeforeLast('.'),
                    notacion = c.notacion,
                    bpm = c.bpm ?: 100,
                    compas = runCatching { Compas(c.numerador ?: 4, c.denominador ?: 4) }.getOrDefault(Compas()),
                    figura = c.figura ?: FiguraPaso.CORCHEA,
                    // Orden estable en la biblioteca (más reciente primero): la demo arriba.
                    ahoraMillis = System.currentTimeMillis() - (i + 1) * 1000L,
                )
            }
        }
        return lista
    }

    private fun insertar(db: SupportSQLiteDatabase, cancion: Cancion) {
        val e = cancion.aEntidad()
        val cancionId = db.insert(
            "canciones",
            SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("titulo", e.titulo)
                put("bpm", e.bpm)
                put("compas", e.compas)
                put("cuantizacion", e.cuantizacion)
                put("cuantizacionHumana", if (e.cuantizacionHumana) 1 else 0)
                put("fechaCreacion", e.fechaCreacion)
                put("origen", e.origen)
            },
        )
        cancion.notas.forEach { n ->
            db.insert(
                "notas",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("cancionId", cancionId)
                    put("tickInicio", n.tickInicio)
                    put("duracionTicks", n.duracionTicks)
                    put("notaMidi", n.nota)
                    put("velocidad", n.velocidad)
                    put("mano", n.mano.name)
                },
            )
        }
    }

    private companion object {
        const val TAG = "SemillasCallback"
        const val CLAVE = "insertadas"
        const val CLAVE_DEMO = "demo:himno"
        const val CARPETA = "canciones"
    }
}
