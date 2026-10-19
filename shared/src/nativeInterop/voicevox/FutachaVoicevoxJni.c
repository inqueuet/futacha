#include "FutachaVoicevox.h"
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#define JNI(name) Java_com_valoser_futacha_shared_audio_voicevox_VoicevoxNative_##name
static void fail(JNIEnv *env) { (*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), futacha_voice_error()); }
/* Java's GetStringUTFChars is modified UTF-8, so text/query payloads cross as UTF-8 bytes. */
static char *utf8(JNIEnv *env, jbyteArray bytes) {
    jsize length = (*env)->GetArrayLength(env, bytes);
    char *text = malloc((size_t)length + 1);
    if (!text) { (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"), "Voice input"); return NULL; }
    (*env)->GetByteArrayRegion(env, bytes, 0, length, (jbyte *)text);
    text[length] = 0;
    return text;
}
JNIEXPORT jlong JNICALL JNI(open)(JNIEnv *env, jobject self, jstring core, jstring runtime, jstring dictionary, jstring model, jint threads) {
    const char *a = (*env)->GetStringUTFChars(env, core, NULL), *b = (*env)->GetStringUTFChars(env, runtime, NULL);
    const char *c = (*env)->GetStringUTFChars(env, dictionary, NULL), *d = (*env)->GetStringUTFChars(env, model, NULL);
    FutachaVoiceSession *session = NULL;
    int status = futacha_voice_open(a, b, c, d, (unsigned)threads, &session);
    (*env)->ReleaseStringUTFChars(env, core, a); (*env)->ReleaseStringUTFChars(env, runtime, b);
    (*env)->ReleaseStringUTFChars(env, dictionary, c); (*env)->ReleaseStringUTFChars(env, model, d);
    if (status) fail(env);
    return (jlong)(intptr_t)session;
}
JNIEXPORT jbyteArray JNICALL JNI(query)(JNIEnv *env, jobject self, jlong handle, jbyteArray bytes) {
    char *text = utf8(env, bytes), *json = NULL;
    if (!text) return NULL;
    int status = futacha_voice_query((FutachaVoiceSession *)(intptr_t)handle, text, &json); free(text);
    if (status) { fail(env); return NULL; }
    jbyteArray output = (*env)->NewByteArray(env, (jsize)strlen(json));
    if (output) (*env)->SetByteArrayRegion(env, output, 0, (jsize)strlen(json), (jbyte *)json);
    futacha_voice_json_free(json);
    return output;
}
JNIEXPORT jbyteArray JNICALL JNI(synthesize)(JNIEnv *env, jobject self, jlong handle, jbyteArray bytes) {
    char *query = utf8(env, bytes); uint8_t *wav = NULL; size_t length = 0;
    if (!query) return NULL;
    int status = futacha_voice_synthesis((FutachaVoiceSession *)(intptr_t)handle, query, &wav, &length); free(query);
    if (status) { fail(env); return NULL; }
    if (length > 16 * 1024 * 1024) { futacha_voice_wav_free(wav); (*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), "音声が長すぎます"); return NULL; }
    jbyteArray output = (*env)->NewByteArray(env, (jsize)length);
    if (output) (*env)->SetByteArrayRegion(env, output, 0, (jsize)length, (jbyte *)wav);
    futacha_voice_wav_free(wav);
    return output;
}
JNIEXPORT void JNICALL JNI(close)(JNIEnv *env, jobject self, jlong handle) { futacha_voice_close((FutachaVoiceSession *)(intptr_t)handle); }
