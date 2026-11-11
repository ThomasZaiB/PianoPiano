package com.tuapp.piano.presentation.viewmodel

import com.tuapp.piano.domain.musica.ErrorMidi
import com.tuapp.piano.domain.usecase.ImportarMidiUseCase

/** Importa un .mid y devuelve el aviso para el usuario (éxito o error). Lo usan inicio y biblioteca. */
internal suspend fun importarMidiConMensaje(importar: ImportarMidiUseCase, bytes: ByteArray, nombreArchivo: String?): String =
    runCatching { importar(bytes, nombreArchivo) }.fold(
        onSuccess = { r ->
            val extra = if (r.descartadas > 0) " (${r.descartadas} fuera del piano o de percusión, omitidas)" else ""
            "«${r.titulo}» importada · ${r.notas} ${if (r.notas == 1) "nota" else "notas"}$extra"
        },
        onFailure = { e -> if (e is ErrorMidi) e.message.orEmpty() else "No se pudo importar: ${e.message}" },
    )
