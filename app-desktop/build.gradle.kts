import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import groovy.json.JsonOutput
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin { jvmToolchain(17) }

val desktopWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
val macAppStore = providers.gradleProperty("futachaMacAppStore").orNull == "true"
require(!macAppStore || System.getProperty("os.name").startsWith("Mac")) { "App Store packaging requires macOS" }
val macStoreConfig = rootProject.layout.projectDirectory.dir(".local-config/macos-store")
val macStoreProperties = Properties().apply {
    val config = macStoreConfig.file("signing.properties").asFile
    if (macAppStore && config.isFile) config.inputStream().use { load(it) }
}
fun macStoreProperty(name: String, fallback: String = ""): String =
    providers.gradleProperty(name).orNull ?: macStoreProperties.getProperty(name, fallback)
val desktopVersion = when {
    macAppStore -> macStoreProperty("futachaMacVersion", "14.6")
    desktopWindows -> "14.6"
    else -> "14.6"
}
val desktopPackageVersion = when {
    macAppStore -> desktopVersion
    desktopWindows -> "14.6.0"
    else -> "14.6.0"
}
val windowsStoreVersion = providers.gradleProperty("futachaWindowsStoreVersion").orElse("14.6.0.0")
val macStoreSigningDir = layout.buildDirectory.dir("macos-store/signing")
val prepareMacStoreSigning = tasks.register<Exec>("prepareMacStoreSigning") {
    onlyIf { macAppStore }
    inputs.file(rootProject.file("tools/prepare-macos-store.py"))
    if (macAppStore) {
        inputs.file(macStoreConfig.file("embedded.provisionprofile"))
    }
    outputs.dir(macStoreSigningDir)
    // Certificate availability and profile expiry can change without file changes.
    outputs.upToDateWhen { false }
    commandLine("python3", rootProject.file("tools/prepare-macos-store.py"),
        "--profiles", macStoreConfig.asFile, "--output", macStoreSigningDir.get().asFile)
}
val resourcePlatform = if (desktopWindows) "windows" else "macos"
val prepareScript = if (desktopWindows) "prepare-windows-resources.py" else "prepare-desktop-resources.py"
val desktopJava = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
val runtimeJars = configurations.runtimeClasspath.map { it.filter { file -> file.extension == "jar" } }
val optimizedRuntimeDir = layout.buildDirectory.dir("optimized-runtime/${if (macAppStore) "macos-app-store" else resourcePlatform}")
val macStoreNativeDir = optimizedRuntimeDir.map { it.dir("native") }
val optimizeDesktopRuntime = tasks.register<Exec>("optimizeDesktopRuntime") {
    dependsOn(configurations.runtimeClasspath)
    inputs.files(runtimeJars)
    inputs.file(rootProject.file("tools/optimize-desktop-runtime.py"))
    inputs.property("platform", resourcePlatform)
    inputs.property("macAppStore", macAppStore)
    outputs.dir(optimizedRuntimeDir)
    val inputList = layout.buildDirectory.file("optimized-runtime-inputs.json")
    doFirst {
        inputList.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.toJson(runtimeJars.get().map { it.absolutePath }), Charsets.UTF_8)
        }
    }
    commandLine(if (desktopWindows) "python" else "python3",
        rootProject.file("tools/optimize-desktop-runtime.py"), "--platform", resourcePlatform,
        "--inputs", inputList.get().asFile, "--output", optimizedRuntimeDir.get().asFile)
    if (macAppStore) args("--mac-native-output", macStoreNativeDir.get().asFile)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.coil3.core)
    testImplementation(libs.jetbrains.compose.material3)
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    source(rootProject.file("shared/src/videoTestFixtures/kotlin/VideoEditFixtures.kt"))
}

tasks.test {
    javaLauncher.set(desktopJava)
    environment("ORT_DISABLE_TELEMETRY", "1")
    systemProperty("java.awt.headless", "false")
    systemProperty("futacha.resourcesDir", layout.projectDirectory.dir("resources/$resourcePlatform").asFile.absolutePath)
}

val prepareDesktopResources = tasks.register<Exec>("prepareDesktopResources") {
    inputs.file(rootProject.file("tools/$prepareScript"))
    inputs.file(rootProject.file("tools/desktop_vlc.py"))
    if (desktopWindows) inputs.file(rootProject.file("tools/create-windows-icon.ps1"))
    inputs.dir(rootProject.file("shared/src/nativeInterop/tracking"))
    inputs.dir(rootProject.file("shared/src/nativeInterop/macos"))
    inputs.dir(rootProject.file("iosApp/iosApp/Assets.xcassets/AppIconClassic.appiconset"))
    inputs.dir(rootProject.file("iosApp/iosApp/Assets.xcassets/AppIconMidnight.appiconset"))
    inputs.file(rootProject.file("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"))
    outputs.dir(layout.projectDirectory.dir("resources/$resourcePlatform/native"))
    outputs.dir(layout.projectDirectory.dir("resources/$resourcePlatform/icons"))
    outputs.dir(layout.projectDirectory.dir("resources/$resourcePlatform/vlc"))
    outputs.file(layout.projectDirectory.file("resources/$resourcePlatform/Futacha.${if (desktopWindows) "ico" else "icns"}"))
    commandLine(if (desktopWindows) "python" else "python3", rootProject.file("tools/$prepareScript"))
}
tasks.matching { it.name in setOf("prepareAppResources", "run", "test") }.configureEach {
    dependsOn(prepareDesktopResources)
}
// Compose's packaging task can otherwise stay up-to-date when only app resources change.
tasks.matching { it.name in setOf("createDistributable", "packageDmg", "packagePkg", "packageMsi", "packageExe",
    "createReleaseDistributable", "packageReleaseDmg", "packageReleasePkg", "packageReleaseMsi", "packageReleaseExe") }.configureEach {
    inputs.dir(layout.projectDirectory.dir("resources"))
    if (macAppStore) dependsOn(prepareMacStoreSigning)
}
if (macAppStore) tasks.matching { it.name in setOf("createDistributable", "createReleaseDistributable") }.configureEach {
    inputs.file(rootProject.file("tools/finalize-macos-store.py"))
    val buildType = if (name == "createReleaseDistributable") "main-release" else "main"
    val appDirectory = layout.buildDirectory.dir("compose/app-store/$buildType/app/Futacha.app")
    doLast {
        val result = providers.exec {
            isIgnoreExitValue = true
            commandLine("python3", rootProject.file("tools/finalize-macos-store.py"),
                "--app", appDirectory.get().asFile,
                "--entitlements", macStoreSigningDir.get().asFile,
                "--identity", macStoreProperty("futachaMacSigningIdentity"))
        }
        result.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }?.let { logger.lifecycle(it) }
        result.standardError.asText.get().trim().takeIf { it.isNotEmpty() }?.let { logger.lifecycle(it) }
        result.result.get().assertNormalExitValue()
    }
}

compose.desktop {
    application {
        disableDefaultConfiguration()
        mainJar.set(tasks.jar.flatMap { it.archiveFile })
        fromFiles(tasks.jar, optimizedRuntimeDir.map { it.asFileTree.matching { include("*.jar") } })
        if (macAppStore) fromFiles(macStoreNativeDir.map { it.asFileTree })
        dependsOn("jar", "optimizeDesktopRuntime")
        if (desktopWindows) javaHome = desktopJava.get().metadata.installationPath.asFile.absolutePath
        mainClass = "com.valoser.futacha.desktop.MainKt"
        jvmArgs += listOf("-Xmx2g", "-Dapple.awt.application.appearance=system", "-Dfutacha.desktop.version=$desktopVersion")
        if (macAppStore) jvmArgs += listOf(
            "-Dfutacha.mac.appStore=true", "-Dfutacha.desktop.bundleId=com.valoser.futacha",
            "-Djava.library.path=\$APPDIR", "-Djna.boot.library.path=\$APPDIR", "-Djna.nounpack=true",
            "-Dorg.sqlite.lib.path=\$APPDIR", "-Donnxruntime.native.path=\$APPDIR",
            "-Dorg.bytedeco.javacpp.pathsFirst=true", "-Dorg.bytedeco.javacpp.cacheLibraries=false",
            "-Dorg.bytedeco.javacpp.canCreateSymbolicLink=false",
        )
        buildTypes.release.proguard {
            configurationFiles.from(layout.projectDirectory.file("proguard-rules.pro"))
            optimize.set(false)
            obfuscate.set(false)
        }
        nativeDistributions {
            targetFormats(if (desktopWindows) TargetFormat.Msi else if (macAppStore) TargetFormat.Pkg else TargetFormat.Dmg)
            if (macAppStore) outputBaseDir.set(layout.buildDirectory.dir("compose/app-store"))
            packageName = "Futacha"
            packageVersion = desktopPackageVersion
            // JDK 17 jpackage reads its argument file using the Windows default charset.
            description = if (desktopWindows) "Futaba Channel Browser" else "ふたば専用ブラウザ"
            vendor = "futacha"
            modules("java.sql", "java.desktop", "java.logging", "java.management", "java.naming", "jdk.charsets", "jdk.crypto.ec", "jdk.unsupported")
            appResourcesRootDir.set(layout.projectDirectory.dir("resources"))
            windows {
                iconFile.set(layout.projectDirectory.file("resources/windows/Futacha.ico"))
                menuGroup = "Futacha"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "42730d90-850f-4d48-9e67-2ab5639d0ef6"
            }
            macOS {
                bundleID = if (macAppStore) "com.valoser.futacha" else "com.valoser.futacha.desktop"
                packageBuildVersion = if (macAppStore) macStoreProperty("futachaMacBuild", "35") else "14.6.0"
                if (macAppStore) {
                    appStore = true
                    appCategory = "public.app-category.social-networking"
                    provisioningProfile.set(macStoreConfig.file("embedded.provisionprofile"))
                    entitlementsFile.set(macStoreSigningDir.map { it.file("app.entitlements") })
                    // Compose also uses this file for standalone jspawnhelper.
                    // finalize-macos-store.py signs the runtime library without entitlements or a profile.
                    runtimeEntitlementsFile.set(macStoreSigningDir.map { it.file("helper.entitlements") })
                    signing {
                        sign.set(true)
                        identity.set(macStoreProperty("futachaMacSigningIdentity").also {
                            require(it.isNotBlank()) { "Set futachaMacSigningIdentity in .local-config/macos-store/signing.properties" }
                        })
                    }
                }
                minimumSystemVersion = "13.0"
                iconFile.set(layout.projectDirectory.file("resources/macos/Futacha.icns"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key><string>投稿欄へ音声で入力するためにマイクを使用します。</string>
                        <key>NSSpeechRecognitionUsageDescription</key><string>話した内容を投稿欄の文章へ変換します。</string>
                    """.trimIndent()
                }
            }
        }
    }
}

// Use only ProGuard's icon class selection. Restore original bytes for retained
// icons and all other JARs, avoiding expensive or invalid Compose frame rewriting.
tasks.matching { it.name == "proguardReleaseJars" }.configureEach {
    inputs.file(rootProject.file("tools/finalize-desktop-shrink.py"))
    doLast {
        providers.exec {
            commandLine(if (desktopWindows) "python" else "python3",
                rootProject.file("tools/finalize-desktop-shrink.py"),
                "--config", layout.buildDirectory.file("compose/tmp/proguardReleaseJars/jars-config.pro").get().asFile,
                "--output", layout.buildDirectory.dir("compose/tmp/main-release/proguard").get().asFile)
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("desktopCheck") {
    group = "verification"
    description = "Verifies desktop persistence, media processing and both Compose profiles."
    dependsOn(":shared:jvmTest", "test", "testDesktopPackaging", "testDesktopResourcesCmake")
}

tasks.register<Exec>("testDesktopPackaging") {
    group = "verification"
    commandLine(if (desktopWindows) "python" else "python3", "-m", "unittest", "discover",
        "-s", rootProject.file("tools/tests"), "-p", "test_desktop_packaging.py", "-v")
}

// K4-6: cmake lookup used by prepareDesktopResources.
tasks.register<Exec>("testDesktopResourcesCmake") {
    group = "verification"
    commandLine(if (desktopWindows) "python" else "python3", "-m", "unittest", "discover",
        "-s", rootProject.file("tools/tests"), "-p", "test_desktop_resources_cmake.py", "-v")
}

if (desktopWindows) {
    tasks.register<Zip>("packageWindowsZip") {
        group = "distribution"
        description = "Packages the Windows application and its bundled runtime as a portable ZIP."
        dependsOn("createReleaseDistributable")
        from(layout.buildDirectory.dir("compose/binaries/main-release/app/Futacha")) { into("Futacha") }
        from(rootProject.file("docs/windows-port-2026-09-20.md")) { into("Futacha") }
        destinationDirectory.set(layout.projectDirectory.dir("release"))
        archiveFileName.set("Futacha-$desktopPackageVersion-windows-x64.zip")
    }

    tasks.register<Exec>("stageWindowsStoreMsix") {
        group = "distribution"
        description = "Stages the Windows x64 application with its Microsoft Store manifest."
        dependsOn("createReleaseDistributable")
        val appImage = layout.buildDirectory.dir("compose/binaries/main-release/app/Futacha")
        val stagingDirectory = layout.buildDirectory.dir("compose/msix/main-release/package")
        inputs.dir(appImage)
        inputs.file(layout.projectDirectory.file("src/msix/AppxManifest.xml.template"))
        inputs.file(rootProject.file("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"))
        inputs.file(rootProject.file("tools/package-windows-msix.ps1"))
        inputs.property("windowsStoreVersion", windowsStoreVersion)
        outputs.dir(stagingDirectory)
        doFirst {
            commandLine(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
                rootProject.file("tools/package-windows-msix.ps1"),
                "-AppImage", appImage.get().asFile,
                "-ManifestTemplate", layout.projectDirectory.file("src/msix/AppxManifest.xml.template").asFile,
                "-SourceIcon", rootProject.file("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"),
                "-StagingDirectory", stagingDirectory.get().asFile,
                "-OutputDirectory", layout.buildDirectory.dir("compose/binaries/main-release/msix").get().asFile,
                "-Version", windowsStoreVersion.get(),
                "-StageOnly",
            )
        }
    }

    tasks.register<Exec>("packageWindowsStoreMsix") {
        group = "distribution"
        description = "Builds the unsigned x64 MSIX for Microsoft Store submission."
        dependsOn("stageWindowsStoreMsix")
        val stagingDirectory = layout.buildDirectory.dir("compose/msix/main-release/package")
        val outputDirectory = layout.buildDirectory.dir("compose/binaries/main-release/msix")
        inputs.dir(stagingDirectory)
        inputs.file(rootProject.file("tools/package-windows-msix.ps1"))
        inputs.property("windowsStoreVersion", windowsStoreVersion)
        outputs.file(windowsStoreVersion.map { outputDirectory.get().file("Futacha-$it-windows-x64.msix") })
        outputs.file(windowsStoreVersion.map { outputDirectory.get().file("Futacha-$it-windows-x64.msix.sha256") })
        doFirst {
            commandLine(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
                rootProject.file("tools/package-windows-msix.ps1"),
                "-AppImage", layout.buildDirectory.dir("compose/binaries/main-release/app/Futacha").get().asFile,
                "-ManifestTemplate", layout.projectDirectory.file("src/msix/AppxManifest.xml.template").asFile,
                "-SourceIcon", rootProject.file("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"),
                "-StagingDirectory", stagingDirectory.get().asFile,
                "-OutputDirectory", outputDirectory.get().asFile,
                "-Version", windowsStoreVersion.get(),
                "-PackageOnly",
            )
        }
    }
}
