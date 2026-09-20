import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Sync
import java.util.Properties
import groovy.json.JsonOutput

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
    ndkVersion = "29.0.14206865"

    externalNativeBuild {
        cmake {
            path = rootProject.file("shared/src/nativeInterop/tracking/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    splits {
        abi {
            // AGP cannot package an AAB with multiple shrunk APK resource sets.
            // Enable CPU-specific APKs explicitly; keep normal bundle builds valid.
            isEnable = providers.gradleProperty("futacha.splitApks").map { it.toBooleanStrict() }.getOrElse(false)
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    defaultConfig {
        applicationId = "com.valoser.futacha"
        minSdk = 26
        targetSdk = 37
        versionCode = 183
        versionName = "11.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["benchmarkFixtureEnabled"] = "false"
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DOPENCV_SOURCE=${rootProject.file("build/android-tracking/opencv").invariantSeparatorsPath}",
                    "-DANDROID_STL=c++_static", "-DWITH_KLEIDICV=OFF", "-DWITH_CAROTENE=OFF"
                )
                targets += "futacha_tracking_bridge"
            }
        }
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
        assets.directories.add(rootProject.layout.buildDirectory.dir("android-tracking/licenses").get().asFile.absolutePath)
    }
    sourceSets.named("androidTest") {
        kotlin.directories.add(rootProject.file("shared/src/videoTestFixtures/kotlin").absolutePath)
        kotlin.directories.add(rootProject.file("shared/src/inferenceTestFixtures/kotlin").absolutePath)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val prepareAndroidTracking = tasks.register<Exec>("prepareAndroidTracking") {
    inputs.file(rootProject.file("tools/prepare-android-tracking.py"))
    outputs.dir(rootProject.layout.buildDirectory.dir("android-tracking/opencv"))
    outputs.dir(rootProject.layout.buildDirectory.dir("android-tracking/licenses"))
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        rootProject.file("tools/prepare-android-tracking.py"))
}
val testNativeSymbolPackaging = tasks.register<Exec>("testNativeSymbolPackaging") {
    group = "verification"
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        "-m", "unittest", "discover", "-s", rootProject.file("tools/tests"), "-p", "test_android_symbols.py", "-v")
}
tasks.matching { it.name == "testDebugUnitTest" }.configureEach { dependsOn(testNativeSymbolPackaging) }
tasks.matching { it.name.startsWith("configureCMake") || it.name == "preBuild" }.configureEach {
    dependsOn(prepareAndroidTracking)
}

// AGP's SYMBOL_TABLE extraction also copies stripped third-party binaries.
// Remove only copies without private/debug symbols, keeping a complete archive.
// Finalize the producer's own outputs so Gradle tracks the actual packaged data.
tasks.matching { it.name == "extractReleaseNativeSymbolTables" }.configureEach {
    val optimizer = rootProject.file("tools/optimize-android-symbols.py")
    val archive = layout.buildDirectory.file("outputs/native-symbols/release/all-native-symbols.zip")
    inputs.file(optimizer)
    outputs.file(archive)
    doLast {
        val directories = outputs.files.files.filter { it.isDirectory }
        check(directories.size == 1) { "Expected one native symbol directory: $directories" }
        val inputList = temporaryDir.resolve("symbol-inputs.json")
        inputList.writeText(JsonOutput.toJson(directories.map { it.absolutePath }), Charsets.UTF_8)
        val result = providers.exec {
            commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
                optimizer, "--inputs", inputList, "--output", directories.single(),
                "--archive", archive.get().asFile)
        }
        logger.lifecycle(result.standardOutput.asText.get().trim())
        result.result.get().assertNormalExitValue()
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.instrumentation.transformClassesWith(
            com.valoser.futacha.instrumentation.ComposeSelectionGuardFactory::class.java,
            com.android.build.api.instrumentation.InstrumentationScope.ALL
        ) {}
        variant.instrumentation.setAsmFramesComputationMode(
            com.android.build.api.instrumentation.FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
        )
        if (variant.name == "nonMinifiedRelease" || variant.name == "benchmarkRelease") {
            variant.manifestPlaceholders.put("benchmarkFixtureEnabled", "true")
        }
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(syncSharedComposeResourcesForAndroid, prepareAndroidTracking)
}

// Lint model and analysis tasks read the asset source set directly instead of
// going through merge*Assets, so every Debug/Release/Vital lint task needs the producer.
tasks.matching {
    it.name.contains("lint", ignoreCase = true)
}.configureEach {
    dependsOn(syncSharedComposeResourcesForAndroid, prepareAndroidTracking)
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

    constraints {
        implementation(libs.androidx.compose.foundation.guarded) {
            because("ComposeSelectionGuardFactory patches two verified 1.13.0-alpha02 call sites; review before upgrading")
        }
    }

    implementation(project(":shared"))

    // Ktor Client for network operations
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.http)
    implementation(libs.ktor.io)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.unit)
    implementation(libs.androidx.compose.ui.geometry)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.play.services.tasks)
    implementation(libs.google.play.app.update)
    implementation(libs.google.play.app.update.ktx)
    implementation(libs.androidx.profileinstaller)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.performance)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.navigationevent)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.navigationevent)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.monitor)
    androidTestImplementation(libs.androidx.exifinterface)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test)
    androidTestImplementation(libs.androidx.media3.exoplayer)
    androidTestImplementation(libs.androidx.media3.common)
    androidTestImplementation(libs.androidx.media3.datasource)
    androidTestImplementation(libs.coil3.compose)
    androidTestImplementation(libs.coil3.core)
    androidTestImplementation(libs.coil3.network.ktor)
    androidTestImplementation(libs.ktor.client.mock)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    baselineProfile(project(":baselineprofile"))
}
