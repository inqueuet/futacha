import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun signingProperty(name: String): String? =
    localProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull

val releaseSigningStoreFile = signingProperty("FUTACHA_RELEASE_STORE_FILE")?.let { rootProject.file(it) }
val releaseSigningStorePassword = signingProperty("FUTACHA_RELEASE_STORE_PASSWORD")
val releaseSigningKeyAlias = signingProperty("FUTACHA_RELEASE_KEY_ALIAS")
val releaseSigningKeyPassword = signingProperty("FUTACHA_RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig = releaseSigningStoreFile != null &&
    !releaseSigningStorePassword.isNullOrBlank() &&
    !releaseSigningKeyAlias.isNullOrBlank() &&
    !releaseSigningKeyPassword.isNullOrBlank()
// Android Studio's "Generate Signed Bundle / APK" passes the keystore as
// -Pandroid.injected.signing.*; AGP then signs the requested variant with it,
// overriding the build script's signingConfig (K4-1).
val hasInjectedSigningConfig = listOf(
    "android.injected.signing.store.file",
    "android.injected.signing.store.password",
    "android.injected.signing.key.alias",
    "android.injected.signing.key.password"
).all { !providers.gradleProperty(it).orNull.isNullOrBlank() }

// An unsigned release AAB can never be uploaded, so a scheduled release bundle
// fails before any task runs when signing is missing. Unsigned release APKs stay
// buildable because R8/verification runs use them; those only get a warning so a
// missing signing setup is never silent. The check reads the resolved task graph,
// so every spelling (bundle, build, bR, app-android:bundleRelease, ...) is covered
// (K4-2).
gradle.taskGraph.whenReady {
    if (hasReleaseSigningConfig || hasInjectedSigningConfig) return@whenReady
    val scheduledReleaseTasks = allTasks
        .filter { it.project == project }
        .map { it.name }
        .filter {
            it == "bundleRelease" || it == "signReleaseBundle" ||
                it == "packageRelease" || it == "assembleRelease"
        }
    if (scheduledReleaseTasks.isEmpty()) return@whenReady
    val message = "${project.path}: release signing is not configured (FUTACHA_RELEASE_STORE_FILE, " +
        "FUTACHA_RELEASE_STORE_PASSWORD, FUTACHA_RELEASE_KEY_ALIAS, FUTACHA_RELEASE_KEY_PASSWORD " +
        "in local.properties, Gradle properties or the environment, or Android Studio's " +
        "-Pandroid.injected.signing.* properties)."
    if ("bundleRelease" in scheduledReleaseTasks || "signReleaseBundle" in scheduledReleaseTasks) {
        throw GradleException("$message Refusing to build an unsigned release AAB.")
    }
    logger.warn("WARNING: $message The release APK will be unsigned.")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-api")
    exclude(group = "com.google.android.gms", module = "play-services-ads-base")
    exclude(group = "com.google.android.gms", module = "play-services-ads-identifier")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
    exclude(group = "com.google.android.ump", module = "user-messaging-platform")
    exclude(group = "androidx.privacysandbox.ads", module = "ads-adservices")
    exclude(group = "androidx.privacysandbox.ads", module = "ads-adservices-java")
}

android {
    namespace = "com.valoser.futacha.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.valoser.futacha"
        minSdk = 26
        targetSdk = 36
        versionCode = 100_000_014
        versionName = "1.7"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = releaseSigningStoreFile
                storePassword = releaseSigningStorePassword
                keyAlias = releaseSigningKeyAlias
                keyPassword = releaseSigningKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

// The watch UI has no selectable text today, but Foundation Android is on its classpath through
// :shared; apply the same verified smart-selection patch as the phone app so it cannot regress.
com.valoser.futacha.instrumentation.ComposeSelectionGuardFactory.requireVerifiedFoundation(
    libs.androidx.compose.foundation.guarded.get().versionConstraint.strictVersion
)

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.instrumentation.transformClassesWith(
            com.valoser.futacha.instrumentation.ComposeSelectionGuardFactory::class.java,
            com.android.build.api.instrumentation.InstrumentationScope.ALL
        ) {}
        variant.instrumentation.setAsmFramesComputationMode(
            com.android.build.api.instrumentation.FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
        )
    }
}

dependencies {
    constraints {
        implementation(libs.androidx.compose.foundation.guarded) {
            because("ComposeSelectionGuardFactory patches two verified 1.13.0-alpha02 call sites; review before upgrading")
        }
    }

    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.unit)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.remote.interactions)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.play.services.wearable)
    implementation(libs.play.services.tasks)
    implementation(libs.guava)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.wear.compose.ui.tooling)
    debugImplementation(libs.androidx.wear.tiles.renderer)
}
