import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.native.cocoapods")
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
        it.binaries.framework {
            baseName = "shared"
            isStatic = false
            linkerOpts("-framework", "FileProvider")
        }
    }

    cocoapods {
        summary = "Futacha shared module"
        homepage = "https://github.com/valoser/futacha"
        version = "1.8"
        ios.deploymentTarget = "15.0"
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.material.icons)
                implementation(libs.jetbrains.compose.components.resources)
                implementation(libs.jetbrains.compose.components.ui.tooling.preview)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.coil3.compose)
                implementation(libs.coil3.network.ktor)
                implementation(libs.ktor.client.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.client.mock)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.activity.compose)
                implementation(libs.jetbrains.compose.preview)
                implementation(libs.androidx.media3.exoplayer)
                implementation(libs.androidx.media3.ui)
                implementation(libs.androidx.documentfile)
                implementation(libs.coil3.video)
                implementation(libs.coil3.gif)
                implementation(libs.google.mobile.ads)
                implementation(libs.mlkit.genai.summarization)
                implementation(libs.mlkit.genai.prompt)
            }
        }

        val iosMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
    }
}

dependencies {
    add("androidRuntimeClasspath", libs.jetbrains.compose.ui.tooling)
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
    inputs.file(androidHelpFile)
    inputs.file(iosHelpFile)

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
            listOf(androidHelpFile, iosHelpFile).forEach { file ->
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
