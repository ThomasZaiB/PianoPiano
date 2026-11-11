# Encargo: corrección de bugs y mejora de UI, animaciones y sonido en PianoPiano

Vas a trabajar como ingeniero de software sénior (Android + audio en tiempo real) sobre
PianoPiano, una app de piano para Android. Otro ingeniero ya revisó el código completo y
dejó este documento con lo que encontró. Tu trabajo es implementar los cambios, paquete
por paquete, dejando el proyecto compilando y con los tests en verde después de cada uno.

Lee el documento entero antes de tocar nada. Los números de línea son aproximados (de la
revisión del 2026-11-11): abre siempre el archivo y confirma el problema en el código
antes de corregirlo. Si al leer el código ves que un hallazgo no es cierto o que la
corrección propuesta rompería otra cosa, no la fuerces: déjalo sin tocar y explícalo en
el informe final.

## 1. El proyecto

- Carpeta de trabajo: la raíz del proyecto (`PianoApp`). Módulo único `:app`,
  paquete `com.tuapp.piano`.
- Kotlin 2.1 + Jetpack Compose (Material 3) + Hilt + Room + Navigation Compose.
  `minSdk 26`, `targetSdk 35`.
- Motor de audio propio en C++17 (`app/src/main/cpp/`): Oboe + TinySoundFont.
  Kotlin habla con él por JNI a través de `data/audio/AudioBridge.kt`
  (interfaz `domain/repository/AudioEngineRepository.kt`).
- Teclado de 88 teclas y cascada de notas dibujados en un `SurfaceView` con hilo de
  render propio: `presentation/ui/piano/PianoSurfaceView.kt`.
- Cuatro modos de piano (`LIBRE`, `GRABAR`, `REPRODUCIR`, `PRACTICA`) en
  `presentation/ui/PianoScreen.kt` + `presentation/viewmodel/PianoViewModel.kt`.
- El código, los comentarios y los nombres están en español. Mantén ese idioma y el
  estilo de los comentarios existentes (explican el porqué, no el qué).
- El `README.md` dice que no se modifican `cpp/`, `PianoSurfaceView` ni `AudioBridge`.
  Esa restricción era de una fase anterior y **queda levantada para este encargo**:
  varios arreglos solo se pueden hacer ahí. Actualiza el README al terminar.

### Reglas del motor que no se negocian

El callback de audio (`AudioEngine::onAudioReady` y todo lo que llama) corre en un hilo
de tiempo real. Un bloqueo de pocos milisegundos ahí se oye como un chasquido. Por eso,
en ese hilo: nada de locks, `new`/`malloc`, JNI, logs ni E/S. Toda memoria que necesite
un efecto nuevo se reserva antes, con el stream cerrado (`SoundFontPlayer::configure` o
equivalente). Los comandos llegan por el ring buffer SPSC como `EngineEvent` de 16 bytes;
si añades tipos de evento, `EventType` en `EngineEvent.h`, `kEventTypeCount` y las
constantes `EV_*` de `AudioBridge.kt` tienen que coincidir. No edites
`third_party/tinysoundfont/`.

El hilo de render de `PianoSurfaceView` sigue una regla parecida: cero asignaciones por
frame (objetos `Paint`, `RectF` y arrays ya están preasignados; sigue ese patrón).

## 2. Entorno y verificación

- Windows 11, shell PowerShell. **No arranques el emulador ni instales en un
  dispositivo**: la máquina no lo aguanta. La prueba visual y auditiva la hace el
  usuario. Tú verificas con compilación y tests.
- El proyecto no tiene `gradlew`. Usa el Gradle 8.11.1 ya descargado y el JDK 21 (el
  JBR de Android Studio es Java 25 y Gradle 8.11 no lo soporta):

  ```powershell
  $g = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.11.1-bin" -Recurse -Filter gradle.bat | Select-Object -First 1 -ExpandProperty FullName
  $env:JAVA_HOME = "$env:USERPROFILE\.jdks\jbr-21.0.11"
  & $g --offline :app:testDebugUnitTest :app:assembleDebug --console=plain
  ```

  `assembleDebug` compila también el C++ para tres ABI y tarda; lánzalo en segundo
  plano. Mientras solo toques Kotlin puedes usar `:app:compileDebugKotlin` para iterar
  rápido, pero cada paquete se cierra con `testDebugUnitTest` + `assembleDebug`.
- Estado de partida: compila y los 36 tests de `app/src/test/.../LogicaDominioTest.kt`
  pasan.
- No añadas dependencias nuevas salvo que un punto lo pida. No descargues SoundFonts,
  fuentes ni otros recursos de internet.
- La carpeta no es un repositorio git. Antes de empezar: `git init`, crea un
  `.gitignore` de Android (como mínimo `build/`, `app/build/`, `.gradle/`, `.kotlin/`,
  `.idea/`, `app/.cxx/`, `local.properties`, `apk/`, `*.apk`) y haz un commit base. Después, un
  commit por paquete de trabajo, para que cualquier cambio se pueda revertir solo.

### Cómo trabajar

1. Sigue el orden de los paquetes: están ordenados por riesgo para el usuario. P1 y P2
   son obligatorios. P3 a P5 hazlos en orden hasta donde llegues con calidad; es mejor
   dejar un paquete sin empezar que dejarlo a medias.
2. La lógica que se pueda probar sin Android (dominio, matemática de efectos, máquinas
   de estado) va con test unitario en `app/src/test/`. Si para probar algo hay que
   extraerlo del ViewModel a una clase pura, hazlo.
3. Casi nada de audio ni de dibujo se puede comprobar compilando. Sé conservador en esos
   cambios, deja los valores ajustables como constantes con nombre, y apunta cada cosa
   que requiera prueba en dispositivo (ver sección 9).
4. No hagas refactors que no pida ningún punto. Si ves un bug que no está en la lista,
   corrígelo solo si es pequeño y está en un archivo que ya estás tocando; si no,
   apúntalo en el informe.
5. Los textos visibles van en `res/values/strings.xml`, no en el código.

## 3. P1 — Bugs que afectan a quien usa la app

### 1.1 El pedal se queda puesto en el motor
`PianoViewModel.detenerTodo()` (~línea 291) pone `pedalAbajo = false` en la UI pero no
envía `motor.pedal(false)`. El motor es un singleton que conserva el sustain de cada
canal, y ni `GestorMotorAudio.liberar()` ni `AudioEngine::closeLocked()` lo reinician.
Resultado: sales del piano con el pedal pulsado y, al volver o al abrir otra canción,
las notas suenan sostenidas con el chip "Pedal" apagado.

Arréglalo en los dos lados: el ViewModel suelta el pedal al detener todo, y el motor
deja el sustain en reposo cuando se cierra o reconfigura el stream, para que ninguna
pantalla herede estado de otra.
*Hecho cuando:* no hay ningún camino (salir, segundo plano, cambiar de canción) que deje
el sustain activo con la UI indicando lo contrario.

### 1.2 Carreras en los comandos de transporte
En `PianoViewModel` varias funciones lanzan una corrutina sin dueño que espera en
`saltarA()` (hasta ~60 ms) y después arranca cosas, mientras el estado de la UI ya
cambió o puede cambiar por otra pulsación:

- `grabar()` (~470): marca `GRABANDO` al instante y el arranque real llega tras
  `saltarA`. Si en esa ventana se pulsa Pausa o Detener, `pausarGrabacion()` para todo y
  a continuación la corrutina pendiente ejecuta `arrancarToma()`: transporte y metrónomo
  corriendo con la UI en `PAUSADA`.
- `irATick()` (~427): captura `sonando` antes de suspender y relanza la reproducción
  aunque el usuario haya pausado entre medias. Dos saltos seguidos dejan dos corrutinas
  vivas: la primera programa notas para una posición que ya no es la del motor (suenan
  de golpe, porque un evento con tick pasado se dispara en el acto) y sus eventos ya
  encolados no se retiran.
- `iniciarReproduccion()` (~399), `alternarPractica()` rama LISTA/TERMINADA (~592),
  `reiniciarPractica()` (~620) y `terminarToma()` (~565) tienen la misma forma. Un doble
  toque en Play en práctica entra dos veces en la rama de arranque porque el estado no
  cambia hasta el final de la corrutina.

La causa es una sola: no hay un único dueño de "la operación de transporte en curso".
Corrígela de raíz en vez de parchear cada función: un solo `Job` de transporte que se
cancela con cada comando nuevo, estado de UI que refleja la intención desde el primer
momento, y volver a comprobar el estado después de cada punto de suspensión antes de
arrancar nada. Si te ayuda a probarlo, extrae la máquina de estados a una clase pura
con un `AudioEngineRepository` falso.
*Hecho cuando:* cualquier secuencia rápida de Play/Pausa/Saltar/Grabar/Detener deja el
motor y la UI en el mismo estado, y hay tests que cubren al menos pausa-durante-arranque
y dos saltos seguidos.

### 1.3 La pantalla se apaga durante la reproducción
En `REPRODUCIR` no hay toques, así que el teléfono atenúa y apaga la pantalla a mitad de
canción, y `onStop` la pausa. Mantén la pantalla encendida mientras `PianoScreen` está
visible (y solo ahí).

### 1.4 Los gestos del sistema interrumpen al tocar
`MainActivity` aplica `safeDrawingPadding()` y deja las barras del sistema visibles
también en el piano (en horizontal quitan alto útil), y no hay exclusión de gestos: un
glissando que empieza en el borde izquierdo o derecho dispara el gesto "atrás" de
Android. En la pantalla del piano: modo inmersivo (barras ocultas, reaparecen
deslizando) y `systemGestureExclusionRects` sobre la franja del teclado (Android limita
la exclusión a 200 dp de alto por borde; el teclado mide entre 110 y 260 dp, cubre lo
que quepa). Al salir del piano las barras vuelven. Respeta los recortes de pantalla
(notch) para que ninguna tecla ni botón quede debajo.

### 1.5 El inicio muestra un instante "graba tu primera canción"
`HomeScreen` pinta `Protagonista(ui.inicio)` sin mirar `ui.cargando`. El estado inicial
tiene `continuar = null`, así que en cada arranque en frío aparece la tarjeta de
biblioteca vacía hasta que Room emite, y cambia a mitad de la animación de entrada.
Mientras `cargando` sea true no muestres ninguna de las dos tarjetas (reserva el hueco o
usa un marcador neutro) y arranca la entrada escalonada cuando haya datos.

### 1.6 Doble toque = dos pantallas apiladas
Las llamadas a `navController.navigate(...)` de `PianoNavGraph.kt` no tienen protección.
Dos toques rápidos en una tarjeta apilan dos destinos (dos pianos, dos ViewModel
pidiendo el motor). Ignora la navegación si la entrada actual no está en `RESUMED`, en
un único sitio, no en cada lambda.

### 1.7 El carrusel se queda a medias
`CarruselRecientes.kt` (~131): si atrapas el carrusel en movimiento (`posicion.stop()`)
y levantas el dedo sin superar el umbral de arrastre, el gesto termina con
`return@awaitEachGesture` sin llamar a `asentar`. Queda parado entre dos tarjetas, o
fuera de rango si lo atrapaste durante el rebote. Asienta también en ese camino.

### 1.8 Trabajo pesado en el hilo principal
- `ImportarMidiUseCase` llama a `LectorMidi.leer(bytes)` (hasta 4 MB) desde
  `viewModelScope`, es decir, en el hilo principal.
- `ReproducirCancionUseCase.invoke` construye y ordena la lista completa de eventos en
  cada llamada, también en el hilo principal, y en práctica se llama en cada reanudación
  tras una espera (`reanudarTransporte`).

Mueve el análisis a `Dispatchers.Default` y calcula los eventos ordenados una vez por
canción, no en cada arranque. No cambies la semántica de la planificación (ventana de
0,25 s, Off antes que On en el mismo tick).

## 4. P2 — Robustez del audio

### 2.1 Reapertura del stream
`AudioEngine::onErrorAfterClose` (~118) hace `mStream.reset()` sin comprobar que el
stream que falló sea el actual. Si entre el error y el callback hubo `stop()` + `start()`
(la app pasó por segundo plano al desconectar unos auriculares), el callback atrasado
tira el stream nuevo. Compara con el puntero recibido antes de tocar `mStream`. Además,
si la reapertura falla, Kotlin no se entera y `GestorMotorAudio` sigue en `SONANDO` con
silencio: expón el estado real (basta con que el sondeo de diagnóstico que ya hace
`PianoViewModel` cada segundo detecte "sin stream" y reintente o muestre el error).

### 2.2 Foco de audio
La app no pide foco de audio ni reacciona a perderlo: una llamada o la música de otra
app se mezclan con el piano. Pide foco al adquirir el motor y pausa el transporte al
perderlo. Hazlo en la capa `data/` detrás de una interfaz de dominio, como el resto.

### 2.3 Tempo con decimales
`cargarCancion()` envía `motor.setTempo(cancion.bpm)` (Double) y `prepararMotor()` envía
`_ui.value.bpm.toDouble()` (redondeado a Int). Un MIDI importado a 92,3 BPM suena a 92,3
o a 92 según qué corrutina llegue primero, y la barra de progreso calcula el tiempo con
el entero. Usa una sola fuente de verdad para el tempo.

### 2.4 Detalles menores (agrúpalos en un commit)
- `CMakeLists.txt` (~25): `--gc-sections --strip-all` están en la rama Debug y deberían
  estar en Release (Debug con `-g` y símbolos eliminados no se puede depurar).
- `SemillasCallback.onOpen` (`PianoDatabase.kt` ~56): si falla la inserción de una
  canción incluida, la excepción sale de `onOpen` y la base no abre nunca más. Captura,
  registra y sigue con la siguiente.
- `PianoSurfaceView` (~480): un dedo que sale del teclado hacia la cascada y vuelve a
  bajar no vuelve a atacar ninguna tecla hasta levantarlo.
- `ADELANTO_DISPLAY_NS` está fijo a 60 Hz; calcula el periodo con la frecuencia real de
  la pantalla (90/120 Hz).
- Páginas de 16 KB: con `targetSdk 35` las bibliotecas nativas deben estar alineadas a
  16 KB para Android 15+. Consulta la documentación oficial de Android y de Oboe para la
  versión 1.9.0 del proyecto y NDK 27 antes de cambiar nada; no des por buena ninguna
  suposición sobre qué versión lo soporta. Si hace falta subir Oboe o añadir un
  argumento de CMake, hazlo y explícalo.

## 5. P3 — Sonido

Hoy el sonido es TinySoundFont con `demo-florestan.sf2` (531 KB, solo para pruebas),
ganancia fija de -6 dB y recorte duro a ±1. Mejoras, en este orden:

### 3.1 Limitador en vez de recorte duro
`SoundFontPlayer::render` (~82) hace `clamp` muestra a muestra. Con acordes densos y
pedal eso es distorsión digital audible. Sustitúyelo por un limitador de picos sencillo
(seguidor de envolvente con ataque de ~1 ms y relajación de ~100 ms, la misma reducción
en los dos canales) y deja el `clamp` solo como red de seguridad final. Sin memoria
dinámica. La matemática, en una función que se pueda razonar aislada.

### 3.2 Metrónomo nativo
`MetronomoUseCase` programa notas de piano (Do8 y Sol7) en el canal de la mano derecha.
Suena a piano agudo, no a metrónomo, y choca con esas dos teclas si el usuario las toca.
Genera el clic en el motor: un seno corto con caída exponencial (~30 ms; más agudo y
fuerte en el primer tiempo del compás), mezclado después del sintetizador y antes del
limitador, disparado por el propio hilo de audio a partir de la posición en ticks. Kotlin
solo activa o desactiva y pasa el compás (evento nuevo). El README ya preveía este
cambio; el ViewModel no debería necesitar más que la llamada nueva.

### 3.3 Reverberación
Sin reverberación un piano muestreado suena seco. Añade una ligera, tipo Freeverb
(filtros peine y pasa-todo por canal), con todos los búferes reservados en `configure()`
para la frecuencia real del stream, mezcla húmeda baja por defecto (~12–15 %) y un
ajuste en `AjustesScreen` (Apagada / Sala / Auditorio) que viaja al motor como evento.
Persiste el ajuste en `AjustesRepositoryImpl` como los demás.

### 3.4 Respuesta al toque
`velocidadPara()` reparte linealmente de 45 a 127 según la altura del toque. Añade en
Ajustes una curva de sensibilidad (Suave / Normal / Fuerte) aplicada ahí. Mantén "Normal"
idéntico al comportamiento actual.

### 3.5 Vibración
Opcional y apagada por defecto para las teclas (el motor de vibración añade ruido):
ajuste "Vibración al tocar". En práctica, una vibración corta de rechazo al fallar una
nota, activada por defecto. Usa `performHapticFeedback`; no pidas el permiso VIBRATE.

### 3.6 Lo que NO debes hacer aquí
No cambies ni descargues el SoundFont. Es la mejora de sonido más grande, pero depende
de licencias y de tamaño de APK y la decide el usuario (ver sección 8).

## 6. P4 — Teclado y cascada (`PianoSurfaceView`)

El teclado es plano: rectángulos de un color que cambian de golpe al color de la mano.
La cascada son rectángulos redondeados lisos. Objetivo: que se sienta como un
instrumento, sin perder los 60 FPS ni asignar memoria por frame. Crea los `Shader`,
`Paint` y tablas una sola vez (en `onSizeChanged` o al cambiar el zoom), nunca en
`dibujar()`.

1. **Teclas con volumen.** Blancas con degradado vertical muy sutil y sombra en el borde
   inferior; negras con brillo superior y sombra proyectada sobre las blancas. Al
   pulsar, la tecla se hunde 2–3 dp (se acorta la cara visible y se oscurece el borde
   inferior) además de teñirse.
2. **Transición al soltar.** Hoy el color desaparece en un frame. El ataque sigue siendo
   inmediato (la respuesta al toque no puede esperar), pero la suelta se desvanece en
   ~100 ms. Guarda el instante de suelta por tecla en un array atómico, como ya se hace
   con `instanteResultado`.
3. **Impacto.** Cuando una nota de la canción llega a la línea, un resplandor breve del
   color de su mano sobre la línea de impacto, encima de su tecla. La línea deja de ser
   una raya plana.
4. **Notas de la cascada.** Degradado vertical suave y borde claro fino. Dos notas
   seguidas de la misma tecla hoy se funden en una barra: deja un hueco visible de 2 dp.
5. **Desplazamiento con inercia y zoom con pellizco.** Arrastrar la cascada mueve el
   teclado y frena en seco al soltar, y el zoom solo existe en el menú ⋮ en pasos de 4
   teclas. Añade inercia al soltar y pellizco con dos dedos sobre la cascada (no sobre
   el teclado, donde dos dedos son dos notas). El zoom sigue guardándose en ajustes.
6. **No gastar batería en reposo.** El bucle del `Choreographer` redibuja siempre, aunque
   nada cambie. Con el transporte parado y sin toques ni animaciones pendientes, deja de
   reprogramar el frame y vuelve a pedirlo ante cualquier cambio (toque, ajuste, canción,
   destello, reloj en marcha). Es el punto más delicado del paquete: si no puedes
   garantizar que nunca queda una imagen congelada, déjalo sin hacer y apúntalo.
7. **Accesibilidad.** La vista no tiene `contentDescription`; ponle una que describa el
   teclado.

Todo lo anterior se reduce o desactiva si `movimientoReducido()` es true (pásalo a la
vista como propiedad; la vista no puede leer Compose).

## 7. P5 — Pantallas Compose

1. **Barra del piano (`PianoScreen.BarraSuperior`).** En 56 dp conviven título,
   transporte, estado, tempo, pedal y menú. En práctica, el texto que más importa ("Toca
   Do4 · Mi4", el error) va en `labelMedium`, limitado a 240 dp, en la esquina opuesta a
   donde el usuario mira. Lleva ese mensaje a un rótulo flotante sobre la cascada,
   centrado y cerca de la línea de impacto, y deja en la barra solo el marcador. Revisa
   que la barra no se desborde en un teléfono de 640 dp de ancho en horizontal: todos
   los objetivos táctiles de 48 dp como mínimo.
2. **Cuenta de entrada al grabar.** Una canción nueva empieza a grabar en el tick 0 sin
   aviso. Muestra una cuenta visual del primer compás (4-3-2-1, al ritmo del metrónomo)
   sobre la cascada. Solo en canciones nuevas, no en overdub. Esto además hace que la
   primera nota caiga de forma natural en el segundo compás, que es lo que espera
   `PreparadorGrabacion` (hoy, si la primera nota cae en el compás 0, la canción guardada
   empieza sin compás vacío de preparación; añade ese compás en ese caso y actualiza el
   test `cancionNueva_quitaElSilencioInicialDejandoUnCompas`).
3. **Indicador de grabación.** El punto rojo de "REC" late suavemente mientras graba.
4. **Barra de progreso.** Se actualiza 10 veces por segundo y avanza a saltos. Mientras
   el transporte corre, actualízala por frame (`withFrameNanos`); parada, no sondees.
5. **Resultado de práctica.** El porcentaje sube contando desde 0; con tres estrellas,
   una celebración breve (partículas en un `Canvas`, sin dependencias). `Estrella`,
   `ContadorRacha` y el destello de error ignoran hoy `movimientoReducido()`: respétalo.
6. **Biblioteca.** `Modifier.animateItem()` en las filas de la `LazyColumn` para que
   borrar y filtrar no sean saltos; tras borrar, snackbar con "Deshacer" (el diálogo de
   confirmación puede quedarse solo para el menú, o quitarse si el deshacer es fiable).
   Con más de ~10 canciones, campo de búsqueda por título.
7. **Textos fuera del código.** Hay cadenas visibles escritas en ViewModels y casos de
   uso (`PianoViewModel`, `LibraryViewModel`, `ImportacionMidi.kt`, `GestorMotorAudio`,
   `FiltroBiblioteca.etiqueta`). Los ViewModel emiten un tipo de mensaje y la pantalla
   resuelve el texto con `stringResource`. `FORMATO_FECHA` fuerza el idioma español: usa
   el del sistema.

## 8. Decisiones que son del usuario (no las implementes)

Déjalas descritas en el informe final con tu recomendación:

- **SoundFont de calidad.** Sustituir `demo-florestan.sf2` por un piano de cola con
  licencia compatible. Indica qué tamaño sería razonable y qué habría que comprobar de
  la licencia.
- **Canción con derechos de autor.** `assets/canciones/merry-go-round.txt` (Joe
  Hisaishi) no puede ir en una versión publicada, como ya avisa el README.
- **Etiqueta "Exportadas".** Exportar una canción la cambia de `CREADA` a `EXPORTADA` y
  desaparece del filtro "Creadas"; los MIDI importados cuentan como "Creadas". Propón un
  modelo más claro (origen: grabada / importada, y "exportada" como marca aparte), pero
  cambiarlo exige una migración de Room.
- **Importación MIDI.** Solo se usa el primer tempo (limitado en silencio a 40–240 BPM)
  y se ignora el pedal (CC64), así que algunos archivos suenan cortados o a otra
  velocidad. Estima el coste de soportarlo.

## 9. Entrega

Al terminar cada paquete: `testDebugUnitTest` + `assembleDebug` en verde y commit.
Al final, un informe breve en `docs/INFORME_MEJORAS.md` con:

1. Qué se hizo, por paquete y punto, y en qué archivos.
2. Qué no se hizo y por qué (incluidos los hallazgos de este documento que resultaron
   no ser ciertos).
3. Salida real de la última ejecución de tests y de la compilación. Si algo falla, dilo
   con la salida; no lo presentes como terminado.
4. **Lista de prueba manual para el usuario**, que es quien tiene el teléfono: para cada
   cambio que no se puede verificar compilando (pedal, carreras de transporte, limitador,
   metrónomo, reverberación, gestos de borde, modo inmersivo, aspecto del teclado,
   inercia, pellizco), un paso concreto que hacer y qué debería ver u oír.
5. Las decisiones de la sección 8 con tu recomendación.
