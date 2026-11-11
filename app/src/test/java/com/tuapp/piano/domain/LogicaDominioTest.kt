package com.tuapp.piano.domain

import com.tuapp.piano.data.demo.CancionesDemo
import com.tuapp.piano.data.midi.MidiFileWriter
import com.tuapp.piano.domain.grabacion.GrabadorNotas
import com.tuapp.piano.domain.grabacion.PreparadorGrabacion
import com.tuapp.piano.domain.model.Compas
import com.tuapp.piano.domain.model.Cuantizacion
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.NombresNotas
import com.tuapp.piano.domain.model.EtiquetasTeclado
import com.tuapp.piano.domain.model.Notacion
import com.tuapp.piano.domain.model.Nota
import com.tuapp.piano.domain.musica.Cuantizador
import com.tuapp.piano.domain.practica.EvaluadorPractica
import com.tuapp.piano.domain.practica.EvaluadorPractica.Resultado
import com.tuapp.piano.domain.practica.ManoPractica
import com.tuapp.piano.domain.practica.SeleccionPractica
import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.presentation.ui.piano.TecladoGeometria
import com.tuapp.piano.presentation.ui.practica.estrellasPara
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun n(nota: Int, ini: Long, dur: Long) = Nota(nota, ini, dur, Mano.DERECHA, 90)

class CuantizadorTest {
    @Test fun perfecta_redondeaInicioYFinALaRejilla() {
        val r = Cuantizador.cuantizar(listOf(n(60, 250, 200), n(62, 470, 230)), Cuantizacion.SEMICORCHEA, humana = false)
        assertEquals(240L, r[0].tickInicio); assertEquals(480L, r[0].tickFin)
        assertEquals(480L, r[1].tickInicio); assertEquals(720L, r[1].tickFin)
    }

    @Test fun perfecta_notaMuyCortaDuraUnaRejilla() {
        val r = Cuantizador.cuantizar(listOf(n(60, 100, 10)), Cuantizacion.SEMICORCHEA, false).single()
        assertEquals(0L, r.tickInicio); assertEquals(240L, r.duracionTicks)
    }

    @Test fun tresillos_usanRejillaDe320() {
        val r = Cuantizador.cuantizar(listOf(n(60, 330, 300)), Cuantizacion.TRESILLO, false).single()
        assertEquals(320L, r.tickInicio); assertEquals(640L, r.tickFin)
    }

    @Test fun humana_conservaParteDeLaDesviacion() {
        val r = Cuantizador.cuantizar(listOf(n(60, 260, 200)), Cuantizacion.SEMICORCHEA, humana = true).single()
        assertEquals(247L, r.tickInicio) // 240 + 20 × 0,35
    }

    @Test fun sinCuantizar_noCambiaNada() {
        val r = Cuantizador.cuantizar(listOf(n(60, 333, 77)), Cuantizacion.NINGUNA, false).single()
        assertEquals(333L, r.tickInicio); assertEquals(77L, r.duracionTicks)
    }

    @Test fun mismaTeclaEnElMismoTick_seFusiona() {
        val r = Cuantizador.cuantizar(listOf(n(60, 235, 100), n(60, 245, 400)), Cuantizacion.SEMICORCHEA, false)
        assertEquals(1, r.size)
    }

    @Test fun solapeDeLaMismaTecla_seRecorta() {
        val r = Cuantizador.cuantizar(listOf(n(60, 0, 600), n(60, 480, 240)), Cuantizacion.SEMICORCHEA, false)
        assertEquals(2, r.size); assertEquals(480L, r[0].tickFin)
    }
}

class GrabadorYPreparadorTest {
    @Test fun grabador_convierteOnOffEnNotas() {
        val g = GrabadorNotas()
        g.notaOn(60, 100, Mano.DERECHA, 1000)
        g.notaOn(48, 80, Mano.IZQUIERDA, 1010)
        g.notaOff(60, 1500)
        assertEquals(2, g.cantidad)
        g.cerrarTodas(2000)
        val notas = g.notas()
        assertEquals(listOf(60, 48), notas.map { it.nota })
        assertEquals(990L, notas[1].duracionTicks)
    }

    @Test fun grabador_ignoraNotasFueraDelPiano() {
        val g = GrabadorNotas()
        g.notaOn(10, 100, Mano.DERECHA, 0)
        assertTrue(g.estaVacio)
    }

    @Test fun cancionNueva_quitaElSilencioInicialDejandoUnCompas() {
        val r = PreparadorGrabacion.preparar(listOf(n(60, 3 * 3840 + 965, 400)), Compas(4, 4), Cuantizacion.SEMICORCHEA, false, esCancionNueva = true)
        assertEquals(3840L + 960, r.single().tickInicio)
    }

    @Test fun overdub_noDesplazaLasNotas() {
        val r = PreparadorGrabacion.preparar(listOf(n(60, 3 * 3840 + 965, 400)), Compas(4, 4), Cuantizacion.SEMICORCHEA, false, esCancionNueva = false)
        assertEquals(3 * 3840L + 960, r.single().tickInicio)
    }
}

class EvaluadorPracticaTest {
    private val notas = listOf(n(60, 3840, 400), n(64, 3850, 400), n(62, 4800, 400))

    @Test fun agrupaAcordes() {
        val e = EvaluadorPractica(notas, 120)
        assertEquals(2, e.grupos.size)
        assertEquals(setOf(60, 64), e.grupos[0].notas)
    }

    @Test fun pausaSoloPasadaLaTolerancia() {
        val e = EvaluadorPractica(notas, 120)
        assertFalse(e.debePausar(3900.0))
        assertTrue(e.debePausar(3960.0))
    }

    @Test fun flujoCompleto() {
        val e = EvaluadorPractica(notas, 120)
        assertEquals(Resultado.ERROR, e.alPulsar(60, 1000.0)) // demasiado pronto
        assertEquals(Resultado.ERROR, e.alPulsar(61, 3840.0)) // tecla equivocada
        e.marcarEspera()
        assertEquals(Resultado.ACIERTO, e.alPulsar(60, 3840.0))
        assertEquals(Resultado.IGNORADA, e.alPulsar(60, 3840.0))
        assertEquals(Resultado.GRUPO_COMPLETO, e.alPulsar(64, 3840.0))
        assertFalse(e.esperando)
        assertEquals(Resultado.GRUPO_COMPLETO, e.alPulsar(62, 4790.0))
        assertTrue(e.terminado)
        assertEquals(3, e.aciertos); assertEquals(2, e.errores)
    }

    @Test fun rachaSeCortaConUnErrorYGuardaLaMejor() {
        val e = EvaluadorPractica(notas, 120)
        e.marcarEspera()
        e.alPulsar(60, 3840.0)
        e.alPulsar(64, 3840.0)
        assertEquals(2, e.racha)
        e.alPulsar(61, 4800.0) // error
        assertEquals(0, e.racha)
        e.alPulsar(62, 4800.0)
        assertEquals(1, e.racha)
        assertEquals(2, e.mejorRacha)
        e.reiniciar()
        assertEquals(0, e.racha); assertEquals(0, e.mejorRacha)
    }

    @Test fun nombresDeNotas() {
        assertEquals("Do4", NombresNotas.nombre(60))
        assertEquals("La0", NombresNotas.nombre(21))
        assertEquals("Do#4", NombresNotas.nombre(61))
    }

    @Test fun nombresDeNotasEnIngles() {
        assertEquals("C4", NombresNotas.nombre(60, Notacion.INGLESA))
        assertEquals("A0", NombresNotas.nombre(21, Notacion.INGLESA))
        assertEquals("C4 · E4", NombresNotas.lista(listOf(64, 60), Notacion.INGLESA))
        assertEquals("Sol", NombresNotas.sinOctava(67))
    }
}

class MidiFileWriterTest {
    @Test fun demo_generaSmfTipo1ConTresPistas() {
        val bytes = MidiFileWriter.aBytes(CancionesDemo.himnoDeLaAlegria())
        assertEquals("MThd", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(1, (bytes[8].toInt() shl 8) or bytes[9].toInt())          // formato 1
        assertEquals(3, (bytes[10].toInt() shl 8) or bytes[11].toInt())        // 3 pistas
        assertEquals(960, ((bytes[12].toInt() and 0xFF) shl 8) or (bytes[13].toInt() and 0xFF)) // PPQ
        assertEquals(3, Regex("MTrk").findAll(String(bytes, Charsets.ISO_8859_1)).count())
    }
}

class NotacionTecladoTest {
    @Test fun leePasosAcordesYSilencios() {
        val p = com.tuapp.piano.domain.musica.NotacionTeclado.pasos("C4 [E4G4] - B2")
        assertEquals(listOf(listOf(60), listOf(64, 67), emptyList(), listOf(47)), p)
    }

    @Test fun acompanamientoVaALaManoIzquierda() {
        val n = com.tuapp.piano.domain.musica.NotacionTeclado.parsear("[A3C4E4A4]", 480)
        assertEquals(Mano.IZQUIERDA, n.first { it.nota == 60 }.mano)
        assertEquals(Mano.DERECHA, n.first { it.nota == 69 }.mano)
    }

    @Test fun silencioCortaElLegato() {
        val n = com.tuapp.piano.domain.musica.NotacionTeclado.parsear("[A3A4] B4 - C5", 480)
        assertEquals(2 * 480L - 20, n.first { it.nota == 57 }.duracionTicks)
    }

    @Test(expected = com.tuapp.piano.domain.musica.ErrorNotacion::class)
    fun tokenInvalidoLanzaError() {
        com.tuapp.piano.domain.musica.NotacionTeclado.pasos("C4 H4")
    }
}

class ResultadoPracticaTest {
    @Test fun estrellasSegunPrecision() {
        assertEquals(3, estrellasPara(100)); assertEquals(3, estrellasPara(95))
        assertEquals(2, estrellasPara(94)); assertEquals(2, estrellasPara(80))
        assertEquals(1, estrellasPara(79)); assertEquals(1, estrellasPara(60))
        assertEquals(0, estrellasPara(59))
    }
}

class SeleccionPracticaTest {
    // 4/4: 3840 ticks por compás. Derecha en los compases 1, 2 y 3; izquierda en el 2.
    private val cancion = Cancion.desdeNotas(
        "t",
        listOf(
            n(60, 0, 960),
            Nota(48, 3840, 960, Mano.IZQUIERDA, 90),
            n(62, 3840, 960),
            n(64, 7680, 960),
        ),
    )

    @Test fun porManoSeparaEvaluadasYAcompanamiento() {
        val s = SeleccionPractica(mano = ManoPractica.DERECHA)
        assertEquals(listOf(60, 62, 64), s.notasAEvaluar(cancion).map { it.nota })
        assertEquals(listOf(48), s.notasDeAcompanamiento(cancion).map { it.nota })
        assertTrue(SeleccionPractica().notasDeAcompanamiento(cancion).isEmpty())
    }

    @Test fun tramoLimitaNotasYDaUnCompasDePreparacion() {
        val s = SeleccionPractica(tramo = 2..2)
        assertEquals(listOf(48, 62), s.notasAEvaluar(cancion).map { it.nota })
        assertEquals(0L, s.tickInicio(cancion)) // compás 2 empieza en 3840; preparación desde 0
        assertEquals(7680L, s.tickFin(cancion))
        assertEquals(3, SeleccionPractica.totalCompases(cancion))
    }

    @Test fun vueltaConservaElMarcador() {
        val e = EvaluadorPractica(cancion.notas.take(1), 120)
        e.marcarEspera()
        assertEquals(Resultado.GRUPO_COMPLETO, e.alPulsar(60, 0.0))
        assertTrue(e.terminado)
        e.reiniciarVuelta()
        assertFalse(e.terminado)
        assertEquals(1, e.aciertos); assertEquals(1, e.racha)
    }
}

class EtiquetasTecladoTest {
    @Test fun etiquetasSegunAjustes() {
        val soloDo = TecladoGeometria.etiquetas(Notacion.LATINA, EtiquetasTeclado.SOLO_DO)
        assertEquals("Do4", soloDo[60])
        assertEquals(null, soloDo[62])
        val blancas = TecladoGeometria.etiquetas(Notacion.INGLESA, EtiquetasTeclado.BLANCAS)
        assertEquals("D4", blancas[62])
        assertEquals(null, blancas[61]) // negra
        assertEquals(null, blancas[20]) // fuera del piano
        assertTrue(TecladoGeometria.etiquetas(Notacion.LATINA, EtiquetasTeclado.NINGUNA).all { it == null })
    }
}

class LectorMidiTest {
    @Test fun loExportadoVuelveAEntrarIgual() {
        val original = CancionesDemo.himnoDeLaAlegria()
        val leido = com.tuapp.piano.domain.musica.LectorMidi.leer(MidiFileWriter.aBytes(original))
        val clave = { n: Nota -> listOf(n.nota, n.tickInicio, n.duracionTicks, n.mano.ordinal) }
        val desplazamiento = if (original.notas.first().tickInicio < original.compas.ticksPorCompas) original.compas.ticksPorCompas else 0
        assertEquals(
            original.notas.map { clave(it.copy(tickInicio = it.tickInicio + desplazamiento)) }.sortedBy { it.toString() },
            leido.notas.map(clave).sortedBy { it.toString() },
        )
        assertEquals(original.bpm, leido.bpm, 0.5)
        assertEquals(original.compas, leido.compas)
        assertEquals(original.titulo, leido.titulo)
        assertEquals(0, leido.descartadas)
    }

    @Test fun tipo0ConRunningStatusPercusionYOtraResolucion() {
        val pista = byteArrayOf(
            0x00, 0xFF.toByte(), 0x51, 0x03, 0x07, 0xA1.toByte(), 0x20, // tempo 500000 µs = 120 BPM
            0x00, 0x90.toByte(), 0x3C, 0x64,                            // Do4 on
            0x00, 0x30, 0x64,                                           // Do3 on (running status)
            0x83.toByte(), 0x60, 0x3C, 0x00,                            // +480: Do4 off (velocidad 0)
            0x00, 0x30, 0x00,                                           // Do3 off
            0x00, 0x99.toByte(), 0x24, 0x64,                            // bombo: se descarta
            0x00, 0xFF.toByte(), 0x2F, 0x00,
        )
        val bytes = "MThd".toByteArray() + byteArrayOf(0, 0, 0, 6, 0, 0, 0, 1, 0x01, 0xE0.toByte()) +
            "MTrk".toByteArray() + byteArrayOf(0, 0, 0, pista.size.toByte()) + pista
        val leido = com.tuapp.piano.domain.musica.LectorMidi.leer(bytes)
        assertEquals(120.0, leido.bpm, 0.01)
        assertEquals(1, leido.descartadas)
        assertEquals(2, leido.notas.size)
        val do4 = leido.notas.first { it.nota == 60 }
        val do3 = leido.notas.first { it.nota == 48 }
        assertEquals(Mano.DERECHA, do4.mano)
        assertEquals(Mano.IZQUIERDA, do3.mano)
        assertEquals(960L, do4.duracionTicks)   // 480 ticks a división 480 = una negra = 960 del proyecto
        assertEquals(3840L, do4.tickInicio)     // empezaba en 0: se le añade un compás vacío
    }

    @Test fun rechazaLoQueNoEsMidi() {
        val e = runCatching { com.tuapp.piano.domain.musica.LectorMidi.leer("hola".toByteArray()) }.exceptionOrNull()
        assertTrue(e is com.tuapp.piano.domain.musica.ErrorMidi)
    }
}

class InicioTest {
    private fun r(id: Long, fecha: Long) = com.tuapp.piano.domain.model.ResumenCancion(
        id, "c$id", 100.0, Compas(), Cuantizacion.SEMICORCHEA, fecha,
        com.tuapp.piano.domain.model.OrigenCancion.CREADA, 10, 3840,
    )
    private val biblioteca = listOf(r(3, 300), r(2, 200), r(1, 100)) // más nuevas primero

    @Test fun sinActividadOfreceLaMasNueva() {
        val i = com.tuapp.piano.domain.inicio.armarInicio(biblioteca, com.tuapp.piano.domain.model.Actividad())
        assertEquals(3L, i.continuar?.resumen?.id)
        assertFalse(i.yaTocada)
        assertEquals(listOf(2L, 1L), i.recientes.map { it.resumen.id })
    }

    @Test fun conActividadContinuaLaUltimaEnSuModoYConSuMarca() {
        val a = com.tuapp.piano.domain.model.Actividad()
            .conApertura(1, com.tuapp.piano.domain.model.ModoPiano.REPRODUCIR)
            .conApertura(2, com.tuapp.piano.domain.model.ModoPiano.PRACTICA)
            .conResultado(2, 80).conResultado(2, 70) // la peor no pisa la mejor
            .conApertura(99, com.tuapp.piano.domain.model.ModoPiano.PRACTICA) // borrada
        val i = com.tuapp.piano.domain.inicio.armarInicio(biblioteca, a)
        assertEquals(2L, i.continuar?.resumen?.id)
        assertTrue(i.yaTocada)
        assertEquals(80, i.continuar?.mejorPrecision)
        // La 99 ya no existe: la última válida es la 2, pero no fue la última abierta.
        assertEquals(com.tuapp.piano.domain.model.ModoPiano.REPRODUCIR, i.modoContinuar)
        assertEquals(listOf(1L, 3L), i.recientes.map { it.resumen.id })
    }

    @Test fun bibliotecaVacia() {
        assertEquals(null, com.tuapp.piano.domain.inicio.armarInicio(emptyList(), com.tuapp.piano.domain.model.Actividad()).continuar)
    }
}

class FisicaCarruselTest {
    private val f = com.tuapp.piano.presentation.ui.home.FisicaCarrusel

    @Test fun proyeccionDeApple() {
        // 1000 px/s con 0.998 -> 1 · 0.998 / 0.002 = 499 px
        assertEquals(499f, f.proyectar(1000f), 0.5f)
        assertEquals(0f, f.proyectar(0f), 0f)
    }

    @Test fun destinoUsaElPuntoProyectadoYNoSePasa() {
        assertEquals(0, f.indiceDestino(posicion = 100f, velocidad = 0f, paso = 300f, total = 5)) // 100 < 150
        assertEquals(2, f.indiceDestino(posicion = 100f, velocidad = 1000f, paso = 300f, total = 5)) // 599 -> 2
        assertEquals(4, f.indiceDestino(posicion = 900f, velocidad = 20000f, paso = 300f, total = 5))
        assertEquals(0, f.indiceDestino(posicion = 0f, velocidad = -5000f, paso = 300f, total = 5))
    }

    @Test fun bordesElasticosFrenanCadaVezMas() {
        val poco = f.conBordesElasticos(-50f, 0f, 1000f, 400f)
        val mucho = f.conBordesElasticos(-500f, 0f, 1000f, 400f)
        assertTrue(poco < 0f && poco > -50f)
        assertTrue(mucho < poco && mucho > -500f)
        assertEquals(500f, f.conBordesElasticos(500f, 0f, 1000f, 400f), 0f)
    }
}

class EventosReproduccionTest {
    @Test fun mismaTeclaSeguida_elOffVaAntesQueElOn() {
        val c = Cancion(titulo = "x", notas = listOf(n(60, 0, 480), n(60, 480, 480)))
        val e = com.tuapp.piano.domain.usecase.ReproducirCancionUseCase.eventosOrdenados(c)
        assertEquals(listOf(0L, 480L, 480L, 960L), e.map { it.tick })
        assertEquals(listOf(true, false, true, false), e.map { it.encender })
    }
}

class SensibilidadToqueTest {
    @Test fun normal_esElRepartoLinealOriginal() {
        for (i in 0..20) {
            val rel = i / 20f
            assertEquals((45 + rel * (127 - 45)).toInt(), com.tuapp.piano.domain.model.SensibilidadToque.NORMAL.velocidad(rel))
        }
    }

    @Test fun curvas_vanDe45A127YSuaveQuedaPorDebajoDeFuerte() {
        for (s in com.tuapp.piano.domain.model.SensibilidadToque.entries) {
            assertEquals(45, s.velocidad(0f))
            assertEquals(127, s.velocidad(1f))
        }
        val suave = com.tuapp.piano.domain.model.SensibilidadToque.SUAVE.velocidad(0.5f)
        val fuerte = com.tuapp.piano.domain.model.SensibilidadToque.FUERTE.velocidad(0.5f)
        assertTrue(suave < 86 && 86 < fuerte)
    }
}
