# Preserve all application and runtime APIs, including reflection, JNI/JNA,
# serialization, service loading and JavaCPP. Only unused icon classes shrink.
-keep class !androidx.compose.material.icons.**,** { *; }
-keepattributes *
-dontoptimize
-dontobfuscate
# Finalization restores original class bytes, including retained icons. Avoid
# rebuilding stack maps for large Compose methods whose output is discarded.
-dontpreverify

# JavaCV is intentionally non-transitive: desktop uses its FFmpeg adapter only.
# These optional camera/OpenCL/OpenCV/UI adapters were never runtime dependencies.
-dontwarn org.bytedeco.opencv.**
-dontwarn org.bytedeco.artoolkitplus.**
-dontwarn org.bytedeco.flycapture.**
-dontwarn org.bytedeco.leptonica.**
-dontwarn org.bytedeco.libdc1394.**
-dontwarn org.bytedeco.libfreenect.**
-dontwarn org.bytedeco.libfreenect2.**
-dontwarn org.bytedeco.librealsense.**
-dontwarn org.bytedeco.librealsense2.**
-dontwarn org.bytedeco.videoinput.**
-dontwarn org.bytedeco.javacv.JavaCVCL$PinnedIplImage
-dontwarn org.opencv.core.**
-dontwarn com.jogamp.opencl.**
# JavaCV's optional JOGL canvas/OpenCL interop is not used by the FFmpeg adapter.
-dontwarn com.jogamp.opengl.**
-dontwarn com.jogamp.common.os.Platform
-dontwarn com.badlogic.gdx.graphics.**
-dontwarn javafx.scene.image.**
-dontwarn javafx.scene.paint.Color
-dontwarn android.graphics.Bitmap**

# JavaCPP's Maven tooling and SQLite's GraalVM substitutions are not used by JDK.
-dontwarn org.apache.maven.**
-dontwarn org.bytedeco.javacpp.tools.BuildMojo
-dontwarn org.bytedeco.javacpp.tools.CacheMojo
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn org.graalvm.nativeimage.hosted.**
-dontwarn org.osgi.annotation.**

# OkHttp selects the JDK TLS provider; these alternative providers are optional.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**

# Signature-polymorphic MethodHandle.invokeExact overloads have no fixed JDK
# declaration. Preserve ONNX's code and suppress only this analysis warning.
-dontwarn ai.onnxruntime.platform.Fp16Conversions
