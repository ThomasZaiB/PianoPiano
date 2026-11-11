package com.tuapp.piano.presentation.ui.common

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Algunos gestores de archivos marcan los .mid como binario genérico: se aceptan y el lector valida. */
private val TIPOS_MIDI = arrayOf("audio/midi", "audio/x-midi", "audio/mid", "application/x-midi", "application/octet-stream")

/** Un .mid de piano ocupa pocos KB; el límite evita cargar en memoria un archivo equivocado enorme. */
private const val TAMANO_MAXIMO_MIDI = 4 * 1024 * 1024

/**
 * Selector de archivos para importar un .mid. Devuelve la función que lo abre.
 * El archivo se lee aquí (hace falta el ContentResolver, que no debe vivir en un
 * ViewModel) y se entrega en [onLeido] con su nombre; si no se puede leer, [onError].
 */
@Composable
fun rememberSelectorMidi(onLeido: (bytes: ByteArray, nombre: String?) -> Unit, onError: () -> Unit): () -> Unit {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val alLeer by rememberUpdatedState(onLeido)
    val alFallar by rememberUpdatedState(onError)
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val leido = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = contexto.contentResolver
                    val nombre = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    val bytes = resolver.openInputStream(uri)?.use(::leerConLimite) ?: ByteArray(0)
                    bytes to nombre
                }
            }
            leido.onSuccess { (bytes, nombre) -> alLeer(bytes, nombre) }.onFailure { alFallar() }
        }
    }
    return { selector.launch(TIPOS_MIDI) }
}

/** Lee todo el flujo, o falla si pasa de [TAMANO_MAXIMO_MIDI] (sin cargar el resto en memoria). */
private fun leerConLimite(entrada: InputStream): ByteArray {
    val salida = ByteArrayOutputStream()
    val bufer = ByteArray(8 * 1024)
    while (true) {
        val n = entrada.read(bufer)
        if (n < 0) return salida.toByteArray()
        salida.write(bufer, 0, n)
        require(salida.size() <= TAMANO_MAXIMO_MIDI) { "Archivo demasiado grande" }
    }
}
