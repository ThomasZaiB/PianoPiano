package com.tuapp.piano.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Radios de esquina:
 *  - extraSmall (6)  chips y etiquetas
 *  - small (8)       resaltados pequeños
 *  - medium (12)     tarjetas de contenido, cajas de icono
 *  - large (16)      tarjetas de la biblioteca
 *  - extraLarge (20) tarjetas grandes de la portada
 */
val PianoFormas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
