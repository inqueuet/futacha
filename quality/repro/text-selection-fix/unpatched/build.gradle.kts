plugins {
 alias(libs.plugins.android.application)
 alias(libs.plugins.kotlin.compose)
}
android {
 namespace = "com.valoser.futacha.selectionbaseline"
 compileSdk = 37
 defaultConfig {
  applicationId = "com.valoser.futacha.selectionbaseline"
  minSdk = 26
  targetSdk = 37
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 }
 buildFeatures { compose = true }
}
dependencies {
 implementation(platform(libs.androidx.compose.bom))
 implementation(libs.kotlinx.coroutines.core)
 implementation(libs.androidx.activity.compose)
 implementation(libs.androidx.compose.material3)
 implementation(libs.androidx.compose.foundation)
 debugImplementation(libs.androidx.compose.ui.test.manifest)
 androidTestImplementation(platform(libs.androidx.compose.bom))
 androidTestImplementation(libs.androidx.compose.ui.test.junit4)
 androidTestImplementation(libs.androidx.junit)
 androidTestImplementation(libs.androidx.espresso.core)
}
