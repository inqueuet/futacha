# Keep shared Kotlin serialization metadata for snapshot payloads.
-keepclassmembers class com.valoser.futacha.shared.watch.** {
    *** Companion;
}

# Firebase Analytics can optionally query the advertising ID. The app deliberately
# excludes that dependency and removes the AD_ID permission, so these classes are absent.
-dontwarn com.google.android.gms.ads.identifier.AdvertisingIdClient
-dontwarn com.google.android.gms.ads.identifier.AdvertisingIdClient$Info

# The shared module brings ONNX Runtime onto the classpath. Wear does not run
# analysis today, but if any ORT entry point survives shrinking, keep the whole
# package under its JNI names (libonnxruntime4j_jni.so looks classes up by name).
-if class ai.onnxruntime.OrtEnvironment
-keep class ai.onnxruntime.** { *; }
