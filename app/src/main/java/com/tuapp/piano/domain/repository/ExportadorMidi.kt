package com.tuapp.piano.domain.repository

import com.tuapp.piano.domain.model.Cancion

/**
 * Escribe una canción como Standard MIDI File en un destino elegido por el usuario.
 * El destino es la URI (como texto) devuelta por el Storage Access Framework;
 * el dominio no necesita saber qué es una Uri de Android.
 */
interface ExportadorMidi {
    suspend fun exportar(cancion: Cancion, destinoUri: String)
}
