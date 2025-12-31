package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation

/**
 * プラットフォーム非依存のファイルシステムインターフェース
 *
 * FIX: Result型の適切な使用について
 * - すべてのI/O操作はResult<T>を返します
 * - 呼び出し元は必ず.getOrThrow()、.getOrElse()、.onFailure()などで結果を処理してください
 * - Result型を無視すると、エラーが見逃され、データ破損の原因となります
 * - 例: fileSystem.writeBytes(path, data).getOrThrow() // 正しい
 * - 例: fileSystem.writeBytes(path, data) // 間違い - 結果を無視している
 *
 * ## 安全性チェックのガイドライン：
 *
 * ### 1. 入力検証
 * - パスは空文字列でないことを確認
 * - パスにnull文字(\u0000)が含まれていないか確認
 * - パストラバーサル攻撃（../）を防ぐ
 * - ファイル名の長さ制限（255文字以下）
 *
 * ### 2. サイズ制限
 * - ファイルサイズの上限チェック（MAX_FILE_SIZE）
 * - ディレクトリの合計サイズチェック
 * - 整数オーバーフロー防止（Long.MAX_VALUE超過）
 *
 * ### 3. エラーハンドリング
 * - 権限エラー（PermissionRevokedException）の適切な処理
 * - ディスク容量不足の検出と対応
 * - I/O例外の詳細なログ記録
 *
 * ### 4. リソース管理
 * - 一時ファイルの確実なクリーンアップ
 * - ファイルハンドルのリーク防止
 * - メモリマップドファイルの適切な解放
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
     *
     * FIX: パフォーマンス注意事項
     * - この操作はI/Oスレッドで実行されますが、頻繁な呼び出しはパフォーマンスに影響します
     * - 可能な限り、複数のファイルチェックをバッチ処理してください
     * - ループ内での連続呼び出しは避けてください
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

    /**
     * ファイルまたはディレクトリを削除 (SaveLocation版)
     * @param base ベース保存先 (Path/TreeUri/Bookmark)
     * @param relativePath ベースからの相対パス (空文字列の場合はベース自体を削除)
     */
    suspend fun delete(base: SaveLocation, relativePath: String = ""): Result<Unit>
}

/**
 * プラットフォーム固有のFileSystem実装を取得
 */
expect fun createFileSystem(platformContext: Any? = null): FileSystem
