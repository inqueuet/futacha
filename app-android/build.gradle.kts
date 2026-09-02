import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Sync
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

// Compose Multiplatform 1.11.1 generates accessors for the new AGP 9.3 KMP
// library target, but its generated common resources are not added to the
// Android AAR assets.  Package the shared source assets in the Android host
// under the exact path expected by painterResource.  The iOS/JVM targets keep
// using the Compose resource plugin's normal target aggregation.
val sharedComposeAndroidAssets = layout.buildDirectory.dir("generated/sharedComposeResources/assets")
val syncSharedComposeResourcesForAndroid by tasks.registering(Sync::class) {
    from(project(":shared").layout.projectDirectory.dir("src/commonMain/composeResources")) {
        into("composeResources/futacha.shared.generated.resources")
    }
    into(sharedComposeAndroidAssets)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

baselineProfile {
    mergeIntoMain = true
    // Keep local debug builds fast while ensuring every shipping APK refreshes
    // the profile from the checked critical journeys before it is packaged.
    automaticGenerationDuringBuild = true
}

val hasGoogleServicesConfig = file("google-services.json").exists() ||
    file("src/debug/google-services.json").exists() ||
    file("src/release/google-services.json").exists()

if (hasGoogleServicesConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
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
    namespace = "com.valoser.futacha"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.valoser.futacha"
        minSdk = 26
        targetSdk = 37
        versionCode = 164
        versionName = "10.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["benchmarkFixtureEnabled"] = "false"
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
    sourceSets.named("main") {
        // AGP 9.3 rejects Providers on the legacy SourceSet API.  This is a
        // fixed build-directory path; merge*Assets is explicitly wired to the
        // producer task below.
        assets.directories.add(sharedComposeAndroidAssets.get().asFile.absolutePath)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        if (variant.name == "nonMinifiedRelease" || variant.name == "benchmarkRelease") {
            variant.manifestPlaceholders.put("benchmarkFixtureEnabled", "true")
        }
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(syncSharedComposeResourcesForAndroid)
}

// Lint model and analysis tasks read the asset source set directly instead of
// going through merge*Assets, so every Debug/Release/Vital lint task needs the producer.
tasks.matching {
    it.name.contains("lint", ignoreCase = true)
}.configureEach {
    dependsOn(syncSharedComposeResourcesForAndroid)
}

// Android Studio redirects every APK-producing project to one directory. Preserve
// the target APK before the instrumentation APK replaces the shared metadata,
// then point only the target listing back to that preserved copy.
val injectedApkLocation = providers.gradleProperty("android.injected.apk.location")
if (injectedApkLocation.isPresent) {
    val injectedNonMinifiedReleaseDirectory = injectedApkLocation.map {
        rootProject.file(it).resolve("nonMinifiedRelease")
    }
    val preservedTargetApkDirectory =
        layout.buildDirectory.dir("intermediates/baseline_profile_target_apk/nonMinifiedRelease")
    val packageNonMinifiedRelease = tasks.matching { it.name == "packageNonMinifiedRelease" }
    packageNonMinifiedRelease.configureEach {
        outputs.upToDateWhen { false }
    }
    val preserveNonMinifiedReleaseTargetApk by tasks.registering {
        dependsOn(packageNonMinifiedRelease)
        outputs.dir(preservedTargetApkDirectory)
        outputs.upToDateWhen { false }
        doLast {
            sync {
                from(injectedNonMinifiedReleaseDirectory)
                into(preservedTargetApkDirectory)
            }
        }
    }
    tasks.matching { it.name == "createNonMinifiedReleaseApkListingFileRedirect" }.configureEach {
        dependsOn(
            preserveNonMinifiedReleaseTargetApk,
            ":baselineprofile:packageNonMinifiedRelease",
        )
        inputs.file(preservedTargetApkDirectory.map { it.file("output-metadata.json") })
        outputs.upToDateWhen { false }
        doLast {
            outputs.files.singleFile.writeText(
                "#- File Locator -\n" +
                    "listingFile=${preservedTargetApkDirectory.get().file("output-metadata.json").asFile.absolutePath}\n"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {

    implementation(project(":shared"))

    // Ktor Client for network operations
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.profileinstaller)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.performance)
    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.media3.exoplayer)
    androidTestImplementation(libs.coil3.compose)
    androidTestImplementation(libs.ktor.client.mock)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    baselineProfile(project(":baselineprofile"))
}
