package com.tuapp.piano.domain.model

/** Por qué el motor no suena. La pantalla elige el texto (strings.xml), no el dominio. */
enum class ErrorMotor {
    /** No hay .sf2 en assets o no es válido. */
    SOUNDFONT,

    /** El sistema no dio un stream de audio. */
    STREAM,

    /** El stream se cayó (p. ej. al cambiar de auriculares) y aún no se pudo reabrir. */
    SALIDA_PERDIDA,
}
