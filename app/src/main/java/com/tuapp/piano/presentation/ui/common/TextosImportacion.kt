package com.tuapp.piano.presentation.ui.common

import android.content.Context
import com.tuapp.piano.R
import com.tuapp.piano.presentation.viewmodel.ResultadoImportacionMidi

/** Aviso tras importar un .mid. Con Context (no Composable): se usa al recoger eventos. */
fun textoImportacionMidi(contexto: Context, r: ResultadoImportacionMidi): String = when (r) {
    is ResultadoImportacionMidi.Importada -> {
        val notas = contexto.resources.getQuantityString(R.plurals.cantidad_notas, r.notas, r.notas)
        val base = contexto.getString(R.string.midi_importada, r.titulo, notas)
        if (r.descartadas > 0) base + contexto.getString(R.string.midi_importada_descartadas, r.descartadas) else base
    }
    is ResultadoImportacionMidi.Fallida ->
        if (r.archivoInvalido) r.detalle.orEmpty() else contexto.getString(R.string.midi_error_importar, r.detalle.orEmpty())
}
