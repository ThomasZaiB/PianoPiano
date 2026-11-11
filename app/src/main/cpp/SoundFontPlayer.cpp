// La implementación de TinySoundFont vive SOLO en esta unidad de compilación.
#define TSF_IMPLEMENTATION
#include "tsf.h"

#include "SoundFontPlayer.h"

#include <algorithm>
#include <cstring>

namespace piano {

namespace {
constexpr float kOutputGainDb = -6.0f; // margen para acordes densos con pedal
constexpr int kMidiSustainCC = 64;
} // namespace

SoundFontPlayer::~SoundFontPlayer() {
    if (mTsf) tsf_close(mTsf);
}

bool SoundFontPlayer::load(const void *data, int32_t sizeBytes) {
    if (!data || sizeBytes <= 0) return false;

    tsf *loaded = tsf_load_memory(data, sizeBytes);
    if (!loaded) return false;

    // Preset 0 del banco 0 = "Acoustic Grand Piano" en General MIDI.
    // Si el .sf2 es solo de piano y usa otra numeración, caemos al primer preset.
    int preset = tsf_get_presetindex(loaded, 0, 0);
    if (preset < 0) preset = 0;

    bool ok = tsf_set_max_voices(loaded, kMaxVoices) != 0;
    for (int ch = 0; ok && ch < kChannelCount; ++ch) {
        // Esta llamada crea el canal (asigna memoria) -> debe ocurrir aquí, no en el callback.
        ok = tsf_channel_set_presetindex(loaded, ch, preset) != 0;
    }
    if (!ok) {
        tsf_close(loaded);
        return false;
    }

    if (mTsf) tsf_close(mTsf);
    mTsf = loaded;
    return true;
}

void SoundFontPlayer::configure(int32_t sampleRate) {
    if (!mTsf) return;
    tsf_set_output(mTsf, TSF_STEREO_INTERLEAVED, sampleRate, kOutputGainDb);
    // Cambiar la frecuencia con voces sonando las desafinaría: se cortan.
    for (int ch = 0; ch < kChannelCount; ++ch) tsf_channel_sounds_off_all(mTsf, ch);
}

void SoundFontPlayer::noteOn(int channel, int key, int velocity) noexcept {
    if (!mTsf) return;
    tsf_channel_note_on(mTsf, channel, key, static_cast<float>(velocity) / 127.0f);
}

void SoundFontPlayer::noteOff(int channel, int key) noexcept {
    if (!mTsf) return;
    tsf_channel_note_off(mTsf, channel, key);
}

void SoundFontPlayer::allNotesOff() noexcept {
    if (!mTsf) return;
    for (int ch = 0; ch < kChannelCount; ++ch) tsf_channel_note_off_all(mTsf, ch);
}

void SoundFontPlayer::setSustain(int channel, bool down) noexcept {
    if (!mTsf) return;
    tsf_channel_midi_control(mTsf, channel, kMidiSustainCC, down ? 127 : 0);
}

void SoundFontPlayer::render(float *out, int32_t numFrames) noexcept {
    const int32_t samples = numFrames * 2;
    if (!mTsf) {
        std::memset(out, 0, sizeof(float) * static_cast<size_t>(samples));
        return;
    }
    tsf_render_float(mTsf, out, numFrames, 0);
    // Protección barata contra saturación digital (clipping duro a ±1).
    for (int32_t i = 0; i < samples; ++i) out[i] = std::clamp(out[i], -1.0f, 1.0f);
}

} // namespace piano
