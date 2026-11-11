#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

namespace piano {

// ============================================================================
// Limitador de picos
// ============================================================================

/**
 * Ganancia que deja una envolvente de pico por debajo del umbral. Función pura: con la
 * envolvente bajo el umbral no toca nada (ganancia 1); por encima, la escala justo lo
 * necesario para que el pico quede en el umbral.
 */
inline float gananciaLimitador(float envolvente, float umbral) noexcept {
    return envolvente > umbral ? umbral / envolvente : 1.0f;
}

/** Coeficiente de un filtro de un polo que recorre ~63 % del camino en [ms]. */
inline float coeficienteTiempo(float ms, int32_t sampleRate) noexcept {
    if (ms <= 0.0f || sampleRate <= 0) return 0.0f;
    return std::exp(-1.0f / (ms * 0.001f * static_cast<float>(sampleRate)));
}

/**
 * Sustituye al recorte duro: con acordes densos y pedal la suma pasa de ±1 y el clamp
 * muestra a muestra se oye como distorsión digital. Un seguidor de envolvente de pico
 * (ataque rápido, relajación lenta) calcula una sola ganancia para los dos canales, así
 * la imagen estéreo no se mueve. Sin memoria dinámica: dos floats de estado.
 */
class Limitador {
public:
    static constexpr float kUmbral = 0.89f;        // ≈ -1 dBFS: margen para los conversores
    static constexpr float kAtaqueMs = 1.0f;
    static constexpr float kRelajacionMs = 100.0f;

    void configurar(int32_t sampleRate) noexcept {
        mAtaque = coeficienteTiempo(kAtaqueMs, sampleRate);
        mRelajacion = coeficienteTiempo(kRelajacionMs, sampleRate);
        mEnvolvente = 0.0f;
    }

    /** Estéreo intercalado, en el sitio. */
    void procesar(float *muestras, int32_t frames) noexcept {
        for (int32_t i = 0; i < frames; ++i) {
            float &l = muestras[2 * i];
            float &r = muestras[2 * i + 1];
            const float pico = std::max(std::fabs(l), std::fabs(r));
            const float coef = pico > mEnvolvente ? mAtaque : mRelajacion;
            mEnvolvente = coef * mEnvolvente + (1.0f - coef) * pico;
            // max(pico, envolvente): el ataque de 1 ms deja pasar el primer pico de un golpe
            // seco; así ese pico también se escala y el clamp final casi nunca actúa.
            const float g = gananciaLimitador(std::max(pico, mEnvolvente), kUmbral);
            l *= g;
            r *= g;
        }
    }

private:
    float mAtaque = 0.0f;
    float mRelajacion = 0.0f;
    float mEnvolvente = 0.0f;
};

// ============================================================================
// Metrónomo
// ============================================================================

/**
 * Clic de metrónomo sintetizado: un seno corto con caída exponencial (~30 ms audibles).
 * Lo dispara el hilo de audio en la muestra exacta del tiempo, así que no depende del
 * jitter de Kotlin ni ocupa teclas del piano.
 */
class Metronomo {
public:
    static constexpr float kFrecuenciaAcento = 2093.0f; // Do7: primer tiempo, más agudo
    static constexpr float kFrecuenciaTiempo = 1568.0f; // Sol6
    static constexpr float kVolumenAcento = 0.45f;
    static constexpr float kVolumenTiempo = 0.28f;
    static constexpr float kCaidaMs = 7.0f;             // constante de tiempo: inaudible a ~30 ms
    static constexpr float kSilencio = 1.0e-4f;

    void configurar(int32_t sampleRate) noexcept {
        mSampleRate = sampleRate;
        mCaida = coeficienteTiempo(kCaidaMs, sampleRate);
        mAmplitud = 0.0f;
        mPendiente = -1;
    }

    /** Programa un clic en [offset] frames dentro del bloque que se va a mezclar. */
    void disparar(bool acento, int32_t offset) noexcept {
        mPendiente = offset;
        mPendienteAcento = acento;
    }

    /** Suma el clic (mono, a los dos canales) sobre lo que ya hay en el bloque. */
    void mezclar(float *muestras, int32_t frames) noexcept {
        if (mAmplitud < kSilencio && mPendiente < 0) return;
        for (int32_t i = 0; i < frames; ++i) {
            if (i == mPendiente) arrancar();
            if (mAmplitud < kSilencio) continue;
            const float s = mAmplitud * std::sin(mFase);
            mFase += mIncremento;
            if (mFase > kDosPi) mFase -= kDosPi;
            mAmplitud *= mCaida;
            muestras[2 * i] += s;
            muestras[2 * i + 1] += s;
        }
        mPendiente = -1;
    }

    void silenciar() noexcept {
        mAmplitud = 0.0f;
        mPendiente = -1;
    }

private:
    static constexpr float kDosPi = 6.28318530718f;

    void arrancar() noexcept {
        const float f = mPendienteAcento ? kFrecuenciaAcento : kFrecuenciaTiempo;
        mIncremento = mSampleRate > 0 ? kDosPi * f / static_cast<float>(mSampleRate) : 0.0f;
        mAmplitud = mPendienteAcento ? kVolumenAcento : kVolumenTiempo;
        mFase = 0.0f; // empieza en cero: sin escalón al arrancar
    }

    int32_t mSampleRate = 0;
    float mCaida = 0.0f;
    float mAmplitud = 0.0f;
    float mFase = 0.0f;
    float mIncremento = 0.0f;
    int32_t mPendiente = -1;
    bool mPendienteAcento = false;
};

// ============================================================================
// Reverberación (Freeverb, de Jezar at Dreampoint, dominio público)
// ============================================================================

/**
 * Reverberación ligera tipo Freeverb: 8 filtros peine con amortiguación en paralelo y 4
 * pasa-todo en serie, por canal. Los búferes se reservan en [configurar] (stream cerrado)
 * con el tamaño que corresponde a la frecuencia real del stream; en el hilo de audio solo
 * se leen y escriben.
 */
class Reverberacion {
public:
    enum class Preset : uint8_t { Apagada = 0, Sala = 1, Auditorio = 2 };

    /** Reserva memoria: SOLO con el stream cerrado. */
    void configurar(int32_t sampleRate) {
        const float escala = static_cast<float>(sampleRate) / 44100.0f;
        for (int c = 0; c < 2; ++c) {
            const int desfase = c == 0 ? 0 : kSeparacionEstereo;
            for (int i = 0; i < kPeines; ++i) mPeine[c][i].reservar(escalar(kAfinacionPeine[i] + desfase, escala));
            for (int i = 0; i < kPasaTodo; ++i) mPasaTodo[c][i].reservar(escalar(kAfinacionPasaTodo[i] + desfase, escala));
        }
        aplicar(mPreset);
    }

    /** Hilo de audio: solo cambia coeficientes (y vacía las colas al reactivarse). */
    void setPreset(Preset p) noexcept {
        if (p == mPreset) return;
        if (mPreset == Preset::Apagada) limpiar(); // sin restos viejos al volver a encender
        aplicar(p);
    }

    /** Estéreo intercalado, en el sitio: seco + [mMezcla] de cola. */
    void procesar(float *muestras, int32_t frames) noexcept {
        if (mPreset == Preset::Apagada || !listo()) return;
        for (int32_t i = 0; i < frames; ++i) {
            const float entrada = (muestras[2 * i] + muestras[2 * i + 1]) * kGananciaEntrada;
            float sal[2] = {0.0f, 0.0f};
            for (int c = 0; c < 2; ++c) {
                for (int k = 0; k < kPeines; ++k) sal[c] += mPeine[c][k].procesar(entrada, mRealimentacion, mAmortiguacion);
                for (int k = 0; k < kPasaTodo; ++k) sal[c] = mPasaTodo[c][k].procesar(sal[c]);
            }
            muestras[2 * i] += mMezcla * sal[0];
            muestras[2 * i + 1] += mMezcla * sal[1];
        }
    }

private:
    static constexpr int kPeines = 8;
    static constexpr int kPasaTodo = 4;
    static constexpr int kSeparacionEstereo = 23;
    static constexpr int kAfinacionPeine[kPeines] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
    static constexpr int kAfinacionPasaTodo[kPasaTodo] = {556, 441, 341, 225};
    static constexpr float kGananciaEntrada = 0.015f;
    static constexpr float kRealimentacionPasaTodo = 0.5f;

    // Parámetros por preset (tamaño de sala, amortiguación de agudos, mezcla húmeda).
    static constexpr float kSalaTamano = 0.50f, kSalaAmortiguacion = 0.50f, kSalaMezcla = 0.13f;
    static constexpr float kAuditorioTamano = 0.80f, kAuditorioAmortiguacion = 0.35f, kAuditorioMezcla = 0.15f;

    struct Peine {
        std::vector<float> buf;
        size_t pos = 0;
        float filtro = 0.0f;
        void reservar(size_t n) { buf.assign(std::max<size_t>(n, 1), 0.0f); pos = 0; filtro = 0.0f; }
        float procesar(float x, float realim, float amort) noexcept {
            const float y = buf[pos];
            filtro = y * (1.0f - amort) + filtro * amort;
            buf[pos] = x + filtro * realim;
            if (++pos >= buf.size()) pos = 0;
            return y;
        }
        void limpiar() noexcept { std::fill(buf.begin(), buf.end(), 0.0f); filtro = 0.0f; }
    };

    struct PasaTodo {
        std::vector<float> buf;
        size_t pos = 0;
        void reservar(size_t n) { buf.assign(std::max<size_t>(n, 1), 0.0f); pos = 0; }
        float procesar(float x) noexcept {
            const float b = buf[pos];
            buf[pos] = x + b * kRealimentacionPasaTodo;
            if (++pos >= buf.size()) pos = 0;
            return b - x;
        }
        void limpiar() noexcept { std::fill(buf.begin(), buf.end(), 0.0f); }
    };

    static size_t escalar(int muestras, float escala) {
        return static_cast<size_t>(std::lround(static_cast<float>(muestras) * escala));
    }

    bool listo() const noexcept { return !mPeine[0][0].buf.empty(); }

    void limpiar() noexcept {
        for (int c = 0; c < 2; ++c) {
            for (auto &p : mPeine[c]) p.limpiar();
            for (auto &p : mPasaTodo[c]) p.limpiar();
        }
    }

    void aplicar(Preset p) noexcept {
        mPreset = p;
        float tamano = kSalaTamano, amort = kSalaAmortiguacion, mezcla = kSalaMezcla;
        if (p == Preset::Auditorio) {
            tamano = kAuditorioTamano; amort = kAuditorioAmortiguacion; mezcla = kAuditorioMezcla;
        }
        // Escalado original de Freeverb: realimentación 0,7–0,98 y amortiguación 0–0,4.
        mRealimentacion = tamano * 0.28f + 0.7f;
        mAmortiguacion = amort * 0.4f;
        mMezcla = p == Preset::Apagada ? 0.0f : mezcla;
    }

    Peine mPeine[2][kPeines];
    PasaTodo mPasaTodo[2][kPasaTodo];
    Preset mPreset = Preset::Sala;
    float mRealimentacion = 0.84f;
    float mAmortiguacion = 0.2f;
    float mMezcla = kSalaMezcla;
};

} // namespace piano
