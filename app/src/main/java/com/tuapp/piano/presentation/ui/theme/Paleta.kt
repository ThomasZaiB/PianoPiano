package com.tuapp.piano.presentation.ui.theme

/**
 * Valores ARGB crudos de la app: la única fuente de verdad de color.
 *
 * Son `Long` constantes (no `Color` de Compose) para que también los use el hilo de
 * render de PianoSurfaceView sin depender de Compose: `Color(Paleta.X)` en Compose,
 * `Paleta.X.toInt()` en Canvas. Las pantallas NO usan esto directamente, sino los
 * nombres con significado de [PianoColors].
 */
object Paleta {
    // ---- Neutros, de más oscuro a más claro ----
    const val NEGRO_PROFUNDO = 0xFF0A0A0AL
    const val FONDO = 0xFF121212L
    const val SUPERFICIE = 0xFF1E1E1EL
    const val SUPERFICIE_ALTA = 0xFF282828L
    const val SUPERFICIE_MAXIMA = 0xFF323232L
    const val GRIS_OSCURO = 0xFF5A5A5AL
    const val GRIS_MEDIO = 0xFF757575L
    const val GRIS_CLARO = 0xFF9E9E9EL
    const val GRIS_SEPARADOR = 0xFFBDBDBDL
    const val BLANCO = 0xFFFFFFFFL
    const val NEGRO = 0xFF000000L

    // ---- Acentos: cada mano tiene su color, en dos tonos (tecla blanca / negra) ----
    const val CIAN = 0xFF00E5FFL
    const val CIAN_OSCURO = 0xFF00A5B8L
    const val MAGENTA = 0xFFFF4081L
    const val MAGENTA_OSCURO = 0xFFC2185BL

    // ---- Estados ----
    const val ROJO = 0xFFFF5252L
    const val VERDE = 0xFF69F0AEL
    const val AMBAR = 0xFFFFD740L

    /** Mismo color con otra opacidad (0–255). Para usar fuera de Compose. */
    fun conAlfa(argb: Long, alfa: Int): Int = ((alfa and 0xFF) shl 24) or (argb.toInt() and 0x00FFFFFF)
}
