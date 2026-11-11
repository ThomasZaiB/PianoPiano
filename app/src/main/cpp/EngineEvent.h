#pragma once

#include <cstdint>

namespace piano {

/**
 * Comandos que viajan de Kotlin al hilo de audio por el ring buffer.
 * Los valores numéricos DEBEN coincidir con las constantes EV_* de AudioBridge.kt.
 */
enum class EventType : uint8_t {
    // Notas: value < 0  -> inmediata (teclado en vivo)
    //        value >= 0 -> programada para ese tick exacto (reproducción, sample-accurate)
    NoteOn = 0,         // channel, key, velocity, value
    NoteOff = 1,        // channel, key, value
    AllNotesOff = 2,    // apaga todo con release natural
    Sustain = 3,        // channel, value >= 0.5 -> pedal abajo
    SetTempo = 4,       // value = BPM
    TransportStart = 5, // el reloj PPQ empieza a avanzar
    TransportStop = 6,  // el reloj PPQ se congela y se vacía la cola programada
    Seek = 7,           // value = tick absoluto; vacía la cola programada
};

constexpr int kEventTypeCount = 8;

/** 16 bytes, trivialmente copiable: cabe en el ring buffer sin asignaciones. */
struct EngineEvent {
    EventType type;
    uint8_t channel;  // 0 = mano derecha, 1 = mano izquierda
    uint8_t key;      // nota MIDI 0..127 (A0 = 21, C8 = 108)
    uint8_t velocity; // 1..127
    uint32_t reserved;
    double value;
};

static_assert(sizeof(EngineEvent) == 16, "EngineEvent debería ocupar 16 bytes");

} // namespace piano
