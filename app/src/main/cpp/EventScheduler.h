#pragma once

#include <array>
#include <cstddef>

#include "EngineEvent.h"

namespace piano {

/**
 * Cola de prioridad (min-heap por tick) con capacidad fija y SIN asignaciones:
 * vive dentro de AudioEngine y solo la toca el hilo de audio.
 *
 * Guarda eventos de nota programados para un tick futuro (EngineEvent::value = tick).
 * Con el mismo tick, NoteOff sale antes que NoteOn: así una nota repetida
 * (Do-Do) se vuelve a atacar en vez de quedar cortada.
 */
template <size_t Capacity>
class EventScheduler {
public:
    bool push(const EngineEvent &e) noexcept {
        if (mSize == Capacity) return false;
        size_t i = mSize++;
        mHeap[i] = e;
        while (i > 0) {
            const size_t parent = (i - 1) / 2;
            if (!before(mHeap[i], mHeap[parent])) break;
            std::swap(mHeap[i], mHeap[parent]);
            i = parent;
        }
        return true;
    }

    bool empty() const noexcept { return mSize == 0; }
    size_t size() const noexcept { return mSize; }
    const EngineEvent &top() const noexcept { return mHeap[0]; }

    EngineEvent pop() noexcept {
        EngineEvent result = mHeap[0];
        mHeap[0] = mHeap[--mSize];
        size_t i = 0;
        for (;;) {
            const size_t l = 2 * i + 1, r = l + 1;
            size_t best = i;
            if (l < mSize && before(mHeap[l], mHeap[best])) best = l;
            if (r < mSize && before(mHeap[r], mHeap[best])) best = r;
            if (best == i) break;
            std::swap(mHeap[i], mHeap[best]);
            i = best;
        }
        return result;
    }

    void clear() noexcept { mSize = 0; }

private:
    static bool before(const EngineEvent &a, const EngineEvent &b) noexcept {
        if (a.value != b.value) return a.value < b.value;
        return rank(a) < rank(b);
    }
    static int rank(const EngineEvent &e) noexcept {
        return e.type == EventType::NoteOff ? 0 : 1;
    }

    std::array<EngineEvent, Capacity> mHeap{};
    size_t mSize = 0;
};

} // namespace piano
