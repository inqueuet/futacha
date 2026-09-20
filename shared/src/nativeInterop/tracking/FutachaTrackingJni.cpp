#include "FutachaTracking.h"
#include <jni.h>
#include <cstdint>

namespace {
FutachaTracker* tracker(jlong pointer) {
    return reinterpret_cast<FutachaTracker*>(static_cast<intptr_t>(pointer));
}
bool validFrame(JNIEnv* env, jbyteArray gray, jint width, jint height) {
    const auto count = static_cast<int64_t>(width) * height;
    return gray && width > 0 && height > 0 && count <= 4194304 &&
           count == env->GetArrayLength(gray);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_valoser_futacha_shared_media_analysis_TrackingNative_create(JNIEnv*, jobject) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(futacha_tracker_create()));
}

extern "C" JNIEXPORT void JNICALL
Java_com_valoser_futacha_shared_media_analysis_TrackingNative_destroy(JNIEnv*, jobject, jlong pointer) {
    futacha_tracker_destroy(tracker(pointer));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_valoser_futacha_shared_media_analysis_TrackingNative_seed(
    JNIEnv* env, jobject, jlong pointer, jbyteArray gray, jint width, jint height,
    jfloat x, jfloat y, jfloat w, jfloat h) {
    if (!pointer || !validFrame(env, gray, width, height)) return -1;
    jbyte* bytes = env->GetByteArrayElements(gray, nullptr);
    if (!bytes) return -1;
    const int status = futacha_tracker_seed(tracker(pointer), bytes, width, height, x, y, w, h);
    env->ReleaseByteArrayElements(gray, bytes, JNI_ABORT);
    return status;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_valoser_futacha_shared_media_analysis_TrackingNative_step(
    JNIEnv* env, jobject, jlong pointer, jbyteArray gray, jint width, jint height,
    jboolean sceneCut, jfloatArray output) {
    if (!pointer || !validFrame(env, gray, width, height) || !output || env->GetArrayLength(output) != 5) return -1;
    jbyte* bytes = env->GetByteArrayElements(gray, nullptr);
    if (!bytes) return -1;
    float result[5];
    const int status = futacha_tracker_step(tracker(pointer), bytes, width, height, sceneCut ? 1 : 0, result);
    env->ReleaseByteArrayElements(gray, bytes, JNI_ABORT);
    if (status == 0) env->SetFloatArrayRegion(output, 0, 5, result);
    return status;
}
