package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals

class IosFileSystemPathSupportTest {
    private val appDataDirectory = "/var/mobile/Containers/Data/Application/app/Documents"
    private val privateAppDataDirectory = "/var/mobile/Containers/Data/Application/app/Library/Application Support/futacha"

    @Test
    fun resolveIosAbsolutePath_preserves_absolute_paths() {
        assertEquals(
            "/var/mobile/custom/file.txt",
            resolveIosAbsolutePath(
                relativePath = "/var/mobile/custom/file.txt",
                appDataDirectory = appDataDirectory,
                privateAppDataDirectory = privateAppDataDirectory
            )
        )
    }

    @Test
    fun resolveIosAbsolutePath_routes_autosave_and_private_paths_to_private_root() {
        assertEquals(
            "$privateAppDataDirectory/autosaved_threads/thread/index.json",
            resolveIosAbsolutePath(
                relativePath = "autosaved_threads/thread/index.json",
                appDataDirectory = appDataDirectory,
                privateAppDataDirectory = privateAppDataDirectory
            )
        )
        assertEquals(
            "$privateAppDataDirectory/cache/state.json",
            resolveIosAbsolutePath(
                relativePath = "private/cache/state.json",
                appDataDirectory = appDataDirectory,
                privateAppDataDirectory = privateAppDataDirectory
            )
        )
    }

    @Test
    fun resolveIosAbsolutePath_uses_documents_root_for_regular_relative_paths() {
        assertEquals(
            "$appDataDirectory/manual_saved_threads/thread.txt",
            resolveIosAbsolutePath(
                relativePath = "manual_saved_threads/thread.txt",
                appDataDirectory = appDataDirectory,
                privateAppDataDirectory = privateAppDataDirectory
            )
        )
    }
}
