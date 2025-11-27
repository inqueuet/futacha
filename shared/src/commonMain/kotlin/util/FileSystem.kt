package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation

/**
 * プラットフォーム非依存のファイルシステムインターフェース
 */
interface FileSystem {
    /**
     * ディレクトリを作成
     * @param path ディレクトリパス
     */
    suspend fun createDirectory(path: String): Result<Unit>

    /**
     * ファイルにバイト配列を書き込み
     * @param path ファイルパス
     * @param bytes 書き込むデータ
     */
    suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit>

    /**
     * ファイルにバイト配列を追記
     * @param path ファイルパス
     * @param bytes 追記するデータ
     */
    suspend fun appendBytes(path: String, bytes: ByteArray): Result<Unit>

    /**
     * ファイルに文字列を書き込み
     * @param path ファイルパス
     * @param content 書き込む文字列
     */
    suspend fun writeString(path: String, content: String): Result<Unit>

    /**
     * ファイルからバイト配列を読み込み
     * @param path ファイルパス
     */
    suspend fun readBytes(path: String): Result<ByteArray>

    /**
     * ファイルから文字列を読み込み
     * @param path ファイルパス
     */
    suspend fun readString(path: String): Result<String>

    /**
     * ファイルまたはディレクトリを削除
     * @param path ファイル/ディレクトリパス
     */
    suspend fun delete(path: String): Result<Unit>

    /**
     * ディレクトリを再帰的に削除
     * @param path ディレクトリパス
     */
    suspend fun deleteRecursively(path: String): Result<Unit>

    /**
     * ファイルまたはディレクトリが存在するか確認
     * @param path ファイル/ディレクトリパス
     */
    suspend fun exists(path: String): Boolean

    /**
     * ファイルサイズを取得
     * @param path ファイルパス
     */
    suspend fun getFileSize(path: String): Long

    /**
     * ディレクトリ内のファイル一覧を取得
     * @param directory ディレクトリパス
     */
    suspend fun listFiles(directory: String): List<String>

    /**
     * アプリ専用データディレクトリの絶対パスを取得
     */
    fun getAppDataDirectory(): String

    /**
     * 相対パスを絶対パスに変換
     * @param relativePath 相対パス
     */
    fun resolveAbsolutePath(relativePath: String): String

    // ========================================
    // SaveLocation-based APIs
    // ========================================

    /**
     * ディレクトリを作成 (SaveLocation版)
     * @param base ベース保存先 (Path/TreeUri/Bookmark)
     * @param relativePath ベースからの相対パス (空文字列の場合はベースディレクトリを作成)
     */
    suspend fun createDirectory(base: SaveLocation, relativePath: String = ""): Result<Unit>

    /**
     * ファイルにバイト配列を書き込み (SaveLocation版)
     * @param base ベース保存先 (Path/TreeUri/Bookmark)
     * @param relativePath ベースからの相対パス (ファイル名を含む)
     * @param bytes 書き込むデータ
     */
    suspend fun writeBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit>

    /**
     * ファイルにバイト配列を追記 (SaveLocation版)
     * @param base ベース保存先 (Path/TreeUri/Bookmark)
     * @param relativePath ベースからの相対パス (ファイル名を含む)
     * @param bytes 追記するデータ
     */
    suspend fun appendBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit>

    /**
     * ファイルに文字列を書き込み (SaveLocation版)
     * @param base ベース保存先 (Path/TreeUri/Bookmark)
     * @param relativePath ベースからの相対パス (ファイル名を含む)
     * @param content 書き込む文字列
     */
    suspend fun writeString(base: SaveLocation, relativePath: String, content: String): Result<Unit>

    /**
     * ファイルから文字列を読み込み (SaveLocation版)
     * @param base ベース保存先 (Path/TreeUri/Bookmark)
     * @param relativePath ベースからの相対パス (ファイル名を含む)
     */
    suspend fun readString(base: SaveLocation, relativePath: String): Result<String>

    /**
     * ファイルまたはディレクトリが存在するか確認 (SaveLocation版)
     * @param base ベース保存先 (Path/TreeUri/Bookmark)
     * @param relativePath ベースからの相対パス (空文字列の場合はベース自体を確認)
     */
    suspend fun exists(base: SaveLocation, relativePath: String = ""): Boolean
}

/**
 * プラットフォーム固有のFileSystem実装を取得
 */
expect fun createFileSystem(platformContext: Any? = null): FileSystem
