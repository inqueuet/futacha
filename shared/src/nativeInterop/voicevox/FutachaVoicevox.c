#include "FutachaVoicevox.h"
#define VOICEVOX_LOAD_ONNXRUNTIME
#include "voicevox_core.h"
#include <dlfcn.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifdef __APPLE__
#include <TargetConditionals.h>
#endif

/* All calls are additionally serialized by the process-wide Kotlin worker.
 * Keep the library loaded: upstream ONNX initialization is process-global. */
static pthread_mutex_t load_lock = PTHREAD_MUTEX_INITIALIZER;
static void *core_library;
static char last_error[1024];
#define ENTRY(result, name, args) static result (*p_##name) args
ENTRY(struct VoicevoxLoadOnnxruntimeOptions, voicevox_make_default_load_onnxruntime_options, (void));
ENTRY(VoicevoxResultCode, voicevox_onnxruntime_load_once, (struct VoicevoxLoadOnnxruntimeOptions, const struct VoicevoxOnnxruntime **));
ENTRY(VoicevoxResultCode, voicevox_open_jtalk_rc_new, (const char *, struct OpenJtalkRc **));
ENTRY(void, voicevox_open_jtalk_rc_delete, (struct OpenJtalkRc *));
ENTRY(struct VoicevoxInitializeOptions, voicevox_make_default_initialize_options, (void));
ENTRY(VoicevoxResultCode, voicevox_synthesizer_new, (const struct VoicevoxOnnxruntime *, const struct OpenJtalkRc *, struct VoicevoxInitializeOptions, struct VoicevoxSynthesizer **));
ENTRY(void, voicevox_synthesizer_delete, (struct VoicevoxSynthesizer *));
ENTRY(VoicevoxResultCode, voicevox_voice_model_file_open, (const char *, struct VoicevoxVoiceModelFile **));
ENTRY(char *, voicevox_voice_model_file_create_metas_json, (const struct VoicevoxVoiceModelFile *));
ENTRY(void, voicevox_voice_model_file_delete, (struct VoicevoxVoiceModelFile *));
ENTRY(struct VoicevoxLoadVoiceModelOptions, voicevox_make_default_load_voice_model_options, (void));
ENTRY(VoicevoxResultCode, voicevox_synthesizer_load_voice_model, (const struct VoicevoxSynthesizer *, const struct VoicevoxVoiceModelFile *, struct VoicevoxLoadVoiceModelOptions));
ENTRY(VoicevoxResultCode, voicevox_synthesizer_create_audio_query, (const struct VoicevoxSynthesizer *, const char *, VoicevoxStyleId, char **));
ENTRY(struct VoicevoxSynthesisOptions, voicevox_make_default_synthesis_options, (void));
ENTRY(VoicevoxResultCode, voicevox_synthesizer_synthesis, (const struct VoicevoxSynthesizer *, const char *, VoicevoxStyleId, struct VoicevoxSynthesisOptions, uintptr_t *, uint8_t **));
ENTRY(void, voicevox_json_free, (char *));
ENTRY(void, voicevox_wav_free, (uint8_t *));
ENTRY(const char *, voicevox_error_result_to_message, (VoicevoxResultCode));

struct FutachaVoiceSession { struct OpenJtalkRc *dictionary; struct VoicevoxSynthesizer *synthesizer; };
static int error(const char *message) { snprintf(last_error, sizeof(last_error), "%s", message ? message : "音声の準備に失敗しました"); return -1; }
static int result(VoicevoxResultCode code) {
    return code == VOICEVOX_RESULT_OK ? 0 : error(p_voicevox_error_result_to_message(code));
}

static int load_core(const char *path) {
    pthread_mutex_lock(&load_lock);
    if (core_library) { pthread_mutex_unlock(&load_lock); return 0; }
    void *library = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (!library) { error(dlerror()); pthread_mutex_unlock(&load_lock); return -1; }
#define LOAD(name) do { *(void **)(&p_##name) = dlsym(library, #name); if (!p_##name) { error("音声ライブラリのAPIが一致しません"); dlclose(library); pthread_mutex_unlock(&load_lock); return -1; } } while (0)
    LOAD(voicevox_make_default_load_onnxruntime_options); LOAD(voicevox_onnxruntime_load_once);
    LOAD(voicevox_open_jtalk_rc_new); LOAD(voicevox_open_jtalk_rc_delete);
    LOAD(voicevox_make_default_initialize_options); LOAD(voicevox_synthesizer_new); LOAD(voicevox_synthesizer_delete);
    LOAD(voicevox_voice_model_file_open); LOAD(voicevox_voice_model_file_create_metas_json); LOAD(voicevox_voice_model_file_delete);
    LOAD(voicevox_make_default_load_voice_model_options); LOAD(voicevox_synthesizer_load_voice_model);
    LOAD(voicevox_synthesizer_create_audio_query); LOAD(voicevox_make_default_synthesis_options); LOAD(voicevox_synthesizer_synthesis);
    LOAD(voicevox_json_free); LOAD(voicevox_wav_free); LOAD(voicevox_error_result_to_message);
#undef LOAD
    core_library = library;
    pthread_mutex_unlock(&load_lock);
    return 0;
}

int futacha_voice_open(const char *core, const char *runtime, const char *dictionary,
                      const char *model_path, unsigned threads, FutachaVoiceSession **out) {
#if defined(__APPLE__) && TARGET_OS_IOS
    /* Guard the native loader itself, in addition to the Kotlin/UI capability check.
     * The bundled runtime requires iOS 16; neither it nor core is linked at launch. */
    if (__builtin_available(iOS 16.0, *)) {} else return error("ずんだもんにはiOS 16以降が必要です");
#endif
    *out = NULL;
    if (load_core(core)) return -1;
    struct VoicevoxLoadOnnxruntimeOptions load = p_voicevox_make_default_load_onnxruntime_options();
    load.filename = runtime;
    const struct VoicevoxOnnxruntime *ort = NULL;
    if (result(p_voicevox_onnxruntime_load_once(load, &ort))) return -1;
    FutachaVoiceSession *session = calloc(1, sizeof(*session));
    if (!session) return error("音声用のメモリを確保できません");
    struct VoicevoxVoiceModelFile *model = NULL;
    if (result(p_voicevox_open_jtalk_rc_new(dictionary, &session->dictionary))) goto failed;
    struct VoicevoxInitializeOptions options = p_voicevox_make_default_initialize_options();
    options.acceleration_mode = VOICEVOX_ACCELERATION_MODE_CPU;
    options.cpu_num_threads = threads > 0 && threads <= 2 ? threads : 1;
    if (result(p_voicevox_synthesizer_new(ort, session->dictionary, options, &session->synthesizer))) goto failed;
    if (result(p_voicevox_voice_model_file_open(model_path, &model))) goto failed;
    char *metas = p_voicevox_voice_model_file_create_metas_json(model);
    int speaker_present = metas && strstr(metas, "388f246b-8c41-4ac1-8e2d-5d79f3ff56d9");
    if (metas) p_voicevox_json_free(metas);
    if (!speaker_present) { error("ずんだもんの音声データではありません"); goto failed; }
    if (result(p_voicevox_synthesizer_load_voice_model(session->synthesizer, model, p_voicevox_make_default_load_voice_model_options()))) goto failed;
    p_voicevox_voice_model_file_delete(model);
    *out = session;
    return 0;
failed:
    if (model) p_voicevox_voice_model_file_delete(model);
    futacha_voice_close(session);
    return -1;
}

int futacha_voice_query(FutachaVoiceSession *session, const char *text, char **json) {
    return result(p_voicevox_synthesizer_create_audio_query(session->synthesizer, text, 3, json));
}
int futacha_voice_synthesis(FutachaVoiceSession *session, const char *query, uint8_t **wav, size_t *size) {
    uintptr_t length = 0;
    int code = result(p_voicevox_synthesizer_synthesis(session->synthesizer, query, 3,
                 p_voicevox_make_default_synthesis_options(), &length, wav));
    *size = length;
    return code;
}
void futacha_voice_json_free(char *json) { if (json) p_voicevox_json_free(json); }
void futacha_voice_wav_free(uint8_t *wav) { if (wav) p_voicevox_wav_free(wav); }
void futacha_voice_close(FutachaVoiceSession *session) {
    if (!session) return;
    if (session->synthesizer) p_voicevox_synthesizer_delete(session->synthesizer);
    if (session->dictionary) p_voicevox_open_jtalk_rc_delete(session->dictionary);
    free(session);
}
const char *futacha_voice_error(void) { return last_error; }
