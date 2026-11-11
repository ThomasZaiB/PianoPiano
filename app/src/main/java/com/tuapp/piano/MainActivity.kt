package com.tuapp.piano

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tuapp.piano.presentation.navigation.PianoNavGraph
import com.tuapp.piano.presentation.ui.theme.PianoColors
import com.tuapp.piano.presentation.ui.theme.PianoTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Única Activity. La orientación la decide cada pantalla del NavGraph y el ciclo de
 * vida del audio lo gestiona PianoScreen (solo hay stream abierto con el piano visible).
 * Los márgenes del sistema también los pone cada destino: el piano va en modo inmersivo
 * y solo respeta los recortes de pantalla; el resto, barras y teclado incluidos.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PianoTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(PianoColors.Fondo),
                ) {
                    PianoNavGraph()
                }
            }
        }
    }
}
