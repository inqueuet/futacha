plugins { java }

repositories {
    google()
    mavenCentral()
}

// The exact Foundation Android AAR the selection guard patches, so the unit test runs the
// real factory over the real pinned bytecode instead of a hand-written imitation.
val guardedFoundationAar: Configuration = configurations.create("guardedFoundationAar") {
    isTransitive = false
    isCanBeConsumed = false
}

dependencies {
    compileOnly(gradleApi())
    implementation(libs.android.gradle.api) {
        // The application applies AGP itself. Keep its plugin implementation off
        // buildSrc's parent classpath while retaining the public API dependencies.
        exclude(group = "com.android.tools.build", module = "gradle")
    }
    implementation(libs.kotlin.gradle.plugin.api)
    implementation(libs.asm)

    testImplementation(gradleApi())
    testImplementation(libs.junit)
    add(
        guardedFoundationAar.name,
        "androidx.compose.foundation:foundation-android:" +
            libs.androidx.compose.foundation.guarded.get().versionConstraint.strictVersion + "@aar"
    )
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.test {
    val aar: FileCollection = guardedFoundationAar
    inputs.files(aar)
    val repositoryRoot = rootDir.parentFile
    inputs.file(repositoryRoot.resolve("app-android/src/main/java/com/valoser/futacha/text/SafeTextClassification.kt"))
    inputs.file(repositoryRoot.resolve("app-wear/src/main/java/com/valoser/futacha/text/SafeTextClassification.kt"))
    systemProperty("futacha.repositoryRoot", repositoryRoot.absolutePath)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dfutacha.guardedFoundationAar=" + aar.singleFile.absolutePath)
    })
}
