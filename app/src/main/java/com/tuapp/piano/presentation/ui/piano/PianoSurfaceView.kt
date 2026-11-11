package com.tuapp.piano.presentation.ui.piano

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.EtiquetasTeclado
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.Notacion
import com.tuapp.piano.domain.model.PPQ
import com.tuapp.piano.domain.model.RelojSnapshot
import com.tuapp.piano.presentation.ui.theme.Paleta
import com.tuapp.piano.presentation.ui.piano.TecladoGeometria as G
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Teclado de 88 teclas + cascada de notas, dibujados en un hilo propio a la
 * frecuencia del display (60 FPS o más) y sincronizados con el reloj del motor C++.
 *
 * Distribución vertical:
 *   ┌ minimapa (88 teclas en miniatura; tocar/arrastrar = desplazar) ┐
 *   │ cascada: las notas caen hacia la línea de impacto               │
 *   ├─────────────────────── línea de impacto ───────────────────────┤
 *   └ teclado desplazable (multitouch, glissando)                     ┘
 *
 * Reglas de rendimiento del hilo de render: cero asignaciones por frame
 * (Paint/RectF/arrays preasignados) y lockHardwareCanvas (dibujo por GPU).
 */
class PianoSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    interface Listener {
        fun onNoteOn(nota: Int, velocidad: Int, mano: Mano)
        fun onNoteOff(nota: Int, mano: Mano)
    }

    var listener: Listener? = null

    /** Lector del reloj maestro; se invoca desde el hilo de render en cada frame. */
    @Volatile var fuenteReloj: ((RelojSnapshot) -> Unit)? = null

    /** Latencia de salida de audio: la cascada se retrasa lo mismo para coincidir con lo que se OYE. */
    @Volatile var latenciaAudioMs: Double = 0.0

    /** Teclas por debajo de esta nota se consideran mano izquierda (Do central por defecto). */
    @Volatile var notaDivision: Int = 60

    /** false: todas las notas suenan con [VELOCIDAD_FIJA], sin importar dónde se toque. */
    @Volatile var dinamicaTactil: Boolean = true

    /** Compases que caben en la cascada: más compases = las notas caen más despacio. */
    @Volatile var compasesVisibles: Int = 2

    /** Nombre a dibujar en cada tecla (tabla precalculada de TecladoGeometria). */
    @Volatile var etiquetas: Array<String?> = G.etiquetas(Notacion.LATINA, EtiquetasTeclado.SOLO_DO)

    /** Zoom: cuántas teclas blancas caben en pantalla (14 = muy grande, 52 = piano completo). */
    var teclasVisibles: Int = 21
        set(value) {
            val v = value.coerceIn(MIN_TECLAS_VISIBLES, G.TOTAL_BLANCAS)
            if (v == field) return
            // Mantener centrado el mismo punto del teclado al hacer zoom.
            val centro = (desplazamientoX + anchoVista / 2f) / max(anchoBlanca, 1f)
            field = v
            recalcularGeometria()
            desplazamientoX = centro * anchoBlanca - anchoVista / 2f
            limitarScroll()
        }

    /** Práctica: la tecla [nota] destella en verde (acierto) o rojo (error). Hilo UI. */
    fun marcarResultado(nota: Int, acierto: Boolean) {
        if (nota !in 0..127) return
        instanteResultado.set(nota, System.nanoTime())
        resultadoTecla.set(nota, if (acierto) RESULTADO_ACIERTO else RESULTADO_ERROR)
    }

    fun setCancion(cancion: Cancion?) {
        if (partitura?.origen === cancion) return
        partitura = cancion?.let(::Partitura)
    }

    // ---------------------------------------------------------------------------------
    // Geometría (escrita en el hilo UI, leída por el de render: @Volatile basta,
    // un frame con valores mezclados durante un zoom es invisible).
    // ---------------------------------------------------------------------------------
    private val densidad = resources.displayMetrics.density
    @Volatile private var anchoVista = 0
    @Volatile private var altoVista = 0
    @Volatile private var anchoBlanca = 1f
    @Volatile private var topTeclado = 0f
    @Volatile private var altoTeclado = 0f
    @Volatile private var altoMinimapa = 0f
    @Volatile private var desplazamientoX = 0f
    private var centradoInicial = false

    private val anchoTotal get() = anchoBlanca * G.TOTAL_BLANCAS

    @Volatile private var partitura: Partitura? = null

    // Estado de teclas presionadas por el usuario: (mano.ordinal + 1) o 0. Lo lee el render.
    private val teclasTocadas = AtomicIntegerArray(128)

    // Último resultado de práctica por tecla y cuándo ocurrió (System.nanoTime, misma base que el vsync).
    private val resultadoTecla = AtomicIntegerArray(128)
    private val instanteResultado = AtomicLongArray(128)

    // ---------------------------------------------------------------------------------
    // Hilo de render
    // ---------------------------------------------------------------------------------
    private val bloqueoSuperficie = Any()
    @Volatile private var superficieLista = false
    private var hiloRender: HandlerThread? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!superficieLista) return
            renderizar(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        superficieLista = true
        val hilo = HandlerThread("PianoRender", Process.THREAD_PRIORITY_DISPLAY).also { it.start() }
        hiloRender = hilo
        // Choreographer es por hilo: obtenerlo DENTRO del hilo de render da vsync propio.
        Handler(hilo.looper).post { Choreographer.getInstance().postFrameCallback(frameCallback) }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Esperar a que termine el frame en curso: tras volver de aquí la superficie no es válida.
        synchronized(bloqueoSuperficie) { superficieLista = false }
        hiloRender?.quitSafely()
        hiloRender?.join()
        hiloRender = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        anchoVista = w
        altoVista = h
        recalcularGeometria()
        if (!centradoInicial && w > 0) {
            centradoInicial = true
            centrarEnNota(60)
        }
        limitarScroll()
    }

    private fun recalcularGeometria() {
        if (anchoVista == 0) return
        anchoBlanca = anchoVista.toFloat() / teclasVisibles
        altoMinimapa = 22f * densidad
        altoTeclado = (altoVista * PROPORCION_TECLADO).coerceIn(110f * densidad, 260f * densidad)
        topTeclado = altoVista - altoTeclado
    }

    fun centrarEnNota(nota: Int) {
        desplazamientoX = (G.xUnidades(nota) + 0.5f) * anchoBlanca - anchoVista / 2f
        limitarScroll()
    }

    private fun limitarScroll() {
        desplazamientoX = desplazamientoX.coerceIn(0f, max(0f, anchoTotal - anchoVista))
    }

    private fun renderizar(frameTimeNanos: Long) {
        synchronized(bloqueoSuperficie) {
            if (!superficieLista) return
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: IllegalStateException) {
                null
            } ?: return
            try {
                dibujar(canvas, frameTimeNanos)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Dibujo (hilo de render). Nada de `new` aquí dentro.
    // ---------------------------------------------------------------------------------
    private val reloj = RelojSnapshot()
    private val activasCancion = IntArray(128)
    private val rect = RectF()
    private val hud = CharArray(48)

    private val pRelleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pLinea = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * densidad
    }
    private val pHud = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = 14f * densidad
        color = COLOR_TEXTO
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private fun dibujar(c: Canvas, frameTimeNanos: Long) {
        val w = anchoVista.toFloat()
        val h = altoVista.toFloat()
        if (w <= 0f || h <= 0f) return
        val sx = desplazamientoX
        val ww = anchoBlanca
        val impactoY = topTeclado
        val topCascada = altoMinimapa
        val altoCascada = impactoY - topCascada

        c.drawColor(COLOR_FONDO)

        // --- Reloj: lo que el usuario ve debe coincidir con lo que OYE ---
        // frameTimeNanos = vsync; el frame se muestra ~1 vsync después; el audio
        // generado ahora sale por el altavoz `latencia` ms más tarde.
        fuenteReloj?.invoke(reloj)
        val instante = frameTimeNanos + ADELANTO_DISPLAY_NS - (latenciaAudioMs * 1_000_000.0).toLong()
        val tick = max(0.0, reloj.tickAt(instante))

        val p = partitura
        val ticksPorTiempo = p?.ticksPorTiempo ?: PPQ
        val ticksPorCompas = p?.ticksPorCompas ?: (PPQ * 4)
        val ticksVisibles = (ticksPorCompas * compasesVisibles.coerceAtLeast(1)).toDouble()
        val pxPorTick = (altoCascada / ticksVisibles).toFloat()

        c.save()
        c.clipRect(0f, topCascada, w, impactoY)

        // --- Rejilla de tiempos y compases (se mueve con la música) ---
        var t = ceil(tick / ticksPorTiempo).toLong() * ticksPorTiempo
        val tickTope = tick + ticksVisibles
        while (t <= tickTope) {
            val y = impactoY - ((t - tick) * pxPorTick).toFloat()
            val esCompas = t % ticksPorCompas == 0L
            pLinea.color = if (esCompas) COLOR_REJILLA_COMPAS else COLOR_REJILLA_TIEMPO
            pLinea.strokeWidth = if (esCompas) 1.5f * densidad else 1f
            c.drawLine(0f, y, w, y, pLinea)
            t += ticksPorTiempo
        }

        // --- Notas de la canción ---
        java.util.Arrays.fill(activasCancion, 0)
        if (p != null) dibujarNotas(c, p, tick, tickTope, impactoY, pxPorTick, sx, ww, w)
        c.restore()

        // --- Línea de impacto ---
        pLinea.color = COLOR_IMPACTO
        pLinea.strokeWidth = 2f * densidad
        c.drawLine(0f, impactoY, w, impactoY, pLinea)

        dibujarTeclado(c, sx, ww, w, h, frameTimeNanos)
        dibujarDestellos(c, sx, ww, w, frameTimeNanos)
        dibujarMinimapa(c, sx, w)
        dibujarHud(c, tick, ticksPorTiempo, ticksPorCompas, w, topCascada)
    }

    private fun dibujarNotas(
        c: Canvas, p: Partitura, tick: Double, tickTope: Double,
        impactoY: Float, pxPorTick: Float, sx: Float, ww: Float, w: Float,
    ) {
        val radio = 5f * densidad
        val margen = max(1f, ww * 0.06f)
        var i = p.primerIndiceDesde(tick - p.maxDuracion)
        while (i < p.n && p.inicio[i] <= tickTope) {
            val ini = p.inicio[i]
            val fin = p.fin[i]
            if (fin >= tick) {
                val nota = p.nota[i]
                val mano = p.mano[i]
                if (ini <= tick && tick < fin) activasCancion[nota] = mano + 1

                val x = G.xUnidades(nota) * ww - sx
                val ancho = G.anchoUnidades(nota) * ww
                if (x + ancho >= 0f && x <= w) {
                    val yAbajo = impactoY - ((ini - tick) * pxPorTick).toFloat()
                    val yArriba = impactoY - ((fin - tick) * pxPorTick).toFloat()
                    rect.set(x + margen, yArriba, x + ancho - margen, yAbajo)
                    pRelleno.color = colorNota(mano, G.esNegra[nota])
                    c.drawRoundRect(rect, radio, radio, pRelleno)
                }
            }
            i++
        }
    }

    private fun dibujarTeclado(c: Canvas, sx: Float, ww: Float, w: Float, h: Float, ahora: Long) {
        val top = topTeclado
        val altoNegra = altoTeclado * G.PROPORCION_ALTO_NEGRA
        val primera = max(0, floor(sx / ww).toInt())
        val ultima = min(G.TOTAL_BLANCAS - 1, ceil((sx + w) / ww).toInt())
        val radio = 4f * densidad

        // Recorte: las teclas se dibujan con esquinas redondeadas y su borde superior
        // queda oculto bajo la línea de impacto -> arriba rectas, abajo redondeadas.
        c.save()
        c.clipRect(0f, top, w, h)

        // Blancas
        pLinea.color = COLOR_SEPARADOR
        pLinea.strokeWidth = 1f
        pTexto.color = COLOR_ETIQUETA
        for (wi in primera..ultima) {
            val nota = G.notaBlanca[wi]
            val x = wi * ww - sx
            val estado = estadoTecla(nota)
            pRelleno.color = conResultado(nota, if (estado == 0) COLOR_TECLA_BLANCA else colorTeclaIluminada(estado - 1), ahora)
            rect.set(x + 0.5f, top - radio, x + ww - 0.5f, h)
            c.drawRoundRect(rect, radio, radio, pRelleno)
            c.drawLine(x, top, x, h, pLinea)
            etiquetas[nota]?.let { c.drawText(it, x + ww / 2f, h - 8f * densidad, pTexto) }
        }

        // Negras (encima de las blancas)
        val anchoNegra = ww * G.PROPORCION_ANCHO_NEGRA
        for (wi in max(0, primera - 1)..ultima) {
            val nota = G.notaBlanca[wi] + 1
            if (nota > G.ULTIMA || !G.esNegra[nota]) continue
            val x = (wi + 1) * ww - anchoNegra / 2f - sx
            val estado = estadoTecla(nota)
            pRelleno.color = conResultado(nota, if (estado == 0) COLOR_TECLA_NEGRA else colorTeclaIluminada(estado - 1), ahora)
            rect.set(x, top - radio, x + anchoNegra, top + altoNegra)
            c.drawRoundRect(rect, radio, radio, pRelleno)
        }
        c.restore()
    }

    /** Halo sobre la línea de impacto encima de cada tecla con un resultado reciente. */
    private fun dibujarDestellos(c: Canvas, sx: Float, ww: Float, w: Float, ahora: Long) {
        val alto = ALTO_DESTELLO_DP * densidad
        val top = topTeclado
        for (nota in G.PRIMERA..G.ULTIMA) {
            val f = intensidadResultado(nota, ahora)
            if (f <= 0f) continue
            val x = G.xUnidades(nota) * ww - sx
            val ancho = G.anchoUnidades(nota) * ww
            if (x + ancho < 0f || x > w) continue
            pRelleno.color = conAlfa(colorResultado(nota), (f * 200).toInt())
            rect.set(x, top - alto, x + ancho, top)
            c.drawRect(rect, pRelleno)
        }
    }

    /** 1 justo al marcar el resultado, baja a 0 en [DURACION_DESTELLO_NS]. */
    private fun intensidadResultado(nota: Int, ahora: Long): Float {
        if (resultadoTecla.get(nota) == 0) return 0f
        val t = (ahora - instanteResultado.get(nota)).toFloat() / DURACION_DESTELLO_NS
        return if (t >= 1f) 0f else 1f - max(0f, t)
    }

    private fun colorResultado(nota: Int) =
        if (resultadoTecla.get(nota) == RESULTADO_ACIERTO) COLOR_ACIERTO else COLOR_FALLO

    private fun conResultado(nota: Int, base: Int, ahora: Long): Int {
        val f = intensidadResultado(nota, ahora)
        return if (f <= 0f) base else mezclar(base, colorResultado(nota), f * 0.85f)
    }

    private fun dibujarMinimapa(c: Canvas, sx: Float, w: Float) {
        val alto = altoMinimapa
        pRelleno.color = COLOR_MINIMAPA_FONDO
        c.drawRect(0f, 0f, w, alto, pRelleno)
        val mww = w / G.TOTAL_BLANCAS
        pRelleno.color = COLOR_MINIMAPA_BLANCA
        for (wi in 0 until G.TOTAL_BLANCAS) {
            c.drawRect(wi * mww + 0.5f, 3f, (wi + 1) * mww - 0.5f, alto - 3f, pRelleno)
        }
        pRelleno.color = COLOR_MINIMAPA_NEGRA
        for (wi in 0 until G.TOTAL_BLANCAS) {
            val nota = G.notaBlanca[wi] + 1
            if (nota > G.ULTIMA || !G.esNegra[nota]) continue
            val cx = (wi + 1) * mww
            c.drawRect(cx - mww * 0.3f, 3f, cx + mww * 0.3f, alto * 0.6f, pRelleno)
        }
        // Ventana visible
        val escala = w / max(anchoTotal, 1f)
        pLinea.color = COLOR_IMPACTO
        pLinea.strokeWidth = 2f * densidad
        rect.set(sx * escala, 1.5f, (sx + w) * escala, alto - 1.5f)
        c.drawRoundRect(rect, 3f, 3f, pLinea)
    }

    private fun dibujarHud(c: Canvas, tick: Double, ticksPorTiempo: Int, ticksPorCompas: Int, w: Float, top: Float) {
        val t = tick.toLong()
        val compas = (t / ticksPorCompas + 1).toInt()
        val tiempo = ((t % ticksPorCompas) / ticksPorTiempo + 1).toInt()
        var n = escribirEntero(hud, 0, compas, 3)
        hud[n++] = ':'
        n = escribirEntero(hud, n, tiempo, 1)
        for (ch in "   ") hud[n++] = ch
        n = escribirEntero(hud, n, reloj.bpm.toInt(), 1)
        for (ch in " BPM") hud[n++] = ch
        c.drawText(hud, 0, n, w - 12f * densidad, top + 22f * densidad, pHud)
    }

    private fun estadoTecla(nota: Int): Int {
        val tocada = teclasTocadas.get(nota)
        return if (tocada != 0) tocada else activasCancion[nota]
    }

    // ---------------------------------------------------------------------------------
    // Entrada táctil (hilo UI): multitouch, glissando, desplazamiento.
    // ---------------------------------------------------------------------------------
    private val modoPuntero = IntArray(MAX_PUNTEROS) { LIBRE } // >= 0: nota que mantiene
    private val ultimoX = FloatArray(MAX_PUNTEROS)
    private val dedosPorNota = IntArray(128)

    @SuppressLint("ClickableViewAccessibility") // performClick se llama en ACTION_UP
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                empezarPuntero(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) moverPuntero(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                terminarPuntero(e.getPointerId(e.actionIndex))
                if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            MotionEvent.ACTION_CANCEL -> for (id in 0 until MAX_PUNTEROS) terminarPuntero(id)
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun empezarPuntero(id: Int, x: Float, y: Float) {
        if (id !in 0 until MAX_PUNTEROS) return
        when {
            y >= topTeclado -> {
                val nota = notaEn(x + desplazamientoX, y)
                if (nota >= 0) {
                    modoPuntero[id] = nota
                    pulsar(nota, velocidadPara(nota, y))
                }
            }
            y < altoMinimapa -> {
                modoPuntero[id] = MINIMAPA
                saltarConMinimapa(x)
            }
            else -> {
                modoPuntero[id] = ARRASTRE
                ultimoX[id] = x
            }
        }
    }

    private fun moverPuntero(id: Int, x: Float, y: Float) {
        if (id !in 0 until MAX_PUNTEROS) return
        val modo = modoPuntero[id]
        when {
            modo >= 0 -> {
                // Glissando: al deslizar sobre otra tecla, suelta la anterior y ataca la nueva.
                val nueva = if (y >= topTeclado) notaEn(x + desplazamientoX, y) else -1
                if (nueva != modo) {
                    soltar(modo)
                    modoPuntero[id] = if (nueva >= 0) nueva else LIBRE
                    if (nueva >= 0) pulsar(nueva, velocidadPara(nueva, y))
                }
            }
            modo == MINIMAPA -> saltarConMinimapa(x)
            modo == ARRASTRE -> {
                desplazamientoX -= x - ultimoX[id]
                ultimoX[id] = x
                limitarScroll()
            }
        }
    }

    private fun terminarPuntero(id: Int) {
        if (id !in 0 until MAX_PUNTEROS) return
        val modo = modoPuntero[id]
        if (modo >= 0) soltar(modo)
        modoPuntero[id] = LIBRE
    }

    private fun manoDe(nota: Int) = if (nota < notaDivision) Mano.IZQUIERDA else Mano.DERECHA

    private fun pulsar(nota: Int, velocidad: Int) {
        val mano = manoDe(nota)
        dedosPorNota[nota]++
        teclasTocadas.set(nota, mano.ordinal + 1)
        listener?.onNoteOn(nota, velocidad, mano)
    }

    private fun soltar(nota: Int) {
        if (dedosPorNota[nota] == 0) return
        // Con dos dedos en la misma tecla, solo se apaga al levantar el último.
        if (--dedosPorNota[nota] == 0) {
            teclasTocadas.set(nota, 0)
            listener?.onNoteOff(nota, manoDe(nota))
        }
    }

    /**
     * Velocidad según dónde se toca la tecla: cerca del borde frontal = más fuerte,
     * como en un piano real (más palanca). Más fiable que la presión táctil, que
     * muchos teléfonos no reportan.
     */
    private fun velocidadPara(nota: Int, y: Float): Int {
        if (!dinamicaTactil) return VELOCIDAD_FIJA
        val alto = if (G.esNegra[nota]) altoTeclado * G.PROPORCION_ALTO_NEGRA else altoTeclado
        val rel = ((y - topTeclado) / alto).coerceIn(0f, 1f)
        return (VELOCIDAD_MIN + rel * (127 - VELOCIDAD_MIN)).toInt()
    }

    /** cx en coordenadas de contenido (ya sumado el scroll). -1 si no hay tecla. */
    private fun notaEn(cx: Float, y: Float): Int {
        val ww = anchoBlanca
        if (cx < 0f || cx >= anchoTotal) return -1
        val wi = (cx / ww).toInt().coerceIn(0, G.TOTAL_BLANCAS - 1)
        if (y < topTeclado + altoTeclado * G.PROPORCION_ALTO_NEGRA) {
            val mitadNegra = ww * G.PROPORCION_ANCHO_NEGRA / 2f
            val blanca = G.notaBlanca[wi]
            // Negra a la derecha de esta blanca
            val derecha = blanca + 1
            if (derecha <= G.ULTIMA && G.esNegra[derecha] && cx >= (wi + 1) * ww - mitadNegra) return derecha
            // Negra a la izquierda
            val izquierda = blanca - 1
            if (izquierda >= G.PRIMERA && G.esNegra[izquierda] && cx <= wi * ww + mitadNegra) return izquierda
        }
        return G.notaBlanca[wi]
    }

    private fun saltarConMinimapa(x: Float) {
        desplazamientoX = (x / max(anchoVista, 1)) * anchoTotal - anchoVista / 2f
        limitarScroll()
    }

    // ---------------------------------------------------------------------------------

    /** Copia de la canción en arrays planos: el bucle de dibujo no toca objetos ni listas. */
    private class Partitura(val origen: Cancion) {
        val n = origen.notas.size
        val nota = IntArray(n)
        val inicio = LongArray(n)
        val fin = LongArray(n)
        val mano = IntArray(n)
        val maxDuracion: Long
        val ticksPorTiempo = origen.compas.ticksPorTiempo
        val ticksPorCompas = origen.compas.ticksPorCompas

        init {
            var maxDur = 0L
            origen.notas.forEachIndexed { i, nt ->
                nota[i] = nt.nota
                inicio[i] = nt.tickInicio
                fin[i] = nt.tickFin
                mano[i] = nt.mano.ordinal
                maxDur = max(maxDur, nt.duracionTicks)
            }
            maxDuracion = maxDur
        }

        /** Primer índice con inicio >= tick (búsqueda binaria; las notas vienen ordenadas). */
        fun primerIndiceDesde(tick: Double): Int {
            var lo = 0
            var hi = n
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (inicio[mid] < tick) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }

    private companion object {
        const val MAX_PUNTEROS = 16
        const val LIBRE = -1
        const val MINIMAPA = -2
        const val ARRASTRE = -3
        const val MIN_TECLAS_VISIBLES = 10
        const val VELOCIDAD_MIN = 45
        const val PROPORCION_TECLADO = 0.34f
        const val VELOCIDAD_FIJA = 100
        const val ADELANTO_DISPLAY_NS = 16_666_667L

        // Colores: salen de la misma Paleta que usa el tema de Compose.
        const val COLOR_FONDO = Paleta.FONDO.toInt()
        const val COLOR_TECLA_BLANCA = Paleta.BLANCO.toInt()
        const val COLOR_TECLA_NEGRA = Paleta.SUPERFICIE.toInt()
        const val COLOR_DERECHA = Paleta.CIAN.toInt()
        const val COLOR_DERECHA_OSCURO = Paleta.CIAN_OSCURO.toInt()
        const val COLOR_IZQUIERDA = Paleta.MAGENTA.toInt()
        const val COLOR_IZQUIERDA_OSCURO = Paleta.MAGENTA_OSCURO.toInt()
        const val COLOR_SEPARADOR = Paleta.GRIS_SEPARADOR.toInt()
        const val COLOR_ETIQUETA = Paleta.GRIS_MEDIO.toInt()
        const val COLOR_MINIMAPA_FONDO = Paleta.NEGRO_PROFUNDO.toInt()
        const val COLOR_MINIMAPA_BLANCA = Paleta.GRIS_OSCURO.toInt()
        const val COLOR_MINIMAPA_NEGRA = Paleta.NEGRO_PROFUNDO.toInt()
        val COLOR_IMPACTO = Paleta.conAlfa(Paleta.CIAN, 0x99)
        val COLOR_REJILLA_TIEMPO = Paleta.conAlfa(Paleta.BLANCO, 0x14)
        val COLOR_REJILLA_COMPAS = Paleta.conAlfa(Paleta.BLANCO, 0x33)
        val COLOR_TEXTO = Paleta.conAlfa(Paleta.BLANCO, 0xB3)
        const val COLOR_ACIERTO = Paleta.VERDE.toInt()
        const val COLOR_FALLO = Paleta.ROJO.toInt()

        const val RESULTADO_ACIERTO = 1
        const val RESULTADO_ERROR = 2
        const val DURACION_DESTELLO_NS = 450_000_000f
        const val ALTO_DESTELLO_DP = 10f

        fun conAlfa(color: Int, alfa: Int) = (alfa.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

        /** Interpola dos colores ARGB opacos sin crear objetos (hilo de render). */
        fun mezclar(a: Int, b: Int, f: Float): Int {
            fun canal(desp: Int): Int {
                val ca = (a shr desp) and 0xFF
                val cb = (b shr desp) and 0xFF
                return (ca + (cb - ca) * f).toInt() and 0xFF
            }
            return (0xFF shl 24) or (canal(16) shl 16) or (canal(8) shl 8) or canal(0)
        }

        fun colorNota(mano: Int, negra: Boolean) = when {
            mano == Mano.DERECHA.ordinal -> if (negra) COLOR_DERECHA_OSCURO else COLOR_DERECHA
            else -> if (negra) COLOR_IZQUIERDA_OSCURO else COLOR_IZQUIERDA
        }

        fun colorTeclaIluminada(mano: Int) =
            if (mano == Mano.DERECHA.ordinal) COLOR_DERECHA else COLOR_IZQUIERDA

        /** Escribe un entero con ceros a la izquierda sin crear Strings. Devuelve la nueva posición. */
        fun escribirEntero(buf: CharArray, pos: Int, valor: Int, minDigitos: Int): Int {
            var v = max(0, valor)
            var digitos = 1
            var tmp = v
            while (tmp >= 10) { tmp /= 10; digitos++ }
            val total = max(digitos, minDigitos)
            for (k in total - 1 downTo 0) {
                buf[pos + k] = ('0' + v % 10)
                v /= 10
            }
            return pos + total
        }
    }
}
