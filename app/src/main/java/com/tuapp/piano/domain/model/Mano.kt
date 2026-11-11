package com.tuapp.piano.domain.model

/** Cada mano va en su propio canal MIDI: permite colorear la cascada y exportar pistas separadas. */
enum class Mano(val canal: Int) {
    DERECHA(0),
    IZQUIERDA(1),
}
