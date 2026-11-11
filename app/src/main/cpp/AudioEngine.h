#pragma once

#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <mutex>

#include "EngineEvent.h"
#include "EventScheduler.h"
#include "MasterClock.h"
#include "RingBuffer.h"
#include "SoundFontPlayer.h"

namespace piano {

/**
 * Motor de audio: dueño del stream Oboe, del sintetizador y del reloj maestro.
 *
 * Reglas de hilos:
 *  - Hilo de audio (onAudioReady): sin locks, sin new/malloc, sin JNI, sin logs.
 *    Solo lee el ring buffer, sintetiza y publica el reloj.
 *  - Hilos de control (Kotlin vía JNI): loadSoundFont/start/stop toman mControlMutex.
 *  - Productores de eventos (cualquier hilo Kotlin): postEvent toma mProducerMutex.
 *    Ningún mutex se toma jamás en el hilo de audio.
 */
class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback,
                    public std::enable_shared_from_this<AudioEngine> {
public:
    AudioEngine() = default;
    ~AudioEngine() override;

    /** Requiere el stream cerrado (llamar antes de start() o tras stop()). */
    bool loadSoundFont(const void *data, int32_t sizeBytes);

    /** Abre y arranca el stream. Idempotente. */
    bool start();

    /** Detiene y cierra el stream (libera el modo exclusivo para otras apps). */
    void stop();

    /** Encola un evento para el hilo de audio. False si el búfer está lleno. */
    bool postEvent(const EngineEvent &event);

    ClockSnapshot readClock() const { return mClock.read(); }

    /** Latencia de salida estimada (ms) o -1 si aún no se conoce. */
    double latencyMillis();

    /** Número de underruns (cortes) desde que se abrió el stream; -1 si no aplica. */
    int32_t xRunCount();

    /** true si hay stream abierto (false tras un error de dispositivo sin reapertura). */
    bool isStreamOpen();

    // ---- Callbacks de Oboe ----
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData,
                                          int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    bool openAndStartLocked();
    void closeLocked();

    // Hilo de audio únicamente:
    void handleEvent(const EngineEvent &e) noexcept;
    void applyNote(const EngineEvent &e, bool scheduled) noexcept;
    void clearScheduled() noexcept;
    void renderBlock(float *out, int32_t numFrames) noexcept;
    double currentTick() const noexcept;
    void reanchor() noexcept;
    void updateTicksPerFrame() noexcept;

    std::mutex mControlMutex;
    std::mutex mProducerMutex;
    std::shared_ptr<oboe::AudioStream> mStream; // protegido por mControlMutex
    bool mWantRunning = false;                  // protegido por mControlMutex

    SoundFontPlayer mPlayer;
    SpscRingBuffer<EngineEvent, 1024> mEvents;
    ClockPublisher mClock;

    // ---- Estado exclusivo del hilo de audio (reloj maestro) ----
    int64_t mFramePosition = 0;
    int32_t mSampleRate = 0;
    double mBpm = 120.0;
    bool mTransportRunning = false;
    double mTickAnchor = 0.0;  // tick en el último cambio de tempo/start/seek
    int64_t mFrameAnchor = 0;  // frame en ese mismo instante
    double mTicksPerFrame = 0.0;

    // ---- Secuenciador (hilo de audio) ----
    EventScheduler<4096> mScheduler;
    // Notas encendidas por el secuenciador: al pausar/saltar se apagan solo esas,
    // sin cortar lo que el usuario esté tocando en vivo en otras teclas.
    bool mScheduledSounding[SoundFontPlayer::kChannelCount][128] = {};
};

} // namespace piano
