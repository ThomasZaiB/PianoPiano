package com.tuapp.piano.presentation.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Mientras esta pantalla está en composición, la Activity se comporta como un instrumento:
 *  - la pantalla no se apaga (en REPRODUCIR no hay toques que la mantengan despierta y
 *    onStop pausaría la canción a mitad);
 *  - barras del sistema ocultas (modo inmersivo): en horizontal devuelven alto útil al
 *    teclado, y reaparecen un momento deslizando desde el borde.
 *
 * Al salir, todo vuelve a su estado normal. Se cuentan los usuarios porque durante la
 * animación de navegación pueden convivir dos pantallas que lo piden.
 */
@Composable
fun ModoInstrumento() {
    val vista = LocalView.current
    DisposableEffect(vista) {
        val ventana = vista.context.buscarActivity()?.window
        usuarios++
        vista.keepScreenOn = true
        if (ventana != null) {
            WindowCompat.getInsetsController(ventana, vista).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            usuarios--
            vista.keepScreenOn = usuarios > 0
            if (usuarios == 0 && ventana != null) {
                WindowCompat.getInsetsController(ventana, vista).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

/** Solo se toca desde el hilo principal (composición). */
private var usuarios = 0

private tailrec fun Context.buscarActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.buscarActivity()
    else -> null
}
