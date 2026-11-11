package com.tuapp.piano.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Escala tipográfica. Fuente del sistema; solo se ajustan tamaños y pesos.
 * Uso en la app:
 *  - displaySmall   título de la portada
 *  - headlineMedium número grande (BPM en la configuración)
 *  - headlineSmall  número mediano (BPM al importar)
 *  - titleMedium    título de tarjeta / fila
 *  - titleSmall     título en la barra del piano
 *  - labelLarge     rótulo de sección y texto de botón
 *  - labelMedium    datos compactos de la barra del piano (BPM, estado)
 *  - labelSmall     etiquetas y chips
 */
val PianoTipografia = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontSize = 12.sp),
    )
}

/** Texto de ancho fijo: editor de notación y contadores que no deben "bailar". */
val EstiloMono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
