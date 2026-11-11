#pragma once

#include <cstdint>

struct tsf; // TinySoundFont (definido en tsf.h)

namespace piano {

/**
 * Envoltorio de TinySoundFont con reglas de tiempo real explícitas.
 *
 * NO tiempo real (solo con el stream de audio cerrado): load(), configure(), ~SoundFontPlayer().
 * Tiempo real (solo desde el callback de audio): noteOn/noteOff/allNotesOff/setSustain/render.
 *
 * Por qué es seguro en el callback:
 *  - tsf_set_max_voices() pre-reserva las voces: tsf_note_on() roba la voz más antigua
 *    en release en vez de hacer realloc.
 *  - Los canales MIDI se crean en load(): la API tsf_channel_* solo asigna memoria
 *    la primera vez que se toca un canal nuevo.
 */
class SoundFontPlayer {
public:
    static constexpr int kChannelCount = 2; // 0 = mano derecha, 1 = mano izquierda
    static constexpr int kMaxVoices = 128;  // polifonía; el pedal de resonancia consume muchas

    SoundFontPlayer() = default;
    ~SoundFontPlayer();
    SoundFontPlayer(const SoundFontPlayer &) = delete;
    SoundFontPlayer &operator=(const SoundFontPlayer &) = delete;

    /** Parsea el .sf2 (TSF copia las muestras; el buffer puede liberarse después). */
    bool load(const void *data, int32_t sizeBytes);

    /** Ajusta la frecuencia de muestreo real del stream. */
    void configure(int32_t sampleRate);

    bool isLoaded() const { return mTsf != nullptr; }

    // ---- Tiempo real ----
    void noteOn(int channel, int key, int velocity) noexcept;
    void noteOff(int channel, int key) noexcept;
    void allNotesOff() noexcept;
    void setSustain(int channel, bool down) noexcept;
    /** Pedal arriba en todos los canales. */
    void resetSustain() noexcept;
    /** Escribe numFrames frames estéreo intercalados (sobrescribe, no mezcla). */
    void render(float *out, int32_t numFrames) noexcept;

private:
    tsf *mTsf = nullptr;
};

} // namespace piano
