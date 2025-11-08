pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "2.2.21"
        id("org.jetbrains.kotlin.android") version "2.2.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
        id("com.android.library") version "8.12.3"
        id("com.android.application") version "8.12.3"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "futacha"
include(":app-android")
include(":shared")
