package com.tuapp.piano.presentation.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI

/**
 * Movimiento de la app: resortes en vez de duraciones fijas. Un resorte arranca desde el
 * valor que se ve en pantalla, así que una animación se puede interrumpir o invertir a
 * mitad de camino sin saltos.
 *
 * Se piensa con los dos parámetros que usa Apple:
 *  - amortiguación: 1 = llega sin pasarse; menos de 1 = rebota.
 *  - respuesta (s): cuánto tarda en llegar, más o menos. No es una duración exacta.
 */
object Movimiento {
    /** Por defecto: sin rebote. El rebote se reserva para gestos que traen impulso. */
    fun <T> resorte(respuestaSegundos: Float = 0.4f, amortiguacion: Float = 1f): SpringSpec<T> =
        spring(dampingRatio = amortiguacion, stiffness = rigidez(respuestaSegundos))

    /** Hundirse al tocar: rápido, para que se sienta en el mismo instante del toque. */
    fun <T> pulsar(): SpringSpec<T> = resorte(respuestaSegundos = 0.15f)

    /** Volver tras soltar: algo más suave que el hundimiento. */
    fun <T> soltar(): SpringSpec<T> = resorte(respuestaSegundos = 0.35f)

    /** Entrada de elementos al abrir una pantalla. */
    fun <T> entrar(): SpringSpec<T> = resorte(respuestaSegundos = 0.5f)

    /** Separación entre elementos de una entrada escalonada. */
    const val ESCALON_MS = 55L

    /** Con masa 1, respuesta = 2π / √rigidez. */
    private fun rigidez(respuestaSegundos: Float): Float {
        val w = (2 * PI / respuestaSegundos).toFloat()
        return w * w
    }
}

/**
 * true si el usuario quitó las animaciones en las opciones de Android. Entonces no hay
 * desplazamientos ni escalas: los elementos aparecen directamente en su sitio.
 */
@Composable
fun movimientoReducido(): Boolean {
    val contexto = LocalContext.current
    return remember(contexto) {
        Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
