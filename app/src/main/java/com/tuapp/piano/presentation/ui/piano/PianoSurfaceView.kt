package com.tuapp.piano.presentation.ui.piano

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.AttributeSet
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.VelocityTracker
import com.tuapp.piano.R
import com.tuapp.piano.domain.model.Cancion
import com.tuapp.piano.domain.model.EtiquetasTeclado
import com.tuapp.piano.domain.model.Mano
import com.tuapp.piano.domain.model.Notacion
import com.tuapp.piano.domain.model.PPQ
import com.tuapp.piano.domain.model.RelojSnapshot
import com.tuapp.piano.domain.model.SensibilidadToque
import com.tuapp.piano.presentation.ui.theme.Paleta
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.tuapp.piano.presentation.ui.piano.TecladoGeometria as G

/**
 * Teclado de 88 teclas + cascada de notas, dibujados en un hilo propio a la
 * frecuencia del display (60 FPS o más) y sincronizados con el reloj del motor C++.
 *
 * Distribución vertical:
 *   ┌ minimapa (88 teclas en miniatura; tocar/arrastrar = desplazar) ┐
 *   │ cascada: las notas caen hacia la línea de impacto               │
 *   │   (arrastrar = desplazar con inercia; pellizcar = zoom)         │
 *   ├─────────────────────── línea de impacto ───────────────────────┤
 *   └ teclado desplazable (multitouch, glissando)                     ┘
 *
 * Reglas de rendimiento del hilo de render: cero asignaciones por frame
 * (Paint/RectF/Shader/arrays preasignados) y lockHardwareCanvas (dibujo por GPU).
 * En reposo (transporte parado, sin toques ni animaciones) deja de redibujar: ver
 * [solicitarFrame] y [sondeo].
 */
class PianoSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    interface Listener {
        fun onNoteOn(nota: Int, velocidad: Int, mano: Mano)
        fun onNoteOff(nota: Int, mano: Mano)

        /** Fin de un pellizco: el zoom nuevo, para guardarlo en ajustes. */
        fun onZoom(teclasVisibles: Int) = Unit
    }

    var listener: Listener? = null

    /** Lector del reloj maestro; se invoca desde el hilo de render en cada frame. */
    @Volatile var fuenteReloj: ((RelojSnapshot) -> Unit)? = null

    /** Latencia de salida de audio: la cascada se retrasa lo mismo para coincidir con lo que se OYE. */
    @Volatile var latenciaAudioMs: Double = 0.0
        set(value) {
            if (abs(value - field) < 0.5) return
            field = value
            solicitarFrame()
        }

    /** Teclas por debajo de esta nota se consideran mano izquierda (Do central por defecto). */
    @Volatile var notaDivision: Int = 60
        set(value) {
            if (value == field) return
            field = value
            solicitarFrame()
        }

    /** false: todas las notas suenan con [VELOCIDAD_FIJA], sin importar dónde se toque. */
    @Volatile var dinamicaTactil: Boolean = true

    /** Curva de respuesta al toque (Ajustes). NORMAL = reparto lineal original. */
    @Volatile var sensibilidad: SensibilidadToque = SensibilidadToque.NORMAL

    /** Vibración corta en cada tecla (apagada por defecto: el motor de vibración hace ruido). */
    @Volatile var vibrarAlTocar: Boolean = false

    /** Sin inercia, sin desvanecidos ni resplandores (opción de accesibilidad de Android). */
    @Volatile var movimientoReducido: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            if (value) velocidadInercia = 0f
            solicitarFrame()
        }

    /** Compases que caben en la cascada: más compases = las notas caen más despacio. */
    @Volatile var compasesVisibles: Int = 2
        set(value) {
            if (value == field) return
            field = value
            solicitarFrame()
        }

    /** Nombre a dibujar en cada tecla (tabla precalculada de TecladoGeometria). */
    @Volatile var etiquetas: Array<String?> = G.etiquetas(Notacion.LATINA, EtiquetasTeclado.SOLO_DO)
        set(value) {
            if (value == field) return
            field = value
            solicitarFrame()
        }

    /**
     * Zoom: cuántas teclas blancas caben en pantalla (14 = muy grande, 52 = piano completo).
     * Mientras dura un pellizco manda el gesto: un valor que llegue de fuera (recomposición)
     * no lo interrumpe.
     */
    var teclasVisibles: Int
        set(value) {
            if (pellizcando) return
            aplicarZoom(value, anchoVista / 2f)
        }
        get() = zoomActual

    private var zoomActual = 21

    /** Práctica: la tecla [nota] destella en verde (acierto) o rojo (error). Hilo UI. */
    fun marcarResultado(nota: Int, acierto: Boolean) {
        if (nota !in 0..127) return
        instanteResultado.set(nota, System.nanoTime())
        resultadoTecla.set(nota, if (acierto) RESULTADO_ACIERTO else RESULTADO_ERROR)
        solicitarFrame()
    }

    fun setCancion(cancion: Cancion?) {
        if (partitura?.origen === cancion) return
        partitura = cancion?.let(::Partitura)
        solicitarFrame()
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

    // Suelta del usuario por tecla: cuándo (System.nanoTime) y de qué mano (ordinal + 1).
    // Atómicos porque una pulsación muy corta puede empezar y acabar entre dos frames.
    private val instanteSuelta = AtomicLongArray(128)
    private val manoSuelta = AtomicIntegerArray(128)

    // Último resultado de práctica por tecla y cuándo ocurrió (System.nanoTime, misma base que el vsync).
    private val resultadoTecla = AtomicIntegerArray(128)
    private val instanteResultado = AtomicLongArray(128)

    // ---------------------------------------------------------------------------------
    // Hilo de render
    // ---------------------------------------------------------------------------------
    private val bloqueoSuperficie = Any()
    @Volatile private var superficieLista = false
    private var hiloRender: HandlerThread? = null
    @Volatile private var manejadorRender: Handler? = null

    /** Solo hilo de render: hay un doFrame pedido al Choreographer. */
    private var frameProgramado = false
    private val despertarPendiente = AtomicBoolean(false)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameProgramado = false
            if (!superficieLista) return
            medirPeriodo(frameTimeNanos)
            aplicarInercia(frameTimeNanos)
            val seguir = renderizar(frameTimeNanos)
            if (seguir) programarFrame()
        }
    }

    /** Hilo de render: pide el próximo vsync si no estaba ya pedido. */
    private fun programarFrame() {
        if (frameProgramado || !superficieLista) return
        frameProgramado = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private val despertar = Runnable {
        despertarPendiente.set(false)
        programarFrame()
    }

    /**
     * Cualquier hilo: algo cambió y hay que dibujar. Barato y sin asignar si ya hay un
     * aviso en camino (un dedo arrastrando genera decenas de eventos por frame).
     */
    private fun solicitarFrame() {
        val h = manejadorRender ?: return
        if (despertarPendiente.compareAndSet(false, true)) h.post(despertar)
    }

    /**
     * Red de seguridad del reposo: cada [PERIODO_SONDEO_MS] mira el reloj del motor. Si el
     * transporte arrancó o la posición/tempo cambiaron sin que nadie avisara a la vista
     * (Play, un Seek en pausa, la práctica que reanuda), se vuelve a dibujar. Así ninguna
     * imagen queda congelada más de ese periodo.
     */
    private val sondeo = object : Runnable {
        override fun run() {
            if (!superficieLista) return
            if (!frameProgramado) {
                fuenteReloj?.invoke(relojSondeo)
                if (relojSondeo.running || relojSondeo.tick != tickDibujado || relojSondeo.bpm != bpmDibujado) programarFrame()
            }
            manejadorRender?.postDelayed(this, PERIODO_SONDEO_MS)
        }
    }
    private val relojSondeo = RelojSnapshot()
    @Volatile private var tickDibujado = Double.NaN
    @Volatile private var bpmDibujado = Double.NaN

    /**
     * Periodo real del display. El frame se muestra ~1 vsync después de frameTimeNanos; a
     * 90/120 Hz ese vsync dura 11/8 ms, no 16,7. Se mide con los propios frames (la
     * frecuencia puede cambiar sola en pantallas adaptativas), suavizado. Solo hilo de render.
     */
    private var periodoVsyncNs = PERIODO_VSYNC_INICIAL_NS
    private var ultimoFrameNs = 0L

    private fun medirPeriodo(frameTimeNanos: Long) {
        val delta = frameTimeNanos - ultimoFrameNs
        ultimoFrameNs = frameTimeNanos
        if (delta in PERIODO_MIN_NS..PERIODO_MAX_NS) periodoVsyncNs += (delta - periodoVsyncNs) / 8
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        contentDescription = context.getString(R.string.piano_teclado_descripcion)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        superficieLista = true
        // Punto de partida razonable antes de medir: la frecuencia que anuncia el display.
        display?.refreshRate?.takeIf { it > 1f }?.let { periodoVsyncNs = (1_000_000_000.0 / it).toLong() }
        ultimoFrameNs = 0L
        val hilo = HandlerThread("PianoRender", Process.THREAD_PRIORITY_DISPLAY).also { it.start() }
        hiloRender = hilo
        val h = Handler(hilo.looper)
        manejadorRender = h
        // Choreographer es por hilo: obtenerlo DENTRO del hilo de render da vsync propio.
        h.post {
            frameProgramado = false
            programarFrame()
            h.postDelayed(sondeo, PERIODO_SONDEO_MS)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = solicitarFrame()

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Esperar a que termine el frame en curso: tras volver de aquí la superficie no es válida.
        synchronized(bloqueoSuperficie) { superficieLista = false }
        manejadorRender = null
        hiloRender?.quitSafely()
        hiloRender?.join()
        hiloRender = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        anchoVista = w
        altoVista = h
        recalcularGeometria()
        prepararSombreadores()
        excluirGestosDelSistema()
        if (!centradoInicial && w > 0) {
            centradoInicial = true
            centrarEnNota(60)
        }
        limitarScroll()
        solicitarFrame()
    }

    private fun recalcularGeometria() {
        if (anchoVista == 0) return
        anchoBlanca = anchoVista.toFloat() / zoomActual
        altoMinimapa = 22f * densidad
        altoTeclado = G.altoTecladoDp(altoVista / densidad) * densidad
        topTeclado = altoVista - altoTeclado
    }

    /** Cambia el zoom manteniendo fijo el punto del teclado que está bajo [focoX]. */
    private fun aplicarZoom(teclas: Int, focoX: Float) {
        val v = teclas.coerceIn(MIN_TECLAS_VISIBLES, G.TOTAL_BLANCAS)
        if (v == zoomActual) return
        val punto = (desplazamientoX + focoX) / max(anchoBlanca, 1f)
        zoomActual = v
        recalcularGeometria()
        desplazamientoX = punto * anchoBlanca - focoX
        limitarScroll()
        solicitarFrame()
    }

    /**
     * Un glissando que empieza en el borde izquierdo o derecho dispararía el gesto "atrás".
     * Android solo respeta [MAX_EXCLUSION_DP] de alto por borde: se cubre la parte baja del
     * teclado, que es la zona de las blancas y donde casi siempre empieza un glissando.
     * Solo se llama al cambiar de tamaño, nunca por frame (crea objetos).
     */
    private fun excluirGestosDelSistema() {
        val alto = min(altoTeclado, MAX_EXCLUSION_DP * densidad).toInt()
        // ViewCompat: la API existe desde Android 10; en versiones anteriores no hay gesto de borde.
        androidx.core.view.ViewCompat.setSystemGestureExclusionRects(
            this,
            listOf(android.graphics.Rect(0, altoVista - alto, anchoVista, altoVista)),
        )
    }

    fun centrarEnNota(nota: Int) {
        desplazamientoX = (G.xUnidades(nota) + 0.5f) * anchoBlanca - anchoVista / 2f
        limitarScroll()
        solicitarFrame()
    }

    /** Devuelve true si tuvo que recortar (se llegó a un extremo). */
    private fun limitarScroll(): Boolean {
        val limitado = desplazamientoX.coerceIn(0f, max(0f, anchoTotal - anchoVista))
        val recortado = limitado != desplazamientoX
        desplazamientoX = limitado
        return recortado
    }

    /** @return true si hace falta otro frame (algo se mueve o se anima). */
    private fun renderizar(frameTimeNanos: Long): Boolean {
        synchronized(bloqueoSuperficie) {
            if (!superficieLista) return false
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: IllegalStateException) {
                null
            } ?: return true // superficie aún no disponible: reintentar en el próximo vsync
            try {
                return dibujar(canvas, frameTimeNanos)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Inercia del desplazamiento (escrita por el hilo UI al soltar, consumida aquí)
    // ---------------------------------------------------------------------------------
    /** px/s; 0 = quieto. */
    @Volatile private var velocidadInercia = 0f
    private var ultimoFrameInercia = 0L

    private fun aplicarInercia(frameTimeNanos: Long) {
        val v = velocidadInercia
        if (v == 0f) {
            ultimoFrameInercia = 0L
            return
        }
        val dt = if (ultimoFrameInercia == 0L) 0f else ((frameTimeNanos - ultimoFrameInercia) / 1e9f).coerceAtMost(0.05f)
        ultimoFrameInercia = frameTimeNanos
        desplazamientoX += v * dt
        val siguiente = v * exp(-dt / TAU_INERCIA_S)
        // En un extremo, o ya casi quieto, se para (sin rebote: el teclado no es una lista).
        velocidadInercia = if (limitarScroll() || abs(siguiente) < VELOCIDAD_MIN_INERCIA_PX_S * densidad) 0f else siguiente
    }

    // ---------------------------------------------------------------------------------
    // Dibujo (hilo de render). Nada de `new` aquí dentro.
    // ---------------------------------------------------------------------------------
    private val reloj = RelojSnapshot()
    private val activasCancion = IntArray(128)
    private val rect = RectF()
    private val hud = CharArray(48)

    // Suelta de notas de la canción, detectada frame a frame (solo hilo de render).
    private val estadoPrevioCancion = IntArray(128)
    private val instanteSueltaCancion = LongArray(128)
    private val manoSueltaCancion = IntArray(128)

    // Resplandor de impacto por tecla en este frame: intensidad 0..1 y mano (ordinal).
    private val brilloImpacto = FloatArray(128)
    private val manoImpacto = IntArray(128)

    private val pRelleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pSombreado = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pLinea = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = COLOR_BORDE_NOTA
    }
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

    // Sombreadores: solo dependen de la geometría vertical, se crean en onSizeChanged.
    @Volatile private var sTeclaBlanca: Shader? = null
    @Volatile private var sTeclaNegra: Shader? = null
    @Volatile private var sResplandorLinea: Shader? = null
    // [mano][0 = blanca, 1 = negra]
    private val sNota = arrayOfNulls<Shader>(4)
    private val sBrillo = arrayOfNulls<Shader>(2)

    /** Hilo UI, en onSizeChanged: crea los degradados (crear un Shader asigna memoria). */
    private fun prepararSombreadores() {
        if (altoVista == 0) return
        val top = topTeclado
        val h = altoVista.toFloat()
        val altoNegra = altoTeclado * G.PROPORCION_ALTO_NEGRA
        val impactoY = topTeclado
        val topCascada = altoMinimapa
        sTeclaBlanca = LinearGradient(0f, top, 0f, h, COLOR_TECLA_BLANCA, COLOR_TECLA_BLANCA_ABAJO, Shader.TileMode.CLAMP)
        sTeclaNegra = LinearGradient(
            0f, top, 0f, top + altoNegra,
            intArrayOf(COLOR_TECLA_NEGRA_BRILLO, COLOR_TECLA_NEGRA, COLOR_TECLA_NEGRA_ABAJO),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
        )
        sResplandorLinea = LinearGradient(
            0f, impactoY - ALTO_RESPLANDOR_LINEA_DP * densidad, 0f, impactoY,
            Paleta.conAlfa(Paleta.CIAN, 0), Paleta.conAlfa(Paleta.CIAN, 0x40), Shader.TileMode.CLAMP,
        )
        for (mano in 0..1) {
            for (negra in 0..1) {
                val base = colorNota(mano, negra == 1)
                // Más apagadas arriba, plenas al llegar a la línea: se lee hacia dónde caen.
                sNota[mano * 2 + negra] = LinearGradient(
                    0f, topCascada, 0f, impactoY,
                    mezclar(base, COLOR_FONDO, 0.35f), base, Shader.TileMode.CLAMP,
                )
            }
            val color = colorTeclaIluminada(mano)
            sBrillo[mano] = LinearGradient(
                0f, impactoY - ALTO_BRILLO_DP * densidad, 0f, impactoY,
                conAlfa(color, 0), conAlfa(color, 0xE6), Shader.TileMode.CLAMP,
            )
        }
    }

    /** @return true si hace falta otro frame. */
    private fun dibujar(c: Canvas, frameTimeNanos: Long): Boolean {
        val w = anchoVista.toFloat()
        val h = altoVista.toFloat()
        if (w <= 0f || h <= 0f) return false
        val sx = desplazamientoX
        val ww = anchoBlanca
        val impactoY = topTeclado
        val topCascada = altoMinimapa
        val altoCascada = impactoY - topCascada
        val reducido = movimientoReducido

        c.drawColor(COLOR_FONDO)

        // --- Reloj: lo que el usuario ve debe coincidir con lo que OYE ---
        // frameTimeNanos = vsync; el frame se muestra ~1 vsync después; el audio
        // generado ahora sale por el altavoz `latencia` ms más tarde.
        fuenteReloj?.invoke(reloj)
        tickDibujado = reloj.tick
        bpmDibujado = reloj.bpm
        val instante = frameTimeNanos + periodoVsyncNs - (latenciaAudioMs * 1_000_000.0).toLong()
        val tick = max(0.0, reloj.tickAt(instante))
        var animando = reloj.running || velocidadInercia != 0f

        val p = partitura
        val ticksPorTiempo = p?.ticksPorTiempo ?: PPQ
        val ticksPorCompas = p?.ticksPorCompas ?: (PPQ * 4)
        val ticksVisibles = (ticksPorCompas * compasesVisibles.coerceAtLeast(1)).toDouble()
        val pxPorTick = (altoCascada / ticksVisibles).toFloat()
        // El resplandor dura lo mismo en segundos sea cual sea el tempo.
        val ticksBrillo = max(1.0, reloj.bpm * PPQ / 60.0 * DURACION_BRILLO_S)

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
        java.util.Arrays.fill(brilloImpacto, 0f)
        if (p != null) dibujarNotas(c, p, tick, tickTope, impactoY, pxPorTick, sx, ww, w, if (reducido) 0.0 else ticksBrillo)

        // --- Línea de impacto: un halo suave encima, no una raya plana ---
        pSombreado.shader = sResplandorLinea
        pSombreado.alpha = 255
        c.drawRect(0f, impactoY - ALTO_RESPLANDOR_LINEA_DP * densidad, w, impactoY, pSombreado)
        c.restore()
        pLinea.color = COLOR_IMPACTO
        pLinea.strokeWidth = 2f * densidad
        c.drawLine(0f, impactoY, w, impactoY, pLinea)
        if (dibujarBrillos(c, sx, ww, w)) animando = true

        if (dibujarTeclado(c, sx, ww, w, h, frameTimeNanos, reducido)) animando = true
        if (dibujarDestellos(c, sx, ww, w, frameTimeNanos)) animando = true
        dibujarMinimapa(c, sx, w)
        dibujarHud(c, tick, ticksPorTiempo, ticksPorCompas, w, topCascada)
        return animando
    }

    private fun dibujarNotas(
        c: Canvas, p: Partitura, tick: Double, tickTope: Double,
        impactoY: Float, pxPorTick: Float, sx: Float, ww: Float, w: Float, ticksBrillo: Double,
    ) {
        val radio = 5f * densidad
        val margen = max(1f, ww * 0.06f)
        val hueco = HUECO_NOTAS_DP * densidad
        pBorde.strokeWidth = densidad
        var i = p.primerIndiceDesde(tick - p.maxDuracion)
        while (i < p.n && p.inicio[i] <= tickTope) {
            val ini = p.inicio[i]
            val fin = p.fin[i]
            if (fin >= tick) {
                val nota = p.nota[i]
                val mano = p.mano[i]
                if (ini <= tick && tick < fin) activasCancion[nota] = mano + 1
                // Recién llegada a la línea: resplandor que se apaga en DURACION_BRILLO_S.
                if (ticksBrillo > 0.0 && ini <= tick && tick - ini < ticksBrillo) {
                    val f = (1.0 - (tick - ini) / ticksBrillo).toFloat()
                    if (f > brilloImpacto[nota]) {
                        brilloImpacto[nota] = f
                        manoImpacto[nota] = mano
                    }
                }

                val x = G.xUnidades(nota) * ww - sx
                val ancho = G.anchoUnidades(nota) * ww
                if (x + ancho >= 0f && x <= w) {
                    val yAbajo = impactoY - ((ini - tick) * pxPorTick).toFloat()
                    // El hueco va en el extremo final: dos notas seguidas de la misma tecla
                    // se ven como dos barras, no como una sola larga.
                    val yArriba = min(impactoY - ((fin - tick) * pxPorTick).toFloat() + hueco, yAbajo - 1f)
                    rect.set(x + margen, yArriba, x + ancho - margen, yAbajo)
                    pSombreado.shader = sNota[mano * 2 + (if (G.esNegra[nota]) 1 else 0)]
                    pSombreado.alpha = 255
                    c.drawRoundRect(rect, radio, radio, pSombreado)
                    rect.inset(densidad / 2f, densidad / 2f)
                    c.drawRoundRect(rect, radio, radio, pBorde)
                }
            }
            i++
        }
    }

    /** Resplandor del color de la mano sobre la línea, encima de cada tecla que acaba de recibir su nota. */
    private fun dibujarBrillos(c: Canvas, sx: Float, ww: Float, w: Float): Boolean {
        val alto = ALTO_BRILLO_DP * densidad
        val top = topTeclado
        var alguno = false
        for (nota in G.PRIMERA..G.ULTIMA) {
            val f = brilloImpacto[nota]
            if (f <= 0f) continue
            alguno = true
            val x = G.xUnidades(nota) * ww - sx
            val ancho = G.anchoUnidades(nota) * ww
            if (x + ancho < 0f || x > w) continue
            pSombreado.shader = sBrillo[manoImpacto[nota]]
            pSombreado.alpha = (f * 255).toInt()
            // Algo más ancho que la tecla: se lee como luz, no como otro rectángulo.
            val extra = ancho * 0.25f
            rect.set(x - extra, top - alto, x + ancho + extra, top)
            c.drawRoundRect(rect, alto / 2f, alto / 2f, pSombreado)
        }
        pSombreado.alpha = 255
        return alguno
    }

    /** @return true si alguna tecla está desvaneciéndose. */
    private fun dibujarTeclado(c: Canvas, sx: Float, ww: Float, w: Float, h: Float, ahora: Long, reducido: Boolean): Boolean {
        val top = topTeclado
        val altoNegra = altoTeclado * G.PROPORCION_ALTO_NEGRA
        val primera = max(0, floor(sx / ww).toInt())
        val ultima = min(G.TOTAL_BLANCAS - 1, ceil((sx + w) / ww).toInt())
        val radio = 4f * densidad
        val hundimiento = HUNDIMIENTO_DP * densidad
        val labio = LABIO_DP * densidad
        var desvaneciendo = false

        // Sueltas de la canción: comparar con el frame anterior (todas las teclas, no solo las visibles).
        for (nota in G.PRIMERA..G.ULTIMA) {
            val ahoraCancion = activasCancion[nota]
            val antes = estadoPrevioCancion[nota]
            if (antes != 0 && ahoraCancion == 0) {
                instanteSueltaCancion[nota] = ahora
                manoSueltaCancion[nota] = antes
            }
            estadoPrevioCancion[nota] = ahoraCancion
        }

        // Recorte: las teclas se dibujan con esquinas redondeadas y su borde superior
        // queda oculto bajo la línea de impacto -> arriba rectas, abajo redondeadas.
        c.save()
        c.clipRect(0f, top, w, h)

        // Blancas: cara con degradado, labio inferior en sombra. Pulsada: se hunde (cara más
        // corta, labio más oscuro) y se tiñe del color de la mano.
        pLinea.color = COLOR_SEPARADOR
        pLinea.strokeWidth = 1f
        pTexto.color = COLOR_ETIQUETA
        for (wi in primera..ultima) {
            val nota = G.notaBlanca[wi]
            val x = wi * ww - sx
            val estado = estadoTecla(nota)
            val desvanecido = if (estado == 0 && !reducido) intensidadSuelta(nota, ahora) else 0f
            if (desvanecido > 0f) desvaneciendo = true
            val abajo = if (estado != 0) h - hundimiento else h
            val color = when {
                estado != 0 -> colorTeclaIluminada(estado - 1)
                desvanecido > 0f -> mezclar(COLOR_TECLA_BLANCA, colorTeclaIluminada(manoDeSuelta(nota)), desvanecido)
                else -> 0
            }
            rect.set(x + 0.5f, top - radio, x + ww - 0.5f, abajo)
            val conFlash = if (color != 0) conResultado(nota, color, ahora) else conResultado(nota, COLOR_TECLA_BLANCA, ahora)
            if (color == 0 && conFlash == COLOR_TECLA_BLANCA) {
                pSombreado.shader = sTeclaBlanca
                pSombreado.alpha = 255
                c.drawRoundRect(rect, radio, radio, pSombreado)
            } else {
                pRelleno.color = conFlash
                c.drawRoundRect(rect, radio, radio, pRelleno)
            }
            // Labio frontal: da grosor a la tecla.
            pRelleno.color = if (estado != 0) COLOR_LABIO_HUNDIDO else COLOR_LABIO
            rect.set(x + 0.5f, abajo - labio, x + ww - 0.5f, abajo)
            c.drawRoundRect(rect, radio, radio, pRelleno)
            c.drawLine(x, top, x, h, pLinea)
            etiquetas[nota]?.let { c.drawText(it, x + ww / 2f, abajo - labio - 6f * densidad, pTexto) }
        }

        // Negras (encima de las blancas), con su sombra proyectada sobre ellas.
        val anchoNegra = ww * G.PROPORCION_ANCHO_NEGRA
        val desplSombra = SOMBRA_NEGRA_DP * densidad
        for (wi in max(0, primera - 1)..ultima) {
            val nota = G.notaBlanca[wi] + 1
            if (nota > G.ULTIMA || !G.esNegra[nota]) continue
            val x = (wi + 1) * ww - anchoNegra / 2f - sx
            val estado = estadoTecla(nota)
            val desvanecido = if (estado == 0 && !reducido) intensidadSuelta(nota, ahora) else 0f
            if (desvanecido > 0f) desvaneciendo = true
            val fondo = top + altoNegra - if (estado != 0) hundimiento else 0f

            pRelleno.color = COLOR_SOMBRA_NEGRA
            rect.set(x + desplSombra * 0.5f, top, x + anchoNegra + desplSombra, fondo + desplSombra * (if (estado != 0) 0.4f else 1f))
            c.drawRoundRect(rect, radio, radio, pRelleno)

            rect.set(x, top - radio, x + anchoNegra, fondo)
            val color = when {
                estado != 0 -> colorTeclaIluminada(estado - 1)
                desvanecido > 0f -> mezclar(COLOR_TECLA_NEGRA, colorTeclaIluminada(manoDeSuelta(nota)), desvanecido)
                else -> 0
            }
            val conFlash = if (color != 0) conResultado(nota, color, ahora) else conResultado(nota, COLOR_TECLA_NEGRA, ahora)
            if (color == 0 && conFlash == COLOR_TECLA_NEGRA) {
                pSombreado.shader = sTeclaNegra
                pSombreado.alpha = 255
                c.drawRoundRect(rect, radio, radio, pSombreado)
            } else {
                pRelleno.color = conFlash
                c.drawRoundRect(rect, radio, radio, pRelleno)
            }
            // Bisel frontal más claro: la luz cae sobre el borde de la tecla.
            pRelleno.color = if (estado != 0) COLOR_BISEL_HUNDIDO else COLOR_BISEL_NEGRA
            rect.set(x + densidad, fondo - labio, x + anchoNegra - densidad, fondo)
            c.drawRoundRect(rect, radio / 2f, radio / 2f, pRelleno)
        }
        c.restore()
        return desvaneciendo
    }

    /**
     * Desvanecido tras soltar: 1 al soltar, 0 a los [DURACION_SUELTA_NS]. Toma la suelta más
     * reciente entre la del usuario (atómica, hilo UI) y la de la canción (este hilo).
     */
    private fun intensidadSuelta(nota: Int, ahora: Long): Float {
        val instante = max(instanteSuelta.get(nota), instanteSueltaCancion[nota])
        if (instante == 0L) return 0f
        val t = (ahora - instante).toFloat() / DURACION_SUELTA_NS
        return if (t >= 1f || t < 0f) 0f else 1f - t
    }

    private fun manoDeSuelta(nota: Int): Int {
        val usuario = instanteSuelta.get(nota) >= instanteSueltaCancion[nota]
        val m = if (usuario) manoSuelta.get(nota) else manoSueltaCancion[nota]
        return max(0, m - 1)
    }

    /** Halo sobre la línea de impacto encima de cada tecla con un resultado reciente. */
    private fun dibujarDestellos(c: Canvas, sx: Float, ww: Float, w: Float, ahora: Long): Boolean {
        val alto = ALTO_DESTELLO_DP * densidad
        val top = topTeclado
        var alguno = false
        for (nota in G.PRIMERA..G.ULTIMA) {
            val f = intensidadResultado(nota, ahora)
            if (f <= 0f) continue
            alguno = true
            val x = G.xUnidades(nota) * ww - sx
            val ancho = G.anchoUnidades(nota) * ww
            if (x + ancho < 0f || x > w) continue
            pRelleno.color = conAlfa(colorResultado(nota), (f * 200).toInt())
            rect.set(x, top - alto, x + ancho, top)
            c.drawRect(rect, pRelleno)
        }
        return alguno
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
        n = escribirEntero(hud, n, reloj.bpm.roundToInt(), 1)
        for (ch in " BPM") hud[n++] = ch
        c.drawText(hud, 0, n, w - 12f * densidad, top + 22f * densidad, pHud)
    }

    private fun estadoTecla(nota: Int): Int {
        val tocada = teclasTocadas.get(nota)
        return if (tocada != 0) tocada else activasCancion[nota]
    }

    // ---------------------------------------------------------------------------------
    // Entrada táctil (hilo UI): multitouch, glissando, desplazamiento con inercia, pellizco.
    // ---------------------------------------------------------------------------------
    private val modoPuntero = IntArray(MAX_PUNTEROS) { LIBRE } // >= 0: nota que mantiene
    private val ultimoX = FloatArray(MAX_PUNTEROS)
    private val dedosPorNota = IntArray(128)
    private var rastreador: VelocityTracker? = null

    // Pellizco: dos dedos sobre la cascada. Sobre el teclado dos dedos son dos notas.
    private var pellizcando = false
    private var dedoA = -1
    private var dedoB = -1
    private var distanciaInicial = 1f
    private var zoomInicial = 21

    @SuppressLint("ClickableViewAccessibility") // performClick se llama en ACTION_UP
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            rastreador?.clear()
            if (rastreador == null) rastreador = VelocityTracker.obtain()
        }
        rastreador?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                empezarPuntero(e.getPointerId(i), e.getX(i), e.getY(i))
                intentarEmpezarPellizco(e)
            }
            MotionEvent.ACTION_MOVE -> {
                if (pellizcando) moverPellizco(e)
                for (i in 0 until e.pointerCount) moverPuntero(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                val eraPellizco = pellizcando && (id == dedoA || id == dedoB)
                if (eraPellizco) terminarPellizco(e, id)
                // Al deshacer un pellizco no hay lanzamiento: el dedo se movía para hacer zoom.
                if (modoPuntero.getOrElse(id) { LIBRE } == ARRASTRE && !eraPellizco && !pellizcando) lanzarInercia(id)
                terminarPuntero(id)
                if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (pellizcando) {
                    pellizcando = false
                    listener?.onZoom(zoomActual)
                }
                for (id in 0 until MAX_PUNTEROS) terminarPuntero(id)
            }
        }
        solicitarFrame()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        rastreador?.recycle()
        rastreador = null
        super.onDetachedFromWindow()
    }

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
                velocidadInercia = 0f
                modoPuntero[id] = MINIMAPA
                saltarConMinimapa(x)
            }
            else -> {
                // Atrapar el teclado en movimiento lo detiene en el sitio.
                velocidadInercia = 0f
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
                    // Fuera del teclado el dedo sigue "tocando": si vuelve a bajar, ataca la tecla.
                    modoPuntero[id] = if (nueva >= 0) nueva else FUERA_DEL_TECLADO
                    if (nueva >= 0) pulsar(nueva, velocidadPara(nueva, y))
                }
            }
            modo == FUERA_DEL_TECLADO -> if (y >= topTeclado) {
                val nota = notaEn(x + desplazamientoX, y)
                if (nota >= 0) {
                    modoPuntero[id] = nota
                    pulsar(nota, velocidadPara(nota, y))
                }
            }
            modo == MINIMAPA -> saltarConMinimapa(x)
            modo == ARRASTRE -> {
                if (!pellizcando) {
                    desplazamientoX -= x - ultimoX[id]
                    limitarScroll()
                }
                ultimoX[id] = x
            }
        }
    }

    private fun terminarPuntero(id: Int) {
        if (id !in 0 until MAX_PUNTEROS) return
        val modo = modoPuntero[id]
        if (modo >= 0) soltar(modo)
        modoPuntero[id] = LIBRE
    }

    /** Al soltar un arrastre: el teclado sigue con la velocidad del dedo y frena solo. */
    private fun lanzarInercia(id: Int) {
        if (movimientoReducido) return
        val r = rastreador ?: return
        r.computeCurrentVelocity(1000)
        val vx = r.getXVelocity(id)
        // El contenido va al revés que desplazamientoX (arrastrar a la derecha lo reduce).
        velocidadInercia = if (abs(vx) > VELOCIDAD_MIN_LANZAMIENTO_PX_S * densidad) -vx else 0f
    }

    private fun intentarEmpezarPellizco(e: MotionEvent) {
        if (pellizcando) return
        var a = -1
        var b = -1
        for (i in 0 until e.pointerCount) {
            val id = e.getPointerId(i)
            if (modoPuntero.getOrElse(id) { LIBRE } != ARRASTRE) continue
            if (a < 0) a = id else if (b < 0) b = id
        }
        if (a < 0 || b < 0) return
        val ia = e.findPointerIndex(a)
        val ib = e.findPointerIndex(b)
        if (ia < 0 || ib < 0) return
        pellizcando = true
        velocidadInercia = 0f
        dedoA = a
        dedoB = b
        distanciaInicial = max(abs(e.getX(ia) - e.getX(ib)), DISTANCIA_MIN_PELLIZCO_DP * densidad)
        zoomInicial = zoomActual
    }

    private fun moverPellizco(e: MotionEvent) {
        val ia = e.findPointerIndex(dedoA)
        val ib = e.findPointerIndex(dedoB)
        if (ia < 0 || ib < 0) return
        val xa = e.getX(ia)
        val xb = e.getX(ib)
        val distancia = max(abs(xa - xb), DISTANCIA_MIN_PELLIZCO_DP * densidad)
        // Separar los dedos acerca (menos teclas visibles); juntarlos aleja.
        aplicarZoom((zoomInicial * distanciaInicial / distancia).roundToInt(), (xa + xb) / 2f)
    }

    private fun terminarPellizco(e: MotionEvent, sale: Int) {
        pellizcando = false
        // El dedo que queda sigue arrastrando desde donde está, sin salto.
        val queda = if (sale == dedoA) dedoB else dedoA
        val iq = e.findPointerIndex(queda)
        if (iq >= 0 && queda in 0 until MAX_PUNTEROS) ultimoX[queda] = e.getX(iq)
        dedoA = -1
        dedoB = -1
        listener?.onZoom(zoomActual)
    }

    private fun manoDe(nota: Int) = if (nota < notaDivision) Mano.IZQUIERDA else Mano.DERECHA

    private fun pulsar(nota: Int, velocidad: Int) {
        val mano = manoDe(nota)
        dedosPorNota[nota]++
        teclasTocadas.set(nota, mano.ordinal + 1)
        listener?.onNoteOn(nota, velocidad, mano)
        if (vibrarAlTocar) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun soltar(nota: Int) {
        if (dedosPorNota[nota] == 0) return
        // Con dos dedos en la misma tecla, solo se apaga al levantar el último.
        if (--dedosPorNota[nota] == 0) {
            val mano = manoDe(nota)
            manoSuelta.set(nota, mano.ordinal + 1)
            instanteSuelta.set(nota, System.nanoTime())
            teclasTocadas.set(nota, 0)
            listener?.onNoteOff(nota, mano)
        }
    }

    /**
     * Velocidad según dónde se toca la tecla: cerca del borde frontal = más fuerte,
     * como en un piano real (más palanca). Más fiable que la presión táctil, que
     * muchos teléfonos no reportan. La curva la elige el usuario ([sensibilidad]).
     */
    private fun velocidadPara(nota: Int, y: Float): Int {
        if (!dinamicaTactil) return VELOCIDAD_FIJA
        val alto = if (G.esNegra[nota]) altoTeclado * G.PROPORCION_ALTO_NEGRA else altoTeclado
        val rel = ((y - topTeclado) / alto).coerceIn(0f, 1f)
        return sensibilidad.velocidad(rel)
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
        /** Dedo que empezó en una tecla y salió hacia la cascada: sigue vivo para el glissando. */
        const val FUERA_DEL_TECLADO = -4
        const val MIN_TECLAS_VISIBLES = 10
        const val VELOCIDAD_FIJA = 100
        /** Límite de Android para la exclusión de gestos por borde (View.setSystemGestureExclusionRects). */
        const val MAX_EXCLUSION_DP = 200f
        /** Periodo inicial (60 Hz) hasta medir los primeros frames. */
        const val PERIODO_VSYNC_INICIAL_NS = 16_666_667L
        /** Deltas fuera de este rango son frames perdidos o pausas, no el periodo del display. */
        const val PERIODO_MIN_NS = 4_000_000L
        const val PERIODO_MAX_NS = 50_000_000L
        /** Cada cuánto se mira el reloj estando en reposo (sin redibujar). */
        const val PERIODO_SONDEO_MS = 50L

        // Aspecto del teclado
        const val HUNDIMIENTO_DP = 2.5f
        const val LABIO_DP = 4f
        const val SOMBRA_NEGRA_DP = 3f
        const val DURACION_SUELTA_NS = 100_000_000f

        // Cascada
        const val HUECO_NOTAS_DP = 2f
        const val ALTO_BRILLO_DP = 28f
        const val DURACION_BRILLO_S = 0.25
        const val ALTO_RESPLANDOR_LINEA_DP = 10f

        // Inercia y pellizco
        /** Constante de tiempo de la frenada: ~0,33 s, como el desplazamiento de iOS. */
        const val TAU_INERCIA_S = 0.33f
        const val VELOCIDAD_MIN_LANZAMIENTO_PX_S = 80f
        const val VELOCIDAD_MIN_INERCIA_PX_S = 12f
        const val DISTANCIA_MIN_PELLIZCO_DP = 48f

        // Colores: salen de la misma Paleta que usa el tema de Compose.
        const val COLOR_FONDO = Paleta.FONDO.toInt()
        const val COLOR_TECLA_BLANCA = Paleta.BLANCO.toInt()
        const val COLOR_TECLA_BLANCA_ABAJO = 0xFFE6E6E6.toInt()
        const val COLOR_TECLA_NEGRA = Paleta.SUPERFICIE.toInt()
        const val COLOR_TECLA_NEGRA_BRILLO = Paleta.SUPERFICIE_MAXIMA.toInt()
        const val COLOR_TECLA_NEGRA_ABAJO = Paleta.NEGRO_PROFUNDO.toInt()
        const val COLOR_BISEL_NEGRA = Paleta.GRIS_OSCURO.toInt()
        const val COLOR_BISEL_HUNDIDO = Paleta.SUPERFICIE_ALTA.toInt()
        const val COLOR_LABIO = Paleta.GRIS_SEPARADOR.toInt()
        const val COLOR_LABIO_HUNDIDO = Paleta.GRIS_MEDIO.toInt()
        val COLOR_SOMBRA_NEGRA = Paleta.conAlfa(Paleta.NEGRO, 0x59)
        val COLOR_BORDE_NOTA = Paleta.conAlfa(Paleta.BLANCO, 0x4D)
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
