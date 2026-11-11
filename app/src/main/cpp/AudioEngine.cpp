#include "AudioEngine.h"

#include <android/log.h>
#include <time.h>

#include <algorithm>

#define LOG_TAG "PianoEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace piano {

namespace {
/** Límite de eventos procesados por callback: acota el peor caso de CPU. */
constexpr int kMaxEventsPerCallback = 256;
constexpr double kMinBpm = 20.0;
constexpr double kMaxBpm = 400.0;

/** clock_gettime va por vDSO: sin syscall, sin bloqueo. Seguro en tiempo real. */
inline int64_t nowMonotonicNs() noexcept {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1'000'000'000LL + ts.tv_nsec;
}
} // namespace

AudioEngine::~AudioEngine() {
    std::lock_guard<std::mutex> lock(mControlMutex);
    closeLocked();
}

// ============================================================================
// Control (hilos de Kotlin)
// ============================================================================

bool AudioEngine::loadSoundFont(const void *data, int32_t sizeBytes) {
    std::lock_guard<std::mutex> lock(mControlMutex);
    if (mStream) {
        LOGE("loadSoundFont: detén el motor antes de cambiar de SoundFont");
        return false;
    }
    const bool ok = mPlayer.load(data, sizeBytes);
    LOGI("SoundFont %s (%d bytes)", ok ? "cargado" : "NO cargado", sizeBytes);
    return ok;
}

bool AudioEngine::start() {
    std::lock_guard<std::mutex> lock(mControlMutex);
    mWantRunning = true;
    if (mStream) return true;
    if (!mPlayer.isLoaded()) {
        LOGE("start: no hay SoundFont cargado");
        return false;
    }
    return openAndStartLocked();
}

void AudioEngine::stop() {
    std::lock_guard<std::mutex> lock(mControlMutex);
    mWantRunning = false;
    closeLocked();
}

bool AudioEngine::openAndStartLocked() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency) // ruta MMAP/FAST si el equipo la tiene
        ->setSharingMode(oboe::SharingMode::Exclusive)          // sin mezclador del sistema = menos latencia
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        // No forzamos sample rate: usamos el nativo del equipo (evita remuestreo y latencia extra).
        ->setFormatConversionAllowed(true)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setDataCallback(shared_from_this())
        ->setErrorCallback(shared_from_this());

    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK) {
        LOGE("openStream falló: %s", oboe::convertToText(result));
        mStream.reset();
        return false;
    }

    // El stream aún no corre: es seguro tocar el sintetizador desde este hilo.
    mPlayer.configure(mStream->getSampleRate());

    // Doble búfer de "bursts": el mínimo estable en la mayoría de equipos.
    // (Paso posterior opcional: oboe::LatencyTuner para ajustarlo según xruns.)
    mStream->setBufferSizeInFrames(mStream->getFramesPerBurst() * 2);

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("requestStart falló: %s", oboe::convertToText(result));
        closeLocked();
        return false;
    }

    LOGI("Stream abierto: %d Hz, burst=%d, buffer=%d, sharing=%s, perf=%s",
         mStream->getSampleRate(), mStream->getFramesPerBurst(), mStream->getBufferSizeInFrames(),
         oboe::convertToText(mStream->getSharingMode()),
         oboe::convertToText(mStream->getPerformanceMode()));
    return true;
}

void AudioEngine::closeLocked() {
    if (!mStream) return;
    mStream->stop();
    mStream->close();
    mStream.reset();
    // El hilo de audio ya terminó: este hilo es ahora el único consumidor de la cola y
    // dueño del estado del secuenciador, así que podemos tocarlos sin carreras.
    // Se aplican los comandos que quedaron sin procesar (el TransportStop y el Seek que
    // envía la pantalla al salir) para que la próxima apertura no los herede a destiempo.
    EngineEvent event{};
    while (mEvents.pop(event)) handleEvent(event);
    clearScheduled();
    mPlayer.allNotesOff();
    // El motor es compartido entre pantallas: ninguna debe heredar el pedal de otra.
    mPlayer.resetSustain();
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) {
    // Se llama en un hilo propio de Oboe, NO en el hilo de audio. Caso típico:
    // el usuario conecta/desconecta auriculares o Bluetooth (ErrorDisconnected).
    LOGW("Stream cerrado por error: %s", oboe::convertToText(error));
    std::lock_guard<std::mutex> lock(mControlMutex);
    // El aviso puede llegar tarde: si entre el error y este callback hubo stop() + start()
    // (la app pasó por segundo plano), mStream ya es otro stream sano y no hay que tocarlo.
    if (mStream.get() != stream) {
        LOGI("Error de un stream antiguo: se ignora");
        return;
    }
    mStream.reset();
    if (mWantRunning && error == oboe::Result::ErrorDisconnected) {
        LOGI("Reabriendo stream en el nuevo dispositivo de salida…");
        // Si falla, mStream queda vacío: isStreamOpen() lo delata y Kotlin reintenta.
        if (!openAndStartLocked()) LOGE("No se pudo reabrir el stream");
    }
}

bool AudioEngine::isStreamOpen() {
    std::lock_guard<std::mutex> lock(mControlMutex);
    return mStream != nullptr;
}

bool AudioEngine::postEvent(const EngineEvent &event) {
    // Serializa a los productores (UI, reproductor…) para respetar el contrato SPSC.
    // Este mutex NUNCA se toma en el hilo de audio.
    std::lock_guard<std::mutex> lock(mProducerMutex);
    return mEvents.push(event);
}

double AudioEngine::latencyMillis() {
    std::lock_guard<std::mutex> lock(mControlMutex);
    if (!mStream) return -1.0;
    auto latency = mStream->calculateLatencyMillis();
    return latency ? latency.value() : -1.0;
}

int32_t AudioEngine::xRunCount() {
    std::lock_guard<std::mutex> lock(mControlMutex);
    if (!mStream) return -1;
    auto xruns = mStream->getXRunCount();
    return xruns ? xruns.value() : -1;
}

// ============================================================================
// Hilo de audio (tiempo real): nada de locks, asignaciones, JNI ni logs.
// ============================================================================

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *stream, void *audioData,
                                                   int32_t numFrames) {
    const int64_t hostTimeNs = nowMonotonicNs();

    // Tras reabrir en otro dispositivo la frecuencia puede cambiar: re-anclamos el reloj
    // para que la posición musical siga siendo continua.
    const int32_t sampleRate = stream->getSampleRate();
    if (sampleRate != mSampleRate) {
        reanchor();
        mSampleRate = sampleRate;
        updateTicksPerFrame();
    }

    // 1) Eventos pendientes (se aplican al inicio del bloque).
    EngineEvent event{};
    for (int i = 0; i < kMaxEventsPerCallback && mEvents.pop(event); ++i) {
        handleEvent(event);
    }

    // 2) Publicar el reloj maestro (posición al inicio de este bloque).
    ClockSnapshot snapshot;
    snapshot.framePosition = mFramePosition;
    snapshot.hostTimeNs = hostTimeNs;
    snapshot.tick = currentTick();
    snapshot.ticksPerSecond = mTransportRunning ? mBpm * kPPQ / 60.0 : 0.0;
    snapshot.bpm = mBpm;
    snapshot.sampleRate = mSampleRate;
    snapshot.running = mTransportRunning;
    mClock.publish(snapshot);

    // 3) Sintetizar, disparando las notas programadas en su frame exacto.
    renderBlock(static_cast<float *>(audioData), numFrames);

    mFramePosition += numFrames;
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::renderBlock(float *out, int32_t numFrames) noexcept {
    int32_t done = 0;
    if (mTransportRunning && mTicksPerFrame > 0.0) {
        const double blockStartTick = currentTick();
        while (!mScheduler.empty()) {
            const double offsetFrames = (mScheduler.top().value - blockStartTick) / mTicksPerFrame;
            if (offsetFrames >= numFrames) break; // pertenece a un bloque futuro
            int32_t offset = offsetFrames <= 0.0 ? 0 : static_cast<int32_t>(offsetFrames);
            if (offset < done) offset = done;
            if (offset > done) {
                // Sintetiza hasta justo antes del evento: precisión de una muestra
                // (~0.02 ms a 48 kHz) en vez de la de un bloque completo.
                mPlayer.render(out + done * 2, offset - done);
                done = offset;
            }
            applyNote(mScheduler.pop(), true);
        }
    }
    if (done < numFrames) mPlayer.render(out + done * 2, numFrames - done);
}

void AudioEngine::applyNote(const EngineEvent &e, bool scheduled) noexcept {
    if (e.type == EventType::NoteOn) {
        mPlayer.noteOn(e.channel, e.key, e.velocity);
        if (scheduled) mScheduledSounding[e.channel][e.key] = true;
    } else {
        mPlayer.noteOff(e.channel, e.key);
        if (scheduled) mScheduledSounding[e.channel][e.key] = false;
    }
}

void AudioEngine::clearScheduled() noexcept {
    mScheduler.clear();
    for (int ch = 0; ch < SoundFontPlayer::kChannelCount; ++ch) {
        for (int key = 0; key < 128; ++key) {
            if (mScheduledSounding[ch][key]) {
                mPlayer.noteOff(ch, key);
                mScheduledSounding[ch][key] = false;
            }
        }
    }
}

void AudioEngine::handleEvent(const EngineEvent &e) noexcept {
    switch (e.type) {
        case EventType::NoteOn:
        case EventType::NoteOff:
            if (e.value < 0.0) {
                applyNote(e, false); // en vivo: suena ya
            } else {
                mScheduler.push(e);  // programada: suena en su tick (si la cola está llena, se descarta)
            }
            break;
        case EventType::AllNotesOff:
            mPlayer.allNotesOff();
            break;
        case EventType::Sustain:
            mPlayer.setSustain(e.channel, e.value >= 0.5);
            break;
        case EventType::SetTempo:
            reanchor(); // el tick actual se conserva; solo cambia la velocidad a partir de aquí
            mBpm = std::clamp(e.value, kMinBpm, kMaxBpm);
            updateTicksPerFrame();
            break;
        case EventType::TransportStart:
            if (!mTransportRunning) {
                mFrameAnchor = mFramePosition;
                mTransportRunning = true;
            }
            break;
        case EventType::TransportStop:
            if (mTransportRunning) {
                mTickAnchor = currentTick();
                mTransportRunning = false;
            }
            clearScheduled();
            break;
        case EventType::Seek:
            mTickAnchor = std::max(0.0, e.value);
            mFrameAnchor = mFramePosition;
            clearScheduled();
            break;
    }
}

double AudioEngine::currentTick() const noexcept {
    if (!mTransportRunning) return mTickAnchor;
    // Se calcula desde el ancla (no acumulando por bloque) para no arrastrar error de redondeo.
    return mTickAnchor + static_cast<double>(mFramePosition - mFrameAnchor) * mTicksPerFrame;
}

void AudioEngine::reanchor() noexcept {
    mTickAnchor = currentTick();
    mFrameAnchor = mFramePosition;
}

void AudioEngine::updateTicksPerFrame() noexcept {
    mTicksPerFrame = mSampleRate > 0 ? (mBpm * kPPQ) / (60.0 * mSampleRate) : 0.0;
}

} // namespace piano
