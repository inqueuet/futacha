package com.valoser.futacha.shared.model

import kotlinx.serialization.Serializable

/**
 * 保存済みスレッド情報
 */
@Serializable
data class SavedThread(
    val threadId: String,
    val boardId: String,
    val boardName: String,
    val title: String,
    val thumbnailPath: String?,        // OPのサムネイル（相対パス）
    val savedAt: Long,                 // 保存日時（Epoch millis）
    val postCount: Int,                // 投稿数
    val imageCount: Int,               // 画像数
    val videoCount: Int,               // 動画数
    val totalSize: Long,               // 合計ファイルサイズ（bytes）
    val status: SaveStatus             // 保存状態
)

/**
 * 保存状態
 */
@Serializable
enum class SaveStatus {
    DOWNLOADING,    // ダウンロード中
    COMPLETED,      // 完了
    FAILED,         // 失敗
    PARTIAL         // 一部失敗（HTMLと一部メディアのみ）
}

/**
 * 保存済みスレッドのメタデータ（metadata.json）
 */
@Serializable
data class SavedThreadMetadata(
    val threadId: String,
    val boardId: String,
    val boardName: String,
    val boardUrl: String,
    val title: String,
    val savedAt: Long,
    val expiresAtLabel: String?,
    val posts: List<SavedPost>,
    val totalSize: Long,
    val rawHtmlPath: String? = null,   // 取得した元HTMLのローカルパス
    val strippedExternalResources: Boolean = false, // 外部JS/広告を除去したかどうか
    val version: Int = 1              // メタデータバージョン（将来の互換性）
)

/**
 * 保存済み投稿
 */
@Serializable
data class SavedPost(
    val id: String,
    val order: Int?,
    val author: String?,
    val subject: String?,
    val timestamp: String,
    val messageHtml: String,           // ローカルパス変換済み
    val originalImageUrl: String?,     // 元のURL（参照用）
    val localImagePath: String?,       // ローカルパス（相対）
    val originalVideoUrl: String?,     // 動画の元URL（参照用）
    val localVideoPath: String?,       // 動画のローカルパス（相対）
    val originalThumbnailUrl: String?, // 元のURL（参照用）
    val localThumbnailPath: String?,   // ローカルパス（相対）
    val downloadSuccess: Boolean = true // ダウンロード成功フラグ
)

/**
 * 保存済みスレッドのインデックス（index.json）
 */
@Serializable
data class SavedThreadIndex(
    val threads: List<SavedThread>,
    val totalSize: Long,               // 全体の合計サイズ
    val lastUpdated: Long
)

/**
 * 保存進捗情報
 */
data class SaveProgress(
    val phase: SavePhase,
    val current: Int,
    val total: Int,
    val currentItem: String
) {
    companion object {
        // フェーズごとの重み（合計100%）
        private const val WEIGHT_PREPARING = 1.0    // 1%
        private const val WEIGHT_DOWNLOADING = 97.0 // 97%
        private const val WEIGHT_CONVERTING = 1.0   // 1%
        private const val WEIGHT_FINALIZING = 1.0   // 1%
    }

    /**
     * 全体進捗をパーセンテージで取得（0-100）
     */
    fun getOverallProgressPercentage(): Int {
        val phaseProgress = if (total > 0) {
            current.toFloat() / total.toFloat()
        } else {
            0f
        }

        val overallProgress = when (phase) {
            SavePhase.PREPARING -> {
                WEIGHT_PREPARING * phaseProgress
            }
            SavePhase.DOWNLOADING -> {
                WEIGHT_PREPARING + (WEIGHT_DOWNLOADING * phaseProgress)
            }
            SavePhase.CONVERTING -> {
                WEIGHT_PREPARING + WEIGHT_DOWNLOADING + (WEIGHT_CONVERTING * phaseProgress)
            }
            SavePhase.FINALIZING -> {
                WEIGHT_PREPARING + WEIGHT_DOWNLOADING + WEIGHT_CONVERTING + (WEIGHT_FINALIZING * phaseProgress)
            }
        }

        return overallProgress.toInt().coerceIn(0, 100)
    }
}

/**
 * 保存フェーズ
 */
enum class SavePhase {
    PREPARING,       // 準備中
    DOWNLOADING,     // ダウンロード中
    CONVERTING,      // HTML変換中
    FINALIZING       // 完了処理中
}

/**
 * ローカルファイル情報
 */
data class LocalFileInfo(
    val relativePath: String,  // "images/thumb_1_abc123.jpg"
    val fileType: FileType
)

/**
 * ファイルタイプ
 */
enum class FileType {
    THUMBNAIL,
    FULL_IMAGE,
    VIDEO
}
