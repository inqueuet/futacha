import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

val desktopWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
val desktopNativeClassifier = if (desktopWindows) "windows-x86_64" else "macosx-arm64"
val desktopResourcePlatform = if (desktopWindows) "windows" else "macos"

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.cocoapods)
}

kotlin {
    applyDefaultHierarchyTemplate()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.valoser.futacha.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {
            // commonTest is shared with the Android local test target. Android's
            // stub Log methods otherwise throw instead of returning defaults.
            isReturnDefaultValues = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        val targetName = it.name
        val trackingOutput = layout.buildDirectory.dir("nativeTracking/$targetName")
        val buildTracking = tasks.register<Exec>("buildTracking${targetName.replaceFirstChar { name -> name.uppercaseChar() }}") {
            inputs.dir("src/nativeInterop/tracking")
            inputs.file(rootProject.file("tools/build-ios-tracking.py"))
            inputs.property("sdk", providers.exec {
                commandLine("xcrun", "--sdk", if (targetName == "iosArm64") "iphoneos" else "iphonesimulator", "--show-sdk-build-version")
            }.standardOutput.asText)
            outputs.file(trackingOutput.map { output -> output.file("libfutacha_tracking.a") })
            commandLine("/usr/bin/python3", rootProject.file("tools/build-ios-tracking.py"), targetName)
        }
        val trackingInterop = it.compilations.getByName("main").cinterops.create("tracking") {
            defFile(project.file("src/nativeInterop/cinterop/tracking.def"))
            includeDirs(project.file("src/nativeInterop/tracking"))
            extraOpts("-libraryPath", trackingOutput.get().asFile.absolutePath,
                "-staticLibrary", "libfutacha_tracking.a")
        }
        tasks.named(trackingInterop.interopProcessingTaskName).configure { dependsOn(buildTracking) }
        it.compilations.getByName("main").cinterops.create("sqlite3") {
            defFile(project.file("src/nativeInterop/cinterop/sqlite3.def"))
        }
        it.compilerOptions {
            freeCompilerArgs.add("-Xklib-duplicated-unique-name-strategy=allow-first-with-warning")
        }
        it.binaries.all {
            // ORT's static XCFramework references Network even with the CPU provider.
            // Native test executables do not inherit CocoaPods' Xcode linker flags.
            linkerOpts("-framework", "Network", "-lc++", "-lz")
            if (buildType == NativeBuildType.RELEASE) {
                binaryOption("smallBinary", "true")
            }
        }
    }

    cocoapods {
        summary = "Futacha shared module"
        homepage = "https://github.com/valoser/futacha"
        version = "1.8"
        podfile = project.file("../iosApp/Podfile")
        // The app already targets iOS 18.2; the CPU inference pod requires 15.1.
        ios.deploymentTarget = "15.1"
        // Use the same framework for command-line builds and Xcode. CocoaPods
        // wires its libraries into pod frameworks and test executables only.
        framework {
            baseName = "shared"
            isStatic = false
            linkerOpts("-framework", "FileProvider", "-framework", "StoreKit", "-lsqlite3")
        }
        pod("onnxruntime-c") {
            version = libs.versions.onnxRuntime.get()
            packageName = "com.valoser.futacha.shared.ort"
            moduleName = "onnxruntime"
            headers = "onnxruntime/onnxruntime_c_api.h"
        }
    }

    sourceSets {
        // Identical small synthetic videos for native AVFoundation and Android codec tests.
        val videoTestFixtures = "src/videoTestFixtures/kotlin"
        val commonMain by getting {
            dependencies {
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.runtime.saveable)
                implementation(libs.androidx.lifecycle.common)
                implementation(libs.androidx.lifecycle.runtime.compose)
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.ui.graphics)
                implementation(libs.jetbrains.compose.ui.text)
                implementation(libs.jetbrains.compose.ui.unit)
                implementation(libs.jetbrains.compose.ui.geometry)
                implementation(libs.jetbrains.compose.animation)
                implementation(libs.jetbrains.compose.animation.core)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.material.icons)
                implementation(libs.jetbrains.compose.components.resources)
                implementation(libs.jetbrains.compose.components.ui.tooling.preview)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.coil3.compose)
                implementation(libs.coil3.core)
                implementation(libs.coil3.compose.core)
                implementation(libs.coil3.network.core)
                implementation(libs.okio)
                implementation(libs.coil3.network.ktor)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.http)
                implementation(libs.ktor.io)
                implementation(libs.ktor.utils)
            }
        }
        val commonTest by getting {
            kotlin.srcDir(videoTestFixtures)
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.io.core)
                implementation(libs.ktor.client.mock)
            }
        }
        val androidMain by getting {
            kotlin.srcDir("src/jvmAndAndroidMain/kotlin")
            dependencies {
                implementation(libs.onnxruntime.android)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.androidx.core.ktx)
                implementation(libs.okhttp)
                implementation(libs.guava)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.activity.compose)
                // Fix delayed Android back-animation callbacks after input removal/disposal.
                // Navigation Event's atomic-group constraints also align its Compose artifact.
                implementation(libs.androidx.navigationevent)
                implementation(libs.jetbrains.compose.preview)
                implementation(libs.androidx.media3.exoplayer)
                implementation(libs.androidx.media3.common)
                implementation(libs.androidx.media3.effect)
                implementation(libs.androidx.media3.transformer)
                implementation(libs.androidx.media3.database)
                implementation(libs.androidx.media3.datasource)
                implementation(libs.androidx.media3.ui)
                implementation(libs.androidx.documentfile)
                implementation(libs.coil3.video)
                implementation(libs.coil3.gif)
                // Coil/ImageDecoder does not decode APNG. The compatibility mode
                // must keep the sample client's APNG support on API 26+.
                implementation(libs.penfeizhou.animation.apng)
                // Android 26/27 do not have ImageDecoder's animated-WebP
                // support; the reference client decodes those frames too.
                implementation(libs.penfeizhou.animation.awebp)
                implementation(libs.penfeizhou.animation.core)
                implementation(project.dependencies.platform(libs.firebase.bom))
                implementation(libs.firebase.analytics)
                implementation(libs.firebase.common)
                implementation(libs.firebase.performance)
                implementation(libs.firebase.crashlytics)
                implementation(libs.mlkit.genai.summarization)
                implementation(libs.mlkit.genai.prompt)
                implementation(libs.mlkit.common)
                implementation(libs.mlkit.genai.common)
                implementation(libs.play.services.tasks)
                implementation(libs.google.play.billing)
            }
        }

        val iosMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        val jvmMain by getting {
            kotlin.srcDir("src/jvmAndAndroidMain/kotlin")
            dependencies {
                implementation(libs.onnxruntime.jvm)
                implementation(libs.androidx.datastore.preferences)
                implementation("org.xerial:sqlite-jdbc:3.50.3.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${libs.versions.kotlinxCoroutines.get()}")
                implementation(compose.desktop.currentOs)
                implementation("uk.co.caprica:vlcj:4.12.1")
                implementation("org.bytedeco:javacv:1.5.12") { isTransitive = false }
                implementation("org.bytedeco:ffmpeg:7.1.1-1.5.12")
                runtimeOnly("org.bytedeco:ffmpeg:7.1.1-1.5.12:$desktopNativeClassifier")
                runtimeOnly("org.bytedeco:javacpp:1.5.12:$desktopNativeClassifier")
                implementation(libs.ktor.client.okhttp)
                implementation(libs.okhttp)
            }
        }
        val jvmTest by getting {
            kotlin.srcDir("src/inferenceTestFixtures/kotlin")
            kotlin.srcDir("src/inferenceRuntimeTest/kotlin")
            dependencies {
                // Exercise the same Skia decode/encode path as iOS in codec tests.
                runtimeOnly(compose.desktop.currentOs)
            }
        }
        val iosTest by getting {
            kotlin.srcDir("src/inferenceTestFixtures/kotlin")
            kotlin.srcDir("src/inferenceRuntimeTest/kotlin")
        }
    }
}

// Keep the existing CLI entry points and artifact locations, without producing
// a second framework that omits native Pod dependencies such as ONNX Runtime.
for (target in listOf("iosArm64", "iosSimulatorArm64")) {
    tasks.matching { it.name == "linkPodReleaseFramework${target.replaceFirstChar { it.uppercaseChar() }}" }.configureEach {
        inputs.property("stripReleaseLocalSymbols", true)
        doLast {
            // Preserve exported APIs and the separate dSYM/UUID; Xcode signs later.
            val binary = layout.buildDirectory.file("bin/$target/podReleaseFramework/shared.framework/shared").get().asFile
            providers.exec { commandLine("xcrun", "strip", "-S", "-x", binary) }.result.get().assertNormalExitValue()
        }
    }
    for (variant in listOf("Debug", "Release")) {
        tasks.register<Sync>("link${variant}Framework${target.replaceFirstChar { it.uppercaseChar() }}") {
            group = "build"
            description = "Builds and copies the CocoaPods $variant framework for $target."
            dependsOn("linkPod${variant}Framework${target.replaceFirstChar { it.uppercaseChar() }}")
            from(layout.buildDirectory.dir("bin/$target/pod${variant}Framework"))
            into(layout.buildDirectory.dir("bin/$target/${variant.lowercase()}Framework"))
        }
    }
}

dependencies {
    add("androidRuntimeClasspath", libs.jetbrains.compose.ui.tooling)
}

val prepareComposeCocoaPodsResourceDirectory by tasks.registering {
    val output = layout.buildDirectory.dir("compose/cocoapods/compose-resources")
    outputs.dir(output)
    doLast {
        // CocoaPods ignores a resource path that does not exist while it
        // generates Pods.xcodeproj. The Compose sync task fills this directory
        // later from the active iOS architecture during the Xcode build.
        output.get().asFile.mkdirs()
    }
}

tasks.named("podInstall").configure {
    dependsOn(prepareComposeCocoaPodsResourceDirectory)
}

val validateAiActionCatalog by tasks.registering {
    val commonCommandFile = layout.projectDirectory.file("src/commonMain/kotlin/ai/FutachaAiCommand.kt")
    val androidFunctionsFile = rootProject.layout.projectDirectory.file("app-android/src/main/assets/futacha_app_functions.xml")
    val androidAppFunctionServiceFile = rootProject.layout.projectDirectory.file("app-android/src/main/java/com/valoser/futacha/FutachaAppFunctionService.kt")
    val androidManifestFile = rootProject.layout.projectDirectory.file("app-android/src/main/AndroidManifest.xml")
    val iosAppFile = rootProject.layout.projectDirectory.file("iosApp/iosApp/iOSApp.swift")
    val iosInfoPlistFile = rootProject.layout.projectDirectory.file("iosApp/iosApp/Info.plist")
    val androidHelpFile = rootProject.layout.projectDirectory.file("docs/help/help-android.html")
    val iosHelpFile = rootProject.layout.projectDirectory.file("docs/help/help-ios.html")

    inputs.file(commonCommandFile)
    inputs.file(androidFunctionsFile)
    inputs.file(androidAppFunctionServiceFile)
    inputs.file(androidManifestFile)
    inputs.file(iosAppFile)
    inputs.file(iosInfoPlistFile)
    if (androidHelpFile.asFile.isFile) inputs.file(androidHelpFile)
    if (iosHelpFile.asFile.isFile) inputs.file(iosHelpFile)

    doLast {
        fun parseCommonIds(): List<String> {
            return Regex("""^\s*\w+\("([^"]+)",""", RegexOption.MULTILINE)
                .findAll(commonCommandFile.asFile.readText())
                .map { it.groupValues[1] }
                .toList()
        }

        fun parseCommonConfirmIds(): List<String> {
            return Regex("""^\s*\w+\("([^"]+)",\s*"[^"]+",\s*FutachaAiCommandRisk\.Confirm\)""", RegexOption.MULTILINE)
                .findAll(commonCommandFile.asFile.readText())
                .map { it.groupValues[1] }
                .toList()
        }

        fun parseAndroidIds(): List<String> {
            return Regex("""<id>([^<]+)</id>""")
                .findAll(androidFunctionsFile.asFile.readText())
                .map { it.groupValues[1] }
                .toList()
        }

        fun parseAndroidFunctionBlocks(): List<String> {
            return Regex("""<appfunction>(.*?)</appfunction>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(androidFunctionsFile.asFile.readText())
                .map { it.groupValues[1] }
                .toList()
        }

        fun requireAndroidFunctionSchemaNamesMatchIds() {
            parseAndroidFunctionBlocks().forEachIndexed { index, block ->
                val id = Regex("""<id>([^<]+)</id>""")
                    .find(block)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: error("Missing Android AppFunction id at index $index")
                val schemaName = Regex("""<schemaName>([^<]+)</schemaName>""")
                    .find(block)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: error("Missing Android AppFunction schemaName for $id")
                require(schemaName == id) {
                    "Android AppFunction schemaName must match id for $id, found $schemaName"
                }
            }
        }

        fun requireAndroidAppFunctionServiceDoesNotEchoDeepLinks() {
            val source = androidAppFunctionServiceFile.asFile.readText()
            require("FutachaAiCommandBridge.enqueue" in source) {
                "Android AppFunctionService must enqueue AI commands through FutachaAiCommandBridge"
            }
            require("""setPropertyString("deepLink"""" !in source) {
                "Android AppFunctionService must not echo deep links in AppFunction results"
            }
            require("getPropertyLongArray" in source && "getPropertyDoubleArray" in source && "getPropertyBooleanArray" in source) {
                "Android AppFunctionService must accept scalar string, number, and boolean parameters"
            }
        }

        fun requireAndroidManifestDeclaresAiEntrypoints() {
            val source = androidManifestFile.asFile.readText()
            require("""android:name=".FutachaAppFunctionService"""" in source) {
                "Android manifest must declare FutachaAppFunctionService"
            }
            require("""android.permission.BIND_APP_FUNCTION_SERVICE""" in source) {
                "Android AppFunctionService must require BIND_APP_FUNCTION_SERVICE"
            }
            require("""android:name="android.app.appfunctions"""" in source &&
                """android:value="futacha_app_functions.xml"""" in source
            ) {
                "Android manifest must link AppFunction service to futacha_app_functions.xml"
            }
            require("""android.app.appfunctions.AppFunctionService""" in source) {
                "Android AppFunctionService intent action is missing"
            }
            require("""android:scheme="futacha"""" in source && """android:host="ai"""" in source) {
                "Android manifest must declare futacha://ai deep link entrypoint"
            }
        }

        fun parseIosIds(): List<String> {
            return Regex("""case\s+\w+\s*=\s*"([^"]+)"""")
                .findAll(iosAppFile.asFile.readText())
                .map { it.groupValues[1] }
                .toList()
        }

        fun parseIosCaseNames(): List<String> {
            return Regex("""case\s+(\w+)\s*=\s*"[^"]+"""")
                .findAll(iosAppFile.asFile.readText())
                .map { it.groupValues[1] }
                .toList()
        }

        fun parseIosDisplayCaseNames(): List<String> {
            val source = iosAppFile.asFile.readText()
            val block = Regex("""caseDisplayRepresentations:[^\n]+=\s*\[([^\]]*)\]""", RegexOption.DOT_MATCHES_ALL)
                .find(source)
                ?.groupValues
                ?.getOrNull(1)
                ?: error("Could not find iOS caseDisplayRepresentations in iOS app")
            return Regex("""\.(\w+)\s*:""")
                .findAll(block)
                .map { it.groupValues[1] }
                .toList()
        }

        fun parseIosConfirmIds(): List<String> {
            val source = iosAppFile.asFile.readText()
            val block = Regex("""futachaConfirmActionIds:[^\[]+\[([^\]]*)\]""", RegexOption.DOT_MATCHES_ALL)
                .find(source)
                ?.groupValues
                ?.getOrNull(1)
                ?: error("Could not find futachaConfirmActionIds in iOS app")
            return Regex(""""([^"]+)"""")
                .findAll(block)
                .map { it.groupValues[1] }
                .toList()
        }

        fun parseIosConfirmationReasonIds(): List<String> {
            val source = iosAppFile.asFile.readText()
            val block = Regex(
                """private\s+func\s+futachaConfirmationReason\(action:\s*String\).*?switch\s+action\s*\{(.*?)default:""",
                RegexOption.DOT_MATCHES_ALL
            )
                .find(source)
                ?.groupValues
                ?.getOrNull(1)
                ?: error("Could not find iOS futachaConfirmationReason switch in iOS app")
            return Regex("""case\s+([^:]+):""")
                .findAll(block)
                .flatMap { caseMatch ->
                    Regex(""""([^"]+)"""")
                        .findAll(caseMatch.groupValues[1])
                        .map { it.groupValues[1] }
                }
                .toList()
        }

        fun requireIosAppIntentsDoNotSerializeSensitiveParametersInUrls() {
            val source = iosAppFile.asFile.readText()
            require("enqueueIntentCommand" in source) {
                "iOS App Intents must enqueue commands directly through FutachaAiCommandBridge.enqueueIntentCommand"
            }
            require("buildFutachaAiIntentUrl" !in source) {
                "iOS App Intents must not build AI command URLs for intent parameters"
            }
            require("""URLQueryItem(name: "comment"""" !in source) {
                "iOS App Intents must not serialize draft comments into URL query items"
            }
            require("""URLQueryItem(name: "password"""" !in source) {
                "iOS App Intents must not serialize delete keys into URL query items"
            }
            require("""@Parameter(title: "URL", default: "")""" in source) {
                "iOS App Intents must expose a URL parameter for URL-based Futacha actions"
            }
            require("""submitFutachaAiIntentCommand(action: actionId, board: board, thread: thread, url: url)""" in source) {
                "OpenFutachaIntent must forward its URL parameter to FutachaAiCommandBridge"
            }
        }

        fun requireIosInfoPlistDeclaresAiEntrypoints() {
            val source = iosInfoPlistFile.asFile.readText()
            require("<key>CFBundleURLSchemes</key>" in source && "<string>futacha</string>" in source) {
                "iOS Info.plist must declare futacha URL scheme for AI deep links"
            }
        }

        fun requireHelpDoesNotExposeInternalMvpCounts() {
            val forbidden = Regex("""(?:50|10)\s*(?:件|つ|個)""")
            val privateHelpFiles = listOf(androidHelpFile, iosHelpFile).filter { it.asFile.isFile }
            require(privateHelpFiles.isEmpty() || privateHelpFiles.size == 2) {
                "Private Android and iOS help files must either both exist or both be absent"
            }
            privateHelpFiles.forEach { file ->
                val match = forbidden.find(file.asFile.readText())
                require(match == null) {
                    "Help must not expose internal AI MVP counts in ${file.asFile.name}: ${match?.value}"
                }
            }
        }

        fun requireSameCatalog(name: String, expected: List<String>, actual: List<String>) {
            require(actual == expected) {
                val missing = expected.toSet() - actual.toSet()
                val extra = actual.toSet() - expected.toSet()
                buildString {
                    append("AI action catalog mismatch in ")
                    append(name)
                    append(". expected=")
                    append(expected.size)
                    append(", actual=")
                    append(actual.size)
                    if (missing.isNotEmpty()) append(", missing=").append(missing.sorted())
                    if (extra.isNotEmpty()) append(", extra=").append(extra.sorted())
                    if (missing.isEmpty() && extra.isEmpty()) {
                        append(", action ids are the same but order differs")
                    }
                }
            }
        }

        fun requireSameSet(name: String, expected: List<String>, actual: List<String>) {
            val missing = expected.toSet() - actual.toSet()
            val extra = actual.toSet() - expected.toSet()
            require(missing.isEmpty() && extra.isEmpty()) {
                buildString {
                    append("AI action catalog mismatch in ")
                    append(name)
                    append(". expected=")
                    append(expected.size)
                    append(", actual=")
                    append(actual.size)
                    if (missing.isNotEmpty()) append(", missing=").append(missing.sorted())
                    if (extra.isNotEmpty()) append(", extra=").append(extra.sorted())
                }
            }
        }

        val commonIds = parseCommonIds()
        require(commonIds.size >= 50) {
            "Expected at least 50 Futacha AI actions in commonMain, found ${commonIds.size}"
        }
        requireSameCatalog("Android AppFunctions XML", commonIds, parseAndroidIds())
        requireAndroidFunctionSchemaNamesMatchIds()
        requireAndroidAppFunctionServiceDoesNotEchoDeepLinks()
        requireAndroidManifestDeclaresAiEntrypoints()
        requireSameCatalog("iOS AppEnum", commonIds, parseIosIds())
        requireSameCatalog("iOS AppEnum display names", parseIosCaseNames(), parseIosDisplayCaseNames())
        requireSameCatalog("iOS confirmation action set", parseCommonConfirmIds(), parseIosConfirmIds())
        requireSameSet("iOS confirmation reason switch", parseCommonConfirmIds(), parseIosConfirmationReasonIds())
        requireIosAppIntentsDoNotSerializeSensitiveParametersInUrls()
        requireIosInfoPlistDeclaresAiEntrypoints()
        requireHelpDoesNotExposeInternalMvpCounts()
    }
}

tasks.named("check") {
    dependsOn(validateAiActionCatalog)
}

// Core Image/VideoToolbox export tests require the simulator's graphics services.
// simctl's standalone launcher omits them; use a fully booted simulator for native tests.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    standalone.set(false)
    providers.gradleProperty("futacha.iosTestDevice").orNull?.let { device.set(it) }
    doFirst {
        val target = device.get()
        val boot = ProcessBuilder("/usr/bin/xcrun", "simctl", "boot", target).redirectErrorStream(true).start()
        boot.inputStream.bufferedReader().readText() // Already booted is harmless; bootstatus validates the device.
        boot.waitFor()
        val ready = ProcessBuilder("/usr/bin/xcrun", "simctl", "bootstatus", target, "-b").inheritIO().start()
        check(ready.waitFor() == 0) { "Could not boot the iOS test simulator: $target" }
    }
}

// Desktop integration tests load the same native resources as the packaged application.
tasks.named<Test>("jvmTest") {
    if (desktopWindows) javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    environment("ORT_DISABLE_TELEMETRY", "1")
    dependsOn(":app-desktop:prepareDesktopResources")
    systemProperty("futacha.resourcesDir", rootProject.file("app-desktop/resources/$desktopResourcePlatform").absolutePath)
}
