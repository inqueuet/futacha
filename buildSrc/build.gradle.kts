plugins { java }

repositories {
    google()
    mavenCentral()
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
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
