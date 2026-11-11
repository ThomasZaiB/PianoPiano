package com.tuapp.piano.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colores con significado. Las pantallas usan SOLO estos nombres: si mañana cambia
 * la paleta, se toca [Paleta] y no cada pantalla.
 */
object PianoColors {
    // Fondos y superficies (de atrás hacia adelante)
    val Fondo = Color(Paleta.FONDO)
    val Superficie = Color(Paleta.SUPERFICIE)
    val SuperficieAlta = Color(Paleta.SUPERFICIE_ALTA)
    val Borde = Color(Paleta.BLANCO).copy(alpha = 0.12f)

    // Texto e iconos
    val Texto = Color(Paleta.BLANCO)
    val TextoSecundario = Color(Paleta.GRIS_CLARO)
    val TextoDeshabilitado = Color(Paleta.GRIS_CLARO).copy(alpha = 0.5f)
    /** Texto/icono encima de un relleno de acento (botones cian, magenta, rojo). */
    val SobreAcento = Color(Paleta.NEGRO)

    // Teclado
    val TeclaBlanca = Color(Paleta.BLANCO)
    val TeclaNegra = Color(Paleta.SUPERFICIE)

    // Acentos: identidad de la app y color de cada mano
    val ManoDerecha = Color(Paleta.CIAN) // cian
    val ManoIzquierda = Color(Paleta.MAGENTA) // magenta

    // Estados
    val Grabar = Color(Paleta.ROJO)
    val Exito = Color(Paleta.VERDE)
    val Aviso = Color(Paleta.AMBAR)
    /** Errores y acciones destructivas (borrar, descartar). */
    val Error = Color(Paleta.ROJO)
}

private val Esquema = darkColorScheme(
    primary = PianoColors.ManoDerecha,
    onPrimary = PianoColors.SobreAcento,
    primaryContainer = PianoColors.ManoDerecha.copy(alpha = 0.16f),
    onPrimaryContainer = PianoColors.ManoDerecha,
    secondary = PianoColors.ManoIzquierda,
    onSecondary = PianoColors.SobreAcento,
    secondaryContainer = PianoColors.SuperficieAlta,
    onSecondaryContainer = PianoColors.Texto,
    tertiary = PianoColors.Exito,
    onTertiary = PianoColors.SobreAcento,
    error = PianoColors.Error,
    onError = PianoColors.SobreAcento,
    background = PianoColors.Fondo,
    onBackground = PianoColors.Texto,
    surface = PianoColors.Superficie,
    onSurface = PianoColors.Texto,
    surfaceVariant = PianoColors.SuperficieAlta,
    onSurfaceVariant = PianoColors.TextoSecundario,
    outline = PianoColors.TextoSecundario.copy(alpha = 0.5f),
    outlineVariant = PianoColors.Borde,
    // Sin esto, diálogos, menús y campos de texto usan los grises violáceos por defecto de Material 3.
    surfaceDim = PianoColors.Fondo,
    surfaceBright = Color(Paleta.SUPERFICIE_MAXIMA),
    surfaceContainerLowest = Color(Paleta.NEGRO_PROFUNDO),
    surfaceContainerLow = PianoColors.Fondo,
    surfaceContainer = PianoColors.Superficie,
    surfaceContainerHigh = PianoColors.SuperficieAlta,
    surfaceContainerHighest = Color(Paleta.SUPERFICIE_MAXIMA),
    inverseSurface = PianoColors.Texto,
    inverseOnSurface = PianoColors.Fondo,
)

@Composable
fun PianoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Esquema,
        typography = PianoTipografia,
        shapes = PianoFormas,
        content = content,
    )
}
