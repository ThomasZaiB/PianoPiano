package com.tuapp.piano.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tuapp.piano.domain.model.Cuantizacion
import com.tuapp.piano.domain.model.ModoPiano
import com.tuapp.piano.presentation.ui.PianoScreen
import com.tuapp.piano.presentation.ui.ajustes.AjustesScreen
import com.tuapp.piano.presentation.ui.common.FijarOrientacion
import com.tuapp.piano.presentation.ui.common.Orientacion
import com.tuapp.piano.presentation.ui.home.HomeScreen
import com.tuapp.piano.presentation.ui.library.ImportarCancionScreen
import com.tuapp.piano.presentation.ui.library.LibraryScreen
import com.tuapp.piano.presentation.ui.setup.SongSetupScreen

private const val DURACION_TRANSICION_MS = 280

private fun <T> animacion() = tween<T>(DURACION_TRANSICION_MS, easing = FastOutSlowInEasing)

/**
 * Entrar o salir del piano gira la pantalla: ahí solo hay fundido, porque un
 * deslizamiento a mitad de la rotación se ve roto.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.involucraPiano() =
    initialState.destination.route == Rutas.PIANO || targetState.destination.route == Rutas.PIANO

@Composable
fun PianoNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Rutas.HOME,
        enterTransition = { if (involucraPiano()) fadeIn(animacion()) else slideIntoContainer(SlideDirection.Start, animacion()) + fadeIn(animacion()) },
        exitTransition = { if (involucraPiano()) fadeOut(animacion()) else slideOutOfContainer(SlideDirection.Start, animacion()) { it / 4 } + fadeOut(animacion()) },
        popEnterTransition = { if (involucraPiano()) fadeIn(animacion()) else slideIntoContainer(SlideDirection.End, animacion()) { it / 4 } + fadeIn(animacion()) },
        popExitTransition = { if (involucraPiano()) fadeOut(animacion()) else slideOutOfContainer(SlideDirection.End, animacion()) + fadeOut(animacion()) },
    ) {

        composable(Rutas.HOME) {
            // El inicio gira con el celular (tiene diseño vertical y acostado).
            FijarOrientacion(Orientacion.LIBRE)
            ConMargenesDelSistema {
                HomeScreen(
                    onCrearCancion = { navController.navegar(Rutas.SONG_SETUP) },
                    onBiblioteca = { navController.navegar(Rutas.LIBRARY) },
                    onTocarLibre = { navController.navegar(Rutas.piano(ModoPiano.LIBRE)) },
                    onAjustes = { navController.navegar(Rutas.AJUSTES) },
                    onImportarTexto = { navController.navegar(Rutas.IMPORTAR) },
                    onAbrir = { modo, id -> navController.navegar(Rutas.piano(modo, id)) },
                )
            }
        }

        composable(Rutas.AJUSTES) {
            FijarOrientacion(Orientacion.VERTICAL)
            ConMargenesDelSistema { AjustesScreen(onVolver = { navController.volver() }) }
        }

        composable(Rutas.LIBRARY) {
            FijarOrientacion(Orientacion.VERTICAL)
            ConMargenesDelSistema {
                LibraryScreen(
                    onVolver = { navController.volver() },
                    onNuevaCancion = { navController.navegar(Rutas.SONG_SETUP) },
                    onImportar = { navController.navegar(Rutas.IMPORTAR) },
                    onAbrir = { modo, id -> navController.navegar(Rutas.piano(modo, id)) },
                )
            }
        }

        composable(Rutas.IMPORTAR) {
            FijarOrientacion(Orientacion.VERTICAL)
            ConMargenesDelSistema {
                ImportarCancionScreen(
                    onVolver = { navController.volver() },
                    onGuardada = { navController.volver() }, // vuelve a la biblioteca, que ya la muestra
                )
            }
        }

        composable(Rutas.SONG_SETUP) {
            FijarOrientacion(Orientacion.VERTICAL)
            ConMargenesDelSistema {
                SongSetupScreen(
                    onVolver = { navController.volver() },
                    onComenzar = { config -> navController.navegar(Rutas.piano(ModoPiano.GRABAR, 0L, config)) },
                )
            }
        }

        composable(
            route = Rutas.PIANO,
            arguments = listOf(
                navArgument(Rutas.ARG_MODO) { type = NavType.StringType },
                navArgument(Rutas.ARG_CANCION_ID) { type = NavType.LongType },
                navArgument(Rutas.ARG_BPM) { type = NavType.IntType; defaultValue = 100 },
                navArgument(Rutas.ARG_NUMERADOR) { type = NavType.IntType; defaultValue = 4 },
                navArgument(Rutas.ARG_DENOMINADOR) { type = NavType.IntType; defaultValue = 4 },
                navArgument(Rutas.ARG_CUANTIZACION) { type = NavType.StringType; defaultValue = Cuantizacion.SEMICORCHEA.name },
                navArgument(Rutas.ARG_HUMANA) { type = NavType.BoolType; defaultValue = true },
                navArgument(Rutas.ARG_METRONOMO) { type = NavType.BoolType; defaultValue = true },
            ),
        ) {
            FijarOrientacion(Orientacion.HORIZONTAL)
            // Inmersivo: sin barras, pero ninguna tecla ni botón debajo del notch.
            Box(Modifier.fillMaxSize().displayCutoutPadding()) {
                // PianoViewModel (hiltViewModel dentro de PianoScreen) recibe estos argumentos
                // automáticamente en su SavedStateHandle.
                PianoScreen(
                    onVolver = { navController.volver() },
                    onIrABiblioteca = {
                        navController.navegar(Rutas.LIBRARY) {
                            popUpTo(Rutas.HOME) // quita la configuración y el piano de la pila
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ConMargenesDelSistema(contenido: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) { contenido() }
}

/**
 * Única puerta de la navegación. Solo navega si la pantalla actual está RESUMED: un doble
 * toque (o un toque durante la animación de entrada) no apila dos destinos ni saca dos de la pila.
 */
private fun NavHostController.navegar(ruta: String, opciones: NavOptionsBuilder.() -> Unit = {}) {
    if (!actualActiva()) return
    navigate(ruta, opciones)
}

private fun NavHostController.volver() {
    if (!actualActiva()) return
    popBackStack()
}

private fun NavHostController.actualActiva() =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED
