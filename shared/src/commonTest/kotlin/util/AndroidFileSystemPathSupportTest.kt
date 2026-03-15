package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidFileSystemPathSupportTest {
    private val appDataDirectory = "/data/app/futacha"
    private val privateAppDataDirectory = "/data/user/0/com.valoser.futacha/files/futacha"
    private val publicDocumentsDirectory = "/storage/emulated/0/Documents/futacha"
    private val publicDownloadsDirectory = "/storage/emulated/0/Download/futacha"

    @Test
    fun resolveAndroidAbsolutePath_preserves_absolute_paths() {
        assertEquals(
            "/storage/emulated/0/custom/file.txt",
            resolveAndroidAbsolutePath(
                relativePath = "/storage/emulated/0/custom/file.txt",
                appDataDirectory = appDataDirectory,
                privateAppDataDirectory = privateAppDataDirectory,
                publicDocumentsDirectory = publicDocumentsDirectory,
                publicDownloadsDirectory = publicDownloadsDirectory
            )
        )
    }

    @Test
    fun resolveAndroidAbsolutePath_routes_private_and_auto_save_paths_to_private_root() {
        assertEquals(
            "$privateAppDataDirectory/cache/thread.json",
            resolveAndroidAbsolutePath(
                relativePath = "private/cache/thread.json",
                appDataDirectory = appDataDirectory,
                privateAppDataDirectory = privateAppDataDirectory,
                publicDocumentsDirectory = publicDocumentsDirectory,
                publicDownloadsDirectory = publicDownloadsDirectory
            )
        )
        assertEquals(
            "$privateAppDataDirectory/autosaved_threads/123/index.json",
            resolveAndroidAbsolutePath(
                relativePath = "autosaved_threads/123/index.json",
                appDataDirectory = appDataDirectory,
                privateAppDataDirectory = privateAppDataDirectory,
                publicDocumentsDirectory = publicDocumentsDirectory,
                publicDownloadsDirectory = publicDownloadsDirectory
            )
        )
    }

    @Test
    fun resolveAndroidAbsolutePath_maps_documents_and_download_aliases() {
        assertEquals(
            "$publicDocumentsDirectory/saved_threads",
            resolveAndroidAbsolutePath(
                relativePath = "Documents",
                appDataDirectory = appDataDirectory,
                privateAppDataDirectory = privateAppDataDirectory,
                publicDocumentsDirectory = publicDocumentsDirectory,
                publicDownloadsDirectory = publicDownloadsDirectory
            )
        )
        assertEquals(
            "$publicDownloadsDirectory/work/export",
            resolveAndroidAbsolutePath(
                relativePath = "download/futacha/work/export",
                appDataDirectory = appDataDirectory,
                privateAppDataDirectory = privateAppDataDirectory,
                publicDocumentsDirectory = publicDocumentsDirectory,
                publicDownloadsDirectory = publicDownloadsDirectory
            )
        )
    }

    @Test
    fun resolveAndroidAbsolutePath_falls_back_to_app_data_for_other_relative_paths() {
        assertEquals(
            "$appDataDirectory/boards/custom/index.json",
            resolveAndroidAbsolutePath(
                relativePath = "./boards/custom/index.json",
                appDataDirectory = appDataDirectory,
                privateAppDataDirectory = privateAppDataDirectory,
                publicDocumentsDirectory = publicDocumentsDirectory,
                publicDownloadsDirectory = publicDownloadsDirectory
            )
        )
    }
}
