package com.valoser.futacha.shared.util

enum class AttachmentPickerPreference {
    MEDIA,
    DOCUMENT,
    ALWAYS_ASK
}

enum class SaveDirectorySelection {
    MANUAL_INPUT,
    PICKER
}

/**
 * 優先するファイラーアプリの情報
 * @property packageName ファイラーアプリのパッケージ名
 * @property label ファイラーアプリの表示名
 */
data class PreferredFileManager(
    val packageName: String,
    val label: String
)

data class ImageData(
    val bytes: ByteArray,
    val fileName: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ImageData

        if (!bytes.contentEquals(other.bytes)) return false
        if (fileName != other.fileName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + fileName.hashCode()
        return result
    }
}

expect suspend fun pickImage(): ImageData?
expect suspend fun pickDirectoryPath(): String?
expect suspend fun pickDirectorySaveLocation(): com.valoser.futacha.shared.model.SaveLocation?
