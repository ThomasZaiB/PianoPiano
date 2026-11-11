# Informe de mejoras — PianoPiano (2026-11-11)

Encargo: `docs/PROMPT_OPUS_MEJORAS.md`. Se hicieron los cinco paquetes (P1 a P5), cada uno
en su commit y cerrado con `testDebugUnitTest` + `assembleDebug` en verde. No se arrancó el
emulador ni se instaló en ningún dispositivo: **nada de lo visual ni de lo audible está
probado todavía**; para eso está la lista de la sección 4.

| Commit | Contenido |
|---|---|
| `347d74a` | Base: el proyecto tal como estaba (`git init` + `.gitignore` de Android) |
| `a857fef` | P1 — bugs de cara al usuario |
| `c6ce0cd` | P2 — robustez del audio |
| `3809195` | P3 — sonido |
| `bddf5ef` | P4 — teclado y cascada |
| `2171fc3` | P5 — pantallas Compose |
| (último) | README e informe |

Cada paquete se puede revertir por separado con `git revert <commit>`.

---

## 1. Qué se hizo

Todos los hallazgos del documento se confirmaron en el código antes de tocarlos; ninguno
resultó falso (ver matices en la sección 2).

### P1 — Bugs de cara al usuario

| Punto | Qué se hizo | Archivos |
|---|---|---|
| 1.1 Pedal | `detenerTodo()` envía `motor.pedal(false)`. En C++, `closeLocked()` aplica la cola pendiente, vacía el secuenciador, apaga las notas y pone el sustain en reposo (`SoundFontPlayer::resetSustain`). | `PianoViewModel.kt`, `AudioEngine.cpp`, `SoundFontPlayer.h/.cpp` |
| 1.2 Carreras | Un único dueño de la operación de transporte: `nuevaOperacion()` cancela la anterior **con todo lo que lanzó** (reproducción, metrónomo, acompañamiento, espera al final) y abre otra. La UI cambia al instante (`GRABANDO`, `reproduciendo`, `EN_CURSO`), así que un segundo toque ya es "Pausa". Los Seek pendientes (`destinoSalto`) mandan sobre el reloj hasta que el motor los confirma. Los toques durante los ms de arranque no se graban ni se evalúan con un tick viejo. | `PianoViewModel.kt` |
| 1.3 Pantalla | `keepScreenOn` mientras la pantalla del piano está en composición. | `common/ModoInstrumento.kt`, `PianoScreen.kt` |
| 1.4 Gestos | Modo inmersivo en el piano (barras ocultas, reaparecen deslizando) y de vuelta al salir; exclusión de gestos sobre la parte baja del teclado (hasta 200 dp); el piano solo respeta el recorte de pantalla (`displayCutoutPadding`) y el resto de pantallas las barras (`safeDrawingPadding`), ahora por destino y no en `MainActivity`. | `MainActivity.kt`, `PianoNavGraph.kt`, `ModoInstrumento.kt`, `PianoSurfaceView.kt` |
| 1.5 Inicio | Mientras `cargando`, la tarjeta grande es un hueco neutro; la entrada escalonada empieza cuando llegan los datos. | `HomeScreen.kt` |
| 1.6 Doble toque | Una sola puerta, `navegar()` / `volver()`, que solo actúa si la entrada actual está `RESUMED` (también evita sacar dos pantallas de la pila). | `PianoNavGraph.kt` |
| 1.7 Carrusel | Al soltar sin superar el umbral de arrastre también se llama a `asentar(0f)`. | `CarruselRecientes.kt` |
| 1.8 Hilo principal | `LectorMidi.leer` en `Dispatchers.Default`. Los eventos ordenados se calculan una vez por canción (`preparar()`, caché de 2 canciones por identidad), también en `Default`, y se precalculan al cargar la canción. Semántica intacta (ventana 0,25 s, Off antes que On). | `ImportarMidiUseCase.kt`, `ReproducirCancionUseCase.kt`, `PianoViewModel.kt` |

Tests nuevos (`TransporteViewModelTest`, con un `AudioEngineRepository` falso que anota los
comandos): pausa antes del arranque, pausa durante la espera del Seek, dos saltos seguidos
(sin que nada de la posición abandonada se programe tras el último Seek, y un solo
arranque), pausa durante un salto, doble toque en Play de práctica, un toque arranca una
sola vez, y salir con el pedal pisado. Además, `EventosReproduccionTest` (Off antes que On).

### P2 — Robustez del audio

| Punto | Qué se hizo | Archivos |
|---|---|---|
| 2.1 Reapertura | `onErrorAfterClose` compara el stream que falló con `mStream` y, si es uno antiguo, no toca nada. Nuevo `isStreamOpen()` → `AudioEngineRepository.streamAbierto()`. El sondeo de diagnóstico del ViewModel (cada segundo) llama a `GestorMotorAudio.vigilarStream()`, que reintenta abrir y, si no puede, pasa a `ERROR` ("Se perdió la salida de audio; reintentando…") en vez de seguir en `SONANDO` con silencio. | `AudioEngine.h/.cpp`, `native-lib.cpp`, `AudioBridge.kt`, `AudioEngineRepository.kt`, `GestorMotorAudio.kt`, `PianoViewModel.kt` |
| 2.2 Foco | Interfaz de dominio `FocoAudioRepository`; implementación `FocoAudioAndroid` (`AudioFocusRequest`, uso música). Se pide al abrir el stream, se reafirma al arrancar el transporte (quien pulsa Play quiere volver a sonar) y se suelta al cerrar. Perderlo (definitivo o temporal) pausa todo como al ir a segundo plano; no se reanuda solo. "Duck" lo hace el sistema. | `domain/repository/FocoAudioRepository.kt`, `data/audio/FocoAudioAndroid.kt`, `di/AudioModule.kt`, `GestorMotorAudio.kt`, `PianoViewModel.kt` |
| 2.3 Tempo | `PianoUiState.bpm` pasa a `Double` y es la única fuente: la UI, la barra de progreso y las dos llamadas a `setTempo` leen ese valor. Un cambio manual deja el tempo en entero (92,3 + 5 → 97). | `PianoViewModel.kt`, `PianoScreen.kt` |
| 2.4 CMake | `--gc-sections --strip-all` pasan a Release; Debug conserva `-g`. | `CMakeLists.txt` |
| 2.4 Semillas | La inserción de cada canción incluida va en `try/catch`: se registra y se sigue; no se marca como insertada, así que se reintenta al abrir. | `PianoDatabase.kt` |
| 2.4 Dedo que vuelve | Nuevo modo de puntero `FUERA_DEL_TECLADO`: si el dedo vuelve a bajar al teclado, ataca la tecla. | `PianoSurfaceView.kt` |
| 2.4 Vsync | El adelanto del display ya no es 16,7 ms fijo: se parte de `display.refreshRate` y se mide con los propios frames (media móvil, descartando pausas). | `PianoSurfaceView.kt` |
| 2.4 16 KB | Documentación oficial consultada (developer.android.com/guide/practices/page-sizes): con NDK r27 hacen falta `-Wl,-z,max-page-size=16384` y `-Wl,-z,common-page-size=16384`; AGP 8.7.3 ya alinea el zip. Medido en el APK: `libpianoengine.so` quedaba alineado y el `liboboe.so` precompilado de **Oboe 1.9.0 estaba a 4 KB** (0x1000). Las notas de versión de Oboe dicen "Updated to 16kb page sizes" en **1.9.3**, así que se subió a 1.9.3 (misma API). Esto exigió **una ejecución de Gradle con red** para bajar el AAR de Google Maven; el resto de compilaciones fueron `--offline`. | `CMakeLists.txt`, `gradle/libs.versions.toml` |

Verificación de 16 KB sobre el APK final (`llvm-readelf -Wl` del NDK 27 y `zipalign -c -P 16`):

```
arm64-v8a/libandroidx.graphics.path.so 0x4000
arm64-v8a/libc++_shared.so 0x4000
arm64-v8a/liboboe.so 0x4000
arm64-v8a/libpianoengine.so 0x4000
x86_64/libandroidx.graphics.path.so 0x4000
x86_64/libc++_shared.so 0x4000
x86_64/liboboe.so 0x4000
x86_64/libpianoengine.so 0x4000
zipalign: Verification successful
```

También se revisó el criterio de RELRO de la guía: las ocho bibliotecas de 64 bits cumplen.
`armeabi-v7a/libc++_shared.so` sigue a 4 KB, pero el requisito solo aplica a 64 bits.

Tests nuevos: perder el foco pausa la reproducción; un stream caído sin reapertura pasa a
`ERROR` y vuelve a `SONANDO` al recuperarse.

### P3 — Sonido

| Punto | Qué se hizo | Archivos |
|---|---|---|
| 3.1 Limitador | `Limitador` (seguidor de envolvente de pico, ataque 1 ms, relajación 100 ms, umbral ≈ -1 dBFS, misma ganancia en los dos canales). La matemática está en `gananciaLimitador()` y `coeficienteTiempo()`, funciones puras. El `clamp` queda como red de seguridad final. Sin memoria dinámica. | `cpp/Efectos.h`, `AudioEngine.cpp`, `SoundFontPlayer.cpp` |
| 3.2 Metrónomo | `Metronomo` en C++: seno con caída exponencial (τ = 7 ms, inaudible a ~30 ms); primer tiempo Do7 a 0,45, resto Sol6 a 0,28. El hilo de audio calcula en cada bloque si cae un tiempo y lo dispara en su muestra exacta. Evento nuevo `Metronome = 8` (numerador y denominador). `MetronomoUseCase` conserva su forma (suena mientras la corrutina vive); **el ViewModel no cambió**. Un turno evita que el `finally` de una invocación cancelada apague la siguiente. | `Efectos.h`, `EngineEvent.h`, `AudioEngine.cpp`, `AudioBridge.kt`, `AudioEngineRepository.kt`, `MetronomoUseCase.kt` |
| 3.3 Reverberación | `Reverberacion` tipo Freeverb (8 peines con amortiguación + 4 pasa-todo por canal, separación estéreo 23 muestras), búferes reservados en `configurar()` para la frecuencia real del stream. Presets Apagada / Sala (13 %, por defecto) / Auditorio (15 %). Evento `Reverb = 9`. Ajuste en Ajustes › Sonido, guardado en `AjustesRepositoryImpl`, enviado al abrir el motor y al cambiarlo. | `Efectos.h`, `AudioEngine.*`, `Ajustes.kt`, `AjustesRepositoryImpl.kt`, `AjustesScreen.kt`, `AjustesViewModel.kt`, `PianoViewModel.kt` |
| 3.4 Toque | `SensibilidadToque` (Suave 1,6 / Normal / Fuerte 0,6, exponente sobre la altura relativa); Normal es exactamente la fórmula anterior (test). Visible en Ajustes cuando "Dinámica al tocar" está activa. | `Ajustes.kt`, `PianoSurfaceView.kt`, `AjustesScreen.kt` |
| 3.5 Vibración | "Vibración al tocar" (apagada por defecto, `KEYBOARD_TAP`) y "Vibrar al fallar" en práctica (encendida, `REJECT` en Android 11+, `LONG_PRESS` antes). `performHapticFeedback`, sin permiso `VIBRATE`. | `Ajustes.kt`, `PianoSurfaceView.kt`, `PianoScreen.kt`, `AjustesScreen.kt` |

Cadena de salida: sintetizador → reverberación → clic del metrónomo (seco) → limitador →
recorte. El SoundFont no se tocó (3.6).

### P4 — Teclado y cascada (`PianoSurfaceView`)

| Punto | Qué se hizo |
|---|---|
| 1 Volumen | Blancas con degradado vertical y labio inferior gris; negras con degradado, bisel frontal más claro y sombra proyectada sobre las blancas. Pulsada: se hunde 2,5 dp (cara más corta, labio más oscuro) y se tiñe. |
| 2 Suelta | Ataque inmediato; la suelta se desvanece en 100 ms. Usuario: `AtomicLongArray` + mano en `AtomicIntegerArray` (una pulsación muy corta entre dos frames también se ve); canción: detectada frame a frame en el hilo de render. |
| 3 Impacto | Al llegar una nota a la línea, resplandor del color de su mano encima de su tecla (0,25 s, igual a cualquier tempo). La línea lleva un halo degradado. |
| 4 Notas | Degradado vertical, borde claro de 1 dp y hueco de 2 dp al final de cada nota (dos notas seguidas de la misma tecla se ven separadas). |
| 5 Inercia y zoom | Al soltar un arrastre de la cascada el teclado sigue con la velocidad del dedo y frena (τ = 0,33 s); atraparlo lo para. Pellizco con dos dedos sobre la cascada (no sobre el teclado), con el punto entre los dedos fijo; al terminar se guarda en ajustes como el zoom del menú. |
| 6 Reposo | Con el transporte parado y sin toques ni animaciones deja de pedir frames. Se despierta con toques, ajustes, canción, destellos, tamaño y latencia, **y además** un sondeo del reloj cada 50 ms en el hilo de render redibuja si el transporte arrancó o cambió la posición o el tempo. Así ninguna imagen puede quedar congelada más de 50 ms. |
| 7 Accesibilidad | `contentDescription` del teclado desde `strings.xml`. |

`movimientoReducido` llega como propiedad y desactiva inercia, desvanecidos y resplandores.
Los `Shader` se crean solo en `onSizeChanged`; en `dibujar()` solo se asignan referencias a
`Paint` ya existentes.

### P5 — Pantallas Compose

| Punto | Qué se hizo | Archivos |
|---|---|---|
| 1 Barra | En práctica, "Toca …", "Siguiente …" y el error van en un rótulo `titleMedium` centrado sobre la cascada, justo encima de la línea (misma fórmula de alto de teclado, ahora en `TecladoGeometria.altoTecladoDp`). En la barra queda el marcador, acotado a 168 dp. Botones de icono M3 (objetivo táctil mínimo de 48 dp). | `PianoScreen.kt`, `TecladoGeometria.kt` |
| 2 Cuenta de entrada | Grabación de canción nueva: 4-3-2-1 grande sobre la cascada durante el primer compás, leído por frame del reloj del motor (el mismo del metrónomo). `PreparadorGrabacion` añade el compás vacío si la primera nota cae en el compás 0; test actualizado. | `PianoViewModel.kt`, `PianoScreen.kt`, `PreparadorGrabacion.kt`, `LogicaDominioTest.kt` |
| 3 REC | Punto rojo que late (0,7 s); fijo con movimiento reducido. | `PianoScreen.kt` |
| 4 Progreso | En marcha, una lectura por frame (`withFrameNanos`); parado, solo 12 frames tras cada pausa o Seek (contador `saltos` en el estado) y nada más. | `PianoScreen.kt`, `PianoViewModel.kt` |
| 5 Resultado | El porcentaje cuenta desde 0; con tres estrellas, confeti en un `Canvas` (40 partículas, sin dependencias). Estrellas, racha y destello de error respetan `movimientoReducido`. | `ResultadoPractica.kt`, `PianoScreen.kt` |
| 6 Biblioteca | `animateItem()` en las filas. Borrar (deslizar o menú) oculta la canción y muestra "Deshacer"; el borrado real llega al cerrarse el aviso (también si se corta o se sale de la pantalla; si se sale del todo, `onCleared` lo completa). Se quitó el diálogo de confirmación: el deshacer es fiable porque nada se borra hasta entonces. Con más de 10 canciones, búsqueda por título (sin distinguir mayúsculas ni tildes). | `LibraryViewModel.kt`, `LibraryScreen.kt` |
| 7 Textos | Mensajes tipados: `MensajePiano`, `ErrorPractica`, `ErrorMotor`, `MensajeBiblioteca`, `ResultadoImportacionMidi`; `FiltroBiblioteca` sin texto. Las pantallas resuelven con `strings.xml`. Título por defecto y fecha de la biblioteca con el formato del idioma del sistema. | `PianoViewModel.kt`, `LibraryViewModel.kt`, `HomeViewModel.kt`, `ImportacionMidi.kt`, `GestorMotorAudio.kt`, `common/TextosImportacion.kt`, pantallas, `strings.xml` |

También se actualizó el `README.md` (la restricción sobre `cpp/`, `PianoSurfaceView` y
`AudioBridge` ya no rige; cadena de audio, eventos, cómo compilar sin Android Studio).

---

## 2. Qué no se hizo, o se hizo distinto, y por qué

- **Ningún hallazgo resultó falso.** Matices: en 2.4 (semillas), el código ya capturaba los
  errores al *construir* la canción, pero no al *insertarla*; eso es lo que se corrigió.
- **1.4:** la exclusión de gestos cubre solo los 200 dp inferiores del teclado (límite de
  Android). En teléfonos el teclado mide menos, así que queda entero; en tabletas, la franja
  superior de las negras sigue pudiendo disparar "atrás".
- **2.4 Oboe:** subir de versión obligó a una descarga de Gradle con red (el AAR de Oboe
  1.9.3). No se descargó nada más.
- **3.3:** la reverberación se aplica al piano; el metrónomo va seco a propósito (un clic
  con eco se pierde).
- **3.5:** `performHapticFeedback` respeta el ajuste del sistema "Respuesta táctil": si el
  usuario lo tiene apagado en Android, la app no vibra.
- **P4.1:** "brillo superior" en las negras se interpretó como degradado más claro arriba
  y un bisel claro en el borde frontal.
- **P4.4:** el degradado de las notas es vertical en coordenadas de pantalla (más apagadas
  arriba, plenas al llegar a la línea), no uno por nota: un degradado por nota obligaría a
  crear o reconfigurar un `Shader` por nota y frame, lo que viola la regla de cero
  asignaciones.
- **P4.6:** el reposo depende del sondeo de 50 ms como red de seguridad. Si el usuario
  pulsa Play estando en reposo, la cascada puede tardar hasta 50 ms en empezar a moverse
  (la posición siempre es la correcta). Es el punto más delicado: ver la prueba 4.13.
- **P5.1:** que la barra quepa en 640 dp de ancho en horizontal está razonado, no visto.
- **P5.2:** la cuenta de entrada solo existe en Crear canción (modo GRABAR nuevo). "Grabar
  rápido" en Tocar libremente sigue empezando al instante, como dice su propia descripción;
  si la primera nota cae en el compás 0, ahora sí se añade el compás vacío al guardar.
- **P5.7, textos que quedan en el código** (no estaban en la lista del encargo):
  `ImportarViewModel` ("No se pudo guardar: …"), `GuardarGrabacionUseCase` ("Sin título",
  inalcanzable porque el diálogo no deja guardar un título vacío), `ImportarMidiUseCase`
  ("Canción importada"), los mensajes de `ErrorMidi` en `LectorMidi` (se muestran tal cual
  al fallar una importación) y los textos de excepción de `AudioBridge.cargarSoundFont`.
  Con `ErrorMotor` el detalle técnico de por qué falló el SoundFont ya no se muestra en la
  barra (antes salía el texto de la excepción).
- **Fuera del encargo, apuntado y no tocado:** la carpeta `apk/` (APK viejo) queda fuera
  de git por el `.gitignore` que pedía el encargo.

---

## 3. Salida real de la última ejecución

Compilación limpia (`:app:clean :app:testDebugUnitTest :app:assembleDebug`, `--offline`):

```
> Task :app:buildCMakeDebug[arm64-v8a]
> Task :app:buildCMakeDebug[armeabi-v7a]
> Task :app:buildCMakeDebug[x86_64]
> Task :app:assembleDebug
BUILD SUCCESSFUL in 30s
58 actionable tasks: 35 executed, 23 from cache
```

En esa pasada Gradle sacó `testDebugUnitTest` de su caché (mismas entradas que la ejecución
anterior), así que se volvió a ejecutar forzado (`:app:testDebugUnitTest --rerun`):

```
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 5s
```

Resultado por suite (de `app/build/test-results/testDebugUnitTest`): **49 tests, 0 fallos,
0 errores** (36 de partida + 13 nuevos).

```
domain.CuantizadorTest: 7          domain.LectorMidiTest: 3
domain.EtiquetasTecladoTest: 1     domain.MidiFileWriterTest: 1
domain.EvaluadorPracticaTest: 6    domain.NotacionTecladoTest: 4
domain.EventosReproduccionTest: 1  domain.ResultadoPracticaTest: 1
domain.FisicaCarruselTest: 3       domain.SeleccionPracticaTest: 3
domain.GrabadorYPreparadorTest: 4  domain.SensibilidadToqueTest: 2
domain.InicioTest: 3               presentation.FocoYStreamTest: 2
                                   presentation.TransporteViewModelTest: 8
```

El compilador C++ (`-Wall -Wextra`) no dio ningún aviso en el código propio.

---

## 4. Lista de prueba manual (en el teléfono)

Instala `app/build/outputs/apk/debug/app-debug.apk`. Cada prueba dice qué hacer y qué
deberías ver u oír; si algo no coincide, anota el número.

**Pedal y transporte**

1. Abre Tocar libremente, activa "Pedal", sal con ← y abre una canción en Reproducir. Toca
   unas teclas: deben cortarse al soltar (sin sostenido) y el chip "Pedal" apagado.
2. Igual, pero con el pedal puesto manda la app a segundo plano y vuelve: el pedal apagado
   en el chip y en el sonido.
3. Crear canción → Grabar y pulsa Pausa enseguida (en menos de medio segundo). Debe quedar
   "En pausa", sin metrónomo sonando ni la cascada moviéndose.
4. Reproducir: con la canción sonando arrastra la barra de progreso dos veces seguidas muy
   rápido. Solo debe sonar desde el último punto, sin notas sueltas de golpe.
5. Reproducir: arrastra la barra y pulsa Pausa en el acto. Debe quedar parada.
6. Práctica: toca dos veces muy rápido el botón ▶. Debe quedar en pausa, sin arrancar.

**Pantalla y gestos**

7. Reproduce una canción larga sin tocar la pantalla: no debe atenuarse ni apagarse.
8. En el piano, las barras del sistema no se ven; desliza desde el borde superior o inferior:
   aparecen un momento y se vuelven a ocultar. Al salir del piano vuelven a quedarse.
9. Haz un glissando empezando en el borde izquierdo o derecho del teclado: no debe dispararse
   "atrás". (El gesto atrás sí funciona desde la cascada).
10. En un teléfono con notch o cámara en pantalla, en horizontal: ninguna tecla ni botón debajo.
11. Inicio en frío (cierra la app del todo): no debe verse ni un instante la tarjeta "graba
    tu primera canción" si ya tienes canciones.
12. Toca dos veces muy rápido una tarjeta del inicio o un acceso: se abre una sola pantalla
    (y al volver, se vuelve al inicio, no a otra copia).
13. Carrusel de recientes: lánzalo y agárralo con el dedo a mitad sin arrastrar; al soltar
    debe colocarse en una tarjeta (también si lo agarras en el rebote del final).

**Teclado y cascada**

14. Deja el piano quieto sin transporte unos segundos y pulsa Play: la cascada debe empezar
    a moverse sin parón visible y en sincronía con lo que suena. Repite en práctica (al
    acertar tras una espera) y tras arrastrar la barra en pausa: la cascada debe saltar a la
    posición nueva.
15. Mira el teclado: blancas con un ligero degradado y un borde inferior gris; negras con
    sombra sobre las blancas. Al pulsar, la tecla se acorta un poco y se tiñe.
16. Pulsa y suelta una tecla: el color se apaga en una décima de segundo, no de golpe. En
    Reproducir, al terminar cada nota de la canción, lo mismo.
17. En Reproducir, cada nota que llega a la línea deja un resplandor breve de su color
    encima de su tecla. La línea tiene un halo, no es una raya plana.
18. Las notas de la cascada tienen un degradado (más apagadas arriba) y un borde claro fino;
    dos notas seguidas de la misma tecla se ven como dos barras separadas.
19. Arrastra la cascada y suelta con impulso: el teclado sigue y frena solo. Tócala mientras
    se mueve: se para.
20. Pellizca con dos dedos sobre la cascada: el zoom cambia de forma continua alrededor del
    punto entre los dedos. Sal y vuelve: se conserva. Dos dedos sobre el teclado siguen
    siendo dos notas.
21. Saca un dedo de una tecla hacia la cascada y vuelve a bajarlo sin levantarlo: debe volver
    a sonar la tecla sobre la que cae.
22. Con un móvil de 90/120 Hz, en Reproducir: las notas deben llegar a la línea justo cuando
    suenan (antes podían ir ~5–8 ms adelantadas).
23. Activa en Android "Quitar animaciones": sin inercia, sin desvanecidos ni resplandores, y
    el REC, las estrellas y la racha sin animar.

**Sonido**

24. Con el pedal puesto toca acordes densos y fuertes en todo el teclado: no debe oírse
    distorsión áspera (antes sí). El volumen baja un poco en los picos, sin chasquidos.
25. Crear canción con metrónomo: el clic suena a metrónomo (no a piano agudo), más agudo y
    fuerte en el primer tiempo, y no choca si tocas Do8 o Sol7. Prueba 3/4 y 6/8.
26. Ajustes › Sonido › Reverberación: Apagada suena seco, Sala con un poco de cola,
    Auditorio con más. Cambiarlo con el piano abierto (vuelve a la pantalla) debe notarse ya.
27. Ajustes › Respuesta al toque: con Suave cuesta más sonar fuerte; con Fuerte, menos;
    Normal igual que antes.
28. Ajustes › Vibración al tocar: con ella encendida, cada tecla vibra. En práctica, una nota
    equivocada vibra (si "Vibrar al fallar" está activo y la respuesta táctil del sistema
    encendida).
29. Reproduciendo, recibe una llamada (o pon música en otra app): el piano se pausa. Al
    volver, se reanuda solo cuando pulsas Play.
30. Reproduciendo con auriculares, desconéctalos (o pasa a Bluetooth): debe seguir sonando
    por el nuevo dispositivo; si no, en la barra aparece "Se perdió la salida de audio;
    reintentando…" y en un par de segundos vuelve.

**Pantallas**

31. Práctica: lo que hay que tocar ("Toca Do4 · Mi4") y el error aparecen grandes, centrados
    justo encima de la línea, y el error destella en rojo. La barra no se desborda en un
    teléfono pequeño en horizontal.
32. Crear canción → Grabar: 4-3-2-1 sobre la cascada al ritmo del metrónomo; la primera nota
    que toques al acabar la cuenta debe quedar al principio del segundo compás al guardar.
33. Grabando, el punto rojo de REC late despacio.
34. Reproducir: la barra de progreso avanza fluida, sin saltos.
35. Termina una práctica con ≥ 95 %: el porcentaje sube contando y salta confeti.
36. Biblioteca: desliza una canción para borrarla, pulsa "Deshacer": vuelve a su sitio con
    animación. Bórrala y no deshagas: desaparece. Con más de 10 canciones aparece el buscador.
37. Con el teléfono en inglés, la fecha de la biblioteca y el título por defecto al guardar
    usan el formato inglés.

---

## 5. Decisiones que son tuyas (sección 8 del encargo)

**SoundFont de calidad.** Es la mejora de sonido más grande que queda. Recomendación: un
piano de cola único (no un banco General MIDI completo), con 4–8 capas de velocidad y una
muestra cada 2–3 semitonos, que deja el `.sf2` en unos **15–40 MB** (el APK hoy pesa unos
22 MB en debug). Candidatos a mirar: *Salamander Grand Piano* (Yamaha C5, distribuido con
licencia Creative Commons de atribución) recortado a menos capas, o el piano de *FluidR3_GM*
(MIT) extraído del banco. De cualquier licencia hay que comprobar: que permite redistribuir
dentro de una app (y comercialmente, si la vas a vender), qué atribución exige y dónde
ponerla (Ajustes › Acerca de y README), y que no tenga cláusula "sin obras derivadas", porque
recortar capas es una obra derivada. TinySoundFont solo lee `.sf2` sin comprimir, así que el
tamaño del archivo es el tamaño en el APK.

**Canción con derechos de autor.** *Merry-Go-Round of Life* no puede ir en una versión
publicada. Recomendación: moverla a `app/src/debug/assets/canciones/`, así sigue en tus
compilaciones de prueba y no entra en la de publicación, sin tocar código.

**Etiqueta "Exportadas".** Propuesta: `origen` (GRABADA, IMPORTADA_MIDI, IMPORTADA_TEXTO,
INCLUIDA) y `exportadaEn: Long?` como marca aparte. Filtros: Todas / Grabadas / Importadas,
con un icono de "exportada" en la tarjeta. Exige migración de Room 1 → 2 (dos columnas; las
`EXPORTADA` de hoy pasan a `GRABADA` con fecha de exportación). Las canciones ya importadas
de MIDI no se pueden distinguir hoy de las grabadas (la etiqueta "MIDI" de la biblioteca
marca las *exportadas*), así que tras la migración contarían como grabadas; solo las nuevas
importaciones quedarían bien clasificadas.
Coste estimado: medio día con tests de la migración.

**Importación MIDI (tempo y pedal).** Hoy se usa el primer tempo, limitado en silencio a
40–240 BPM, y se ignora el CC64. Opciones:
- *Pedal, barato (2–4 h):* al importar, alargar cada nota hasta que se levante el pedal.
  Suena bien y no cambia el modelo ni la base de datos.
- *Pedal, completo (1–2 días):* guardar los eventos de pedal en la canción y programarlos
  en el motor (hay que añadir un tipo de evento programado y una migración).
- *Cambios de tempo, barato (medio día):* convertir el archivo a un tempo único conservando
  el tiempo real de cada nota; suena a la velocidad correcta, pero la rejilla de compases
  deja de coincidir en las partes con otro tempo.
- *Cambios de tempo, completo (2–3 días):* mapa de tempos en el modelo y `SetTempo`
  programados en el motor; afecta a la cascada, la práctica y la exportación.
- En cualquier caso, avisar al importar si el tempo se recortó a 40–240.

Recomendación: las dos opciones baratas primero; cubren la mayoría de MIDI de piano.
