#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#include "whisper.h"

#define LOG_TAG "VoiceBoardJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    std::mutex g_lock;
    struct whisper_context * g_ctx = nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_samnick_voiceboard_transcription_WhisperJNI_nativeInit(
        JNIEnv * env, jobject /* this */, jstring jpath) {
    std::lock_guard<std::mutex> lk(g_lock);

    const char * path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) {
        LOGE("nativeInit: null model path");
        return JNI_FALSE;
    }
    LOGI("loading model: %s", path);

    if (g_ctx) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    g_ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(jpath, path);

    if (!g_ctx) {
        LOGE("whisper_init_from_file_with_params returned null");
        return JNI_FALSE;
    }
    LOGI("model loaded ok");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_samnick_voiceboard_transcription_WhisperJNI_nativeTranscribe(
        JNIEnv * env, jobject /* this */, jfloatArray jpcm, jint n_threads) {
    std::lock_guard<std::mutex> lk(g_lock);
    if (!g_ctx) {
        LOGW("nativeTranscribe: no context loaded");
        return env->NewStringUTF("");
    }

    const jsize len = env->GetArrayLength(jpcm);
    if (len <= 0) {
        return env->NewStringUTF("");
    }

    std::vector<float> pcm(static_cast<size_t>(len));
    env->GetFloatArrayRegion(jpcm, 0, len, pcm.data());

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.n_threads        = std::max(1, static_cast<int>(n_threads));
    wparams.translate        = false;
    wparams.print_progress   = false;
    wparams.print_realtime   = false;
    wparams.print_special    = false;
    wparams.print_timestamps = false;
    wparams.no_context       = true;
    wparams.single_segment   = false;
    wparams.suppress_blank   = true;
    wparams.language         = "auto";

    LOGI("transcribe: %d samples, %d threads", len, wparams.n_threads);

    const int rc = whisper_full(g_ctx, wparams, pcm.data(), static_cast<int>(pcm.size()));
    if (rc != 0) {
        LOGE("whisper_full returned %d", rc);
        return env->NewStringUTF("");
    }

    std::string out;
    const int n = whisper_full_n_segments(g_ctx);
    for (int i = 0; i < n; ++i) {
        const char * seg = whisper_full_get_segment_text(g_ctx, i);
        if (seg) out += seg;
    }
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_samnick_voiceboard_transcription_WhisperJNI_nativeRelease(
        JNIEnv * /* env */, jobject /* this */) {
    std::lock_guard<std::mutex> lk(g_lock);
    if (g_ctx) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
        LOGI("context released");
    }
}
