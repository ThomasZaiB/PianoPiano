package com.tuapp.piano.presentation.viewmodel

import com.tuapp.piano.domain.musica.ErrorMidi
import com.tuapp.piano.domain.usecase.ImportarMidiUseCase

/** Resultado de importar un .mid, para que la pantalla escriba el aviso. Lo usan inicio y biblioteca. */
sealed interface ResultadoImportacionMidi {
    data class Importada(val titulo: String, val notas: Int, val descartadas: Int) : ResultadoImportacionMidi

    /** [detalle]: explicación de LectorMidi si el archivo no es válido; si no, el error técnico. */
    data class Fallida(val detalle: String?, val archivoInvalido: Boolean) : ResultadoImportacionMidi
}

internal suspend fun importarMidiConResultado(importar: ImportarMidiUseCase, bytes: ByteArray, nombreArchivo: String?): ResultadoImportacionMidi =
    runCatching { importar(bytes, nombreArchivo) }.fold(
        onSuccess = { r -> ResultadoImportacionMidi.Importada(r.titulo, r.notas, r.descartadas) },
        onFailure = { e -> ResultadoImportacionMidi.Fallida(e.message, archivoInvalido = e is ErrorMidi) },
    )
