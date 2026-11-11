package com.tuapp.piano.presentation.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.tuapp.piano.R

/** "1 nota" / "12 notas": para insertar en textos que llevan una cantidad de notas. */
@Composable
fun textoNotas(cantidad: Int): String = pluralStringResource(R.plurals.cantidad_notas, cantidad, cantidad)

@Composable
fun textoPasos(cantidad: Int): String = pluralStringResource(R.plurals.cantidad_pasos, cantidad, cantidad)
