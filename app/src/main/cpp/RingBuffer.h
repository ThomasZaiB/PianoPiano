#pragma once

#include <array>
#include <atomic>
#include <cstddef>
#include <type_traits>

namespace piano {

/**
 * Búfer circular lock-free de un productor y un consumidor (SPSC).
 *
 * - Consumidor: el hilo de audio de Oboe. pop() nunca bloquea, nunca asigna memoria
 *   y tiene coste O(1): es seguro dentro del callback de tiempo real.
 * - Productor: un único hilo a la vez. AudioEngine serializa a los productores
 *   (UI, reproductor de canciones, etc.) con un mutex que solo se toma FUERA del
 *   hilo de audio, así el consumidor sigue siendo 100 % lock-free.
 *
 * Los índices crecen sin fin y se enmascaran con (Capacity - 1); como Capacity es
 * potencia de dos, el desbordamiento de size_t sigue dando restas correctas.
 */
template <typename T, size_t Capacity>
class SpscRingBuffer {
    static_assert(Capacity >= 2 && (Capacity & (Capacity - 1)) == 0,
                  "Capacity debe ser potencia de dos");
    static_assert(std::is_trivially_copyable_v<T>,
                  "T debe ser trivialmente copiable (sin constructores que asignen memoria)");
    static_assert(std::atomic<size_t>::is_always_lock_free,
                  "std::atomic<size_t> debe ser lock-free en esta arquitectura");

public:
    /** Productor. Devuelve false si el búfer está lleno (el evento se descarta). */
    bool push(const T &item) noexcept {
        const size_t write = mWrite.load(std::memory_order_relaxed);
        const size_t read = mRead.load(std::memory_order_acquire);
        if (write - read == Capacity) return false;
        mBuffer[write & kMask] = item;
        mWrite.store(write + 1, std::memory_order_release);
        return true;
    }

    /** Consumidor (hilo de audio). Devuelve false si no hay eventos. */
    bool pop(T &out) noexcept {
        const size_t read = mRead.load(std::memory_order_relaxed);
        const size_t write = mWrite.load(std::memory_order_acquire);
        if (read == write) return false;
        out = mBuffer[read & kMask];
        mRead.store(read + 1, std::memory_order_release);
        return true;
    }

    /** Aproximado: solo para diagnóstico. */
    size_t sizeApprox() const noexcept {
        return mWrite.load(std::memory_order_relaxed) - mRead.load(std::memory_order_relaxed);
    }

private:
    static constexpr size_t kMask = Capacity - 1;
    static constexpr size_t kCacheLine = 64;

    // Cada índice en su propia línea de caché para evitar "false sharing"
    // entre el núcleo del productor y el del consumidor.
    alignas(kCacheLine) std::atomic<size_t> mWrite{0};
    alignas(kCacheLine) std::atomic<size_t> mRead{0};
    alignas(kCacheLine) std::array<T, Capacity> mBuffer{};
};

} // namespace piano
