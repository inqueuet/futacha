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
        versionCode = 100_000_009
        versionName = "1.3"
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

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.remote.interactions)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.play.services.wearable)
    implementation(libs.guava)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.wear.compose.ui.tooling)
    debugImplementation(libs.androidx.wear.tiles.renderer)
}
