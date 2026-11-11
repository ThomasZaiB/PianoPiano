#pragma once

#include <atomic>
#include <cstdint>

namespace piano {

/** Resolución musical del proyecto: pulsos por negra. */
constexpr int kPPQ = 960;

/**
 * Foto del reloj maestro tomada al inicio de un callback de audio.
 * La UI extrapola desde aquí: tickAhora = tick + (nanoTime - hostTimeNs) * ticksPerSecond / 1e9
 * hostTimeNs usa CLOCK_MONOTONIC, la misma base que System.nanoTime() en Android.
 */
struct ClockSnapshot {
    int64_t framePosition = 0; // frames generados desde que existe el motor
    int64_t hostTimeNs = 0;    // instante (CLOCK_MONOTONIC) del callback
    double tick = 0.0;         // posición musical en ticks PPQ
    double ticksPerSecond = 0.0; // 0 si el transporte está detenido
    double bpm = 120.0;
    int32_t sampleRate = 0;
    bool running = false;
};

/**
 * Publica el reloj del hilo de audio hacia otros hilos con un "seqlock":
 * - Escritor (hilo de audio): nunca espera ni bloquea.
 * - Lector (UI / render 60 FPS): reintenta si pilla una escritura a medias,
 *   lo que es rarísimo y dura nanosegundos.
 * Todos los campos son atómicos, así que no hay data races formales en C++.
 */
class ClockPublisher {
    static_assert(std::atomic<int64_t>::is_always_lock_free, "int64 atómico debe ser lock-free");
    static_assert(std::atomic<double>::is_always_lock_free, "double atómico debe ser lock-free");

public:
    void publish(const ClockSnapshot &s) noexcept {
        const uint32_t seq = mSeq.load(std::memory_order_relaxed);
        mSeq.store(seq + 1, std::memory_order_relaxed); // impar = escribiendo
        std::atomic_thread_fence(std::memory_order_release);
        mFramePosition.store(s.framePosition, std::memory_order_relaxed);
        mHostTimeNs.store(s.hostTimeNs, std::memory_order_relaxed);
        mTick.store(s.tick, std::memory_order_relaxed);
        mTicksPerSecond.store(s.ticksPerSecond, std::memory_order_relaxed);
        mBpm.store(s.bpm, std::memory_order_relaxed);
        mSampleRate.store(s.sampleRate, std::memory_order_relaxed);
        mRunning.store(s.running, std::memory_order_relaxed);
        mSeq.store(seq + 2, std::memory_order_release); // par = consistente
    }

    ClockSnapshot read() const noexcept {
        ClockSnapshot s;
        uint32_t before, after;
        do {
            before = mSeq.load(std::memory_order_acquire);
            s.framePosition = mFramePosition.load(std::memory_order_relaxed);
            s.hostTimeNs = mHostTimeNs.load(std::memory_order_relaxed);
            s.tick = mTick.load(std::memory_order_relaxed);
            s.ticksPerSecond = mTicksPerSecond.load(std::memory_order_relaxed);
            s.bpm = mBpm.load(std::memory_order_relaxed);
            s.sampleRate = mSampleRate.load(std::memory_order_relaxed);
            s.running = mRunning.load(std::memory_order_relaxed);
            std::atomic_thread_fence(std::memory_order_acquire);
            after = mSeq.load(std::memory_order_relaxed);
        } while ((before & 1u) != 0 || before != after);
        return s;
    }

private:
    std::atomic<uint32_t> mSeq{0};
    std::atomic<int64_t> mFramePosition{0};
    std::atomic<int64_t> mHostTimeNs{0};
    std::atomic<double> mTick{0.0};
    std::atomic<double> mTicksPerSecond{0.0};
    std::atomic<double> mBpm{120.0};
    std::atomic<int32_t> mSampleRate{0};
    std::atomic<bool> mRunning{false};
};

} // namespace piano
