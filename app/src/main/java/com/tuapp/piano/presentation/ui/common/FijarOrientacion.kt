package com.tuapp.piano.presentation.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

enum class Orientacion(val valor: Int) {
    VERTICAL(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    HORIZONTAL(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),

    /** Gira con el celular, respetando el bloqueo de rotación del usuario. */
    LIBRE(ActivityInfo.SCREEN_ORIENTATION_USER),
}

/**
 * Fija la orientación mientras esta pantalla está en composición.
 * Cada destino del NavGraph declara la suya; al volver atrás, la pantalla anterior
 * vuelve a entrar en composición y la restaura. No hace falta deshacer nada al salir.
 *
 * La Activity declara configChanges en el manifiesto, así que girar NO la recrea:
 * el stream de audio y el SurfaceView sobreviven al cambio.
 */
@Composable
fun FijarOrientacion(orientacion: Orientacion) {
    val activity = LocalContext.current.buscarActivity()
    DisposableEffect(orientacion, activity) {
        if (activity != null && activity.requestedOrientation != orientacion.valor) {
            activity.requestedOrientation = orientacion.valor
        }
        onDispose { }
    }
}

private tailrec fun Context.buscarActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.buscarActivity()
    else -> null
}
