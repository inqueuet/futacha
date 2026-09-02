import com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.valoser.futacha.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app-android"

    testOptions.managedDevices.localDevices {
        create("futachaBaselineApi35") {
            device = "Pixel 6"
            apiLevel = 35
            systemImageSource = "aosp"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

baselineProfile {
    // Release builds must never replace an app already installed on a USB
    // device. The isolated GMD also makes profile generation reproducible.
    managedDevices += "futachaBaselineApi35"
    useConnectedDevices = false
}

if (providers.gradleProperty("android.injected.apk.location").isPresent) {
    val preservedTargetApk = project(":app-android").layout.buildDirectory.file(
        "intermediates/baseline_profile_target_apk/nonMinifiedRelease/" +
            "app-android-nonMinifiedRelease.apk"
    )
    tasks.matching { it.name == "packageNonMinifiedRelease" }.configureEach {
        // The target is preserved first; always repackage the instrumentation APK
        // afterward because both tasks expose the same Android Studio output path.
        dependsOn(":app-android:preserveNonMinifiedReleaseTargetApk")
        outputs.upToDateWhen { false }
    }
    tasks.withType<ManagedDeviceInstrumentationTestTask>().matching {
        it.name == "futachaBaselineApi35NonMinifiedReleaseAndroidTest"
    }.configureEach {
        dependsOn(":app-android:preserveNonMinifiedReleaseTargetApk")
        buddyApks.from(preservedTargetApk)
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.uiautomator)
}
