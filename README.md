# PianoPiano

App Android de piano: teclado de 88 teclas, cascada de notas a 60 FPS y motor de audio
de baja latencia en C++ (Oboe + TinySoundFont). Ahora con menú, biblioteca (Room),
grabación con cuantización, modo práctica y exportación a MIDI.

## Cómo ejecutarlo

1. **Android Studio** Ladybug (2024.2) o más reciente → *File › Open* → esta carpeta.
2. *Tools › SDK Manager › SDK Tools*: **NDK 27.2.12479018** y **CMake 3.22.1**.
3. *Run* en un teléfono (recomendado) o en el emulador (API 35, x86_64).

La primera vez, la biblioteca ya trae el *Himno de la Alegría* para probar todo.

## Pantallas y navegación

| Ruta | Pantalla | Orientación |
|---|---|---|
| `home` | Menú: Crear canción · Biblioteca · Tocar libremente · ⚙️ | Vertical |
| `library` | Lista con filtros, deslizar para borrar, acciones por canción | Vertical |
| `song_setup` | BPM, compás, cuantización (perfecta/humana), metrónomo | Vertical |
| `piano/{modo}/{cancionId}` | Teclado + cascada en uno de los 4 modos | Horizontal |

## Los 4 modos del piano

| Modo | Cómo se llega | Qué hace |
|---|---|---|
| `LIBRE` | Inicio › Tocar libremente | Teclado sin partitura. Botón **Grabar rápido** para capturar lo que tocas. |
| `GRABAR` | Crear canción, o ✏️ en la biblioteca | 🔴 Grabar · ⏸ Pausa · ⏹ Detener → nombre → se guarda cuantizada. Desde la biblioteca es **overdub**: suena la canción y las notas nuevas se añaden. |
| `REPRODUCIR` | ▶ en la biblioteca | Reproduce la canción con la cascada. |
| `PRACTICA` | 🎓 en la biblioteca | **Modo espera**: si no tocas la nota a tiempo o tocas otra, la canción se detiene con las notas sobre la línea hasta que aciertes. Cuenta aciertos, errores y precisión. |

Exportar (⬇️ en la biblioteca) abre el selector "Guardar como" de Android y escribe un
`.mid` estándar (tipo 1, PPQ 960, una pista por mano).

## Repertorio: añadir canciones

Hay dos formas, las dos con la misma notación de texto:

```
# titulo: Mi canción        ← cabecera opcional
# bpm: 100
# compas: 3/4
# figura: 1/8               ← duración de cada paso: 1/4, 1/8, 1/16 o Tresillo
[C5E5] [D5F5] B2 E3 [G#3E4] - A4
```

- Cada elemento separado por espacios es **un paso**; `[ ]` = notas a la vez; `-` = silencio.
- C4 = Do central; `#` sostenido, `b` bemol. Las manos y duraciones se deducen solas.

1. **Desde la app**: Biblioteca › **Importar** → pega el texto (o "Abrir .txt"), revisa la
   vista previa (notas, duración o el error exacto) y guarda.
2. **Incluidas en la app**: crea un `.txt` en `app/src/main/assets/canciones/`. Se añade a la
   biblioteca al abrir la app, también en instalaciones existentes, y una sola vez
   (si la borras, no vuelve).

Incluidas ahora: *Himno de la Alegría* (demo) y *Merry-Go-Round of Life*.

## Integración con el motor existente

**No se modificó** `app/src/main/cpp/`, ni `PianoSurfaceView`, ni `AudioBridge`.
Todo lo nuevo usa sus APIs públicas:

- `AudioEngineRepository` (lo implementa `AudioBridge`): notas en vivo y programadas,
  transporte (`transporteIniciar/Detener`, `irATick`), tempo y reloj.
- `PianoSurfaceView`: `listener`, `fuenteReloj`, `setCancion()`, `latenciaAudioMs`, `teclasVisibles`.

Piezas nuevas que hacen falta para ello:

- **`GestorMotorAudio`** (dominio, singleton): único dueño del stream. Carga el SoundFont
  una vez y cuenta cuántas pantallas lo usan, para que dos pantallas del piano que
  conviven durante la animación de navegación no se pisen.
- **`MetronomoUseCase`**: metrónomo provisional hecho con `noteOnEn` (clics agudos que
  dispara C++ con precisión de muestra). Se puede sustituir por uno nativo sin tocar el ViewModel.
- **Grabación**: cada toque toma el tick del reloj del motor, compensando la latencia de salida.
- **Práctica**: `EvaluadorPractica` decide; el ViewModel traduce a `TransportStop` + `Seek`
  (pausa con las notas en la línea) y `TransportStart` (reanudar).

### Archivos nuevos

```
domain/audio/GestorMotorAudio.kt
domain/grabacion/GrabadorNotas.kt, PreparadorGrabacion.kt
domain/musica/Cuantizador.kt, NotacionTeclado.kt
domain/practica/EvaluadorPractica.kt
domain/model/ModoPiano, Cuantizacion, OrigenCancion, EstadoMotor, ResumenCancion,
             ConfigGrabacion, NombresNotas
domain/repository/CancionRepository.kt, ExportadorMidi.kt
domain/usecase/GuardarGrabacionUseCase, ExportarCancionUseCase, MetronomoUseCase, ImportarCancionUseCase
data/local/PianoDatabase.kt (+ SemillasCallback), Mapeadores.kt
data/local/entity/CancionEntity, NotaEntity, Relaciones
data/local/dao/CancionDao.kt
data/repository/CancionRepositoryImpl.kt
data/midi/MidiFileWriter.kt, ExportadorMidiSaf.kt
di/DataModule.kt
presentation/navigation/Rutas.kt, PianoNavGraph.kt
presentation/ui/common/FijarOrientacion.kt
presentation/ui/home/HomeScreen.kt
presentation/ui/setup/SongSetupScreen.kt
presentation/ui/library/LibraryScreen.kt, ImportarCancionScreen.kt
presentation/viewmodel/LibraryViewModel.kt, ImportarViewModel.kt
assets/canciones/merry-go-round.txt
app/src/test/.../LogicaDominioTest.kt
```

### Archivos modificados

| Archivo | Cambio |
|---|---|
| `MainActivity.kt` | Solo monta `PianoNavGraph` |
| `PianoScreen.kt` | Barra por modo, Grabar rápido, diálogos; el ciclo de vida del audio vive aquí |
| `PianoViewModel.kt` | Reescrito: SavedStateHandle + 4 modos |
| `domain/model/Cancion.kt` | Campos nuevos con valores por defecto (cuantización, origen, fecha) |
| `ReproducirCancionUseCase.kt`, `MetronomoUseCase.kt` | Con el transporte parado usan el tick de partida, no un reloj viejo |
| `AndroidManifest.xml` | Sin orientación fija; `configChanges` para girar sin recrear la Activity |
| `libs.versions.toml`, `app/build.gradle.kts` | `material-icons-extended` |

## Licencias de terceros

- **TinySoundFont** — MIT, © Bernhard Schelling.
- **demo-florestan.sf2** — subconjunto Florestan del repositorio de TinySoundFont (MIT). Solo para pruebas.
- **Oboe** — Apache 2.0, © Google.
- **Himno de la Alegría** — L. v. Beethoven (1824), dominio público.
- **Merry-Go-Round of Life** — Joe Hisaishi (2004), obra protegida por derechos de autor.
  Transcripción aportada por el usuario para uso personal; antes de publicar la app
  (p. ej. en Google Play) habría que retirarla o contar con licencia.
