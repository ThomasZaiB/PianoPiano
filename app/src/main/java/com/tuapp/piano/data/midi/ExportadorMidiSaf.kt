package com.tuapp.piano.data.midi

import android.content.Context
import android.net.Uri
import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.repository.ExportadorMidi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Escribe el .mid en la URI que devolvió ActivityResultContracts.CreateDocument (SAF).
 * No necesita permisos de almacenamiento: el usuario ya eligió el destino.
 */
class ExportadorMidiSaf @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExportadorMidi {

    override suspend fun exportar(cancion: Cancion, destinoUri: String) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(destinoUri)
        // "wt" = escribir y truncar, por si el usuario eligió sobrescribir un archivo existente.
        val salida = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("No se pudo abrir el archivo de destino")
        salida.buffered().use { MidiFileWriter.escribir(cancion, it) }
    }
}
