// Puente JNI: traduce llamadas de AudioBridge.kt al motor C++.
// Ninguna de estas funciones se ejecuta en el hilo de audio.
#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <oboe/Oboe.h>

#include <climits>
#include <cstdio>
#include <memory>

#include "AudioEngine.h"

using piano::AudioEngine;
using piano::EngineEvent;
using piano::EventType;

namespace {

/**
 * Instancia única del motor durante toda la vida del proceso. No se destruye nunca
 * mientras la app corre, así ninguna llamada JNI puede tocar memoria liberada.
 */
AudioEngine &engine() {
    static std::shared_ptr<AudioEngine> instance = std::make_shared<AudioEngine>();
    return *instance;
}

template <typename T>
T clampTo(jint v, T lo, T hi) {
    return static_cast<T>(v < lo ? lo : (v > hi ? hi : v));
}

} // namespace

#define JNI_FN(name) Java_com_tuapp_piano_data_audio_AudioBridge_##name

extern "C" {

JNIEXPORT jstring JNICALL JNI_FN(nativeVersion)(JNIEnv *env, jobject) {
    char buf[64];
    snprintf(buf, sizeof(buf), "Oboe %d.%d", oboe::Version::Major, oboe::Version::Minor);
    return env->NewStringUTF(buf);
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeLoadSoundFont)(JNIEnv *env, jobject, jobject assetManager,
                                                       jstring assetPath) {
    AAssetManager *manager = AAssetManager_fromJava(env, assetManager);
    if (!manager || !assetPath) return JNI_FALSE;

    const char *path = env->GetStringUTFChars(assetPath, nullptr);
    // AASSET_MODE_BUFFER + noCompress("sf2") en Gradle = el archivo se mapea, no se copia.
    AAsset *asset = AAssetManager_open(manager, path, AASSET_MODE_BUFFER);
    env->ReleaseStringUTFChars(assetPath, path);
    if (!asset) return JNI_FALSE;

    const void *data = AAsset_getBuffer(asset);
    const off64_t length = AAsset_getLength64(asset);
    bool ok = data != nullptr && length > 0 && length < INT_MAX &&
              engine().loadSoundFont(data, static_cast<int32_t>(length));
    AAsset_close(asset);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeStart)(JNIEnv *, jobject) {
    return engine().start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_FN(nativeStop)(JNIEnv *, jobject) {
    engine().stop();
}

JNIEXPORT jboolean JNICALL JNI_FN(nativePostEvent)(JNIEnv *, jobject, jint type, jint channel,
                                                   jint key, jint velocity, jdouble value) {
    if (type < 0 || type >= piano::kEventTypeCount) return JNI_FALSE;
    EngineEvent event{};
    event.type = static_cast<EventType>(type);
    event.channel = clampTo<uint8_t>(channel, 0, piano::SoundFontPlayer::kChannelCount - 1);
    event.key = clampTo<uint8_t>(key, 0, 127);
    event.velocity = clampTo<uint8_t>(velocity, 1, 127);
    event.value = value;
    return engine().postEvent(event) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Rellena arrays preasignados por Kotlin (sin crear objetos en cada frame):
 *   longs   = [framePosition, hostTimeNs]
 *   doubles = [tick, ticksPerSecond, bpm, sampleRate, running(0/1)]
 */
JNIEXPORT void JNICALL JNI_FN(nativeReadClock)(JNIEnv *env, jobject, jlongArray longs,
                                               jdoubleArray doubles) {
    const piano::ClockSnapshot s = engine().readClock();
    const jlong l[2] = {s.framePosition, s.hostTimeNs};
    const jdouble d[5] = {s.tick, s.ticksPerSecond, s.bpm, static_cast<double>(s.sampleRate),
                          s.running ? 1.0 : 0.0};
    env->SetLongArrayRegion(longs, 0, 2, l);
    env->SetDoubleArrayRegion(doubles, 0, 5, d);
}

JNIEXPORT jdouble JNICALL JNI_FN(nativeGetLatencyMillis)(JNIEnv *, jobject) {
    return engine().latencyMillis();
}

JNIEXPORT jint JNICALL JNI_FN(nativeGetXRunCount)(JNIEnv *, jobject) {
    return engine().xRunCount();
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeIsStreamOpen)(JNIEnv *, jobject) {
    return engine().isStreamOpen() ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
