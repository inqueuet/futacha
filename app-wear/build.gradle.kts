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

android {
    namespace = "com.valoser.futacha.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.valoser.futacha"
        minSdk = 26
        targetSdk = 36
        versionCode = 100_000_003
        versionName = "1.0"
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
            isMinifyEnabled = false
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
