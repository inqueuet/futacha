@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.util

/**
 * Cross-platform logging utility
 * Provides a consistent logging interface across Android and iOS platforms
 *
 * FIX: ロギングのベストプラクティス
 *
 * ## ログレベルの使い分け：
 * - d (Debug): 開発時のデバッグ情報（本番では無効化）
 *   例: データフロー、状態遷移、詳細なパラメータ
 *
 * - i (Info): 重要なビジネスイベント（本番でも有効）
 *   例: ユーザーアクション、重要な状態変更、処理完了
 *
 * - w (Warning): 問題が発生したが処理は継続（要注意）
 *   例: リトライ実行、フォールバック使用、想定外の値
 *
 * - e (Error): エラーが発生し処理が失敗（要対応）
 *   例: 例外キャッチ、処理中断、データ不整合
 *
 * ## セキュリティとプライバシー：
 * - 個人情報（メールアドレス、パスワードなど）をログに出力しない
 * - APIキー、トークンなどの機密情報をログに出力しない
 * - ユーザーIDは必要最小限に（できればハッシュ化）
 * - 本番環境ではDebugログを無効化
 *
 * ## パフォーマンス：
 * - 文字列結合は遅延評価を推奨（ラムダ式）
 * - 大量のログ出力はパフォーマンスに影響
 * - ループ内での頻繁なログは避ける
 * - Debug ログは条件付きで出力
 *
 * ## 例外処理：
 * - エラーログには必ず例外オブジェクトを含める
 * - スタックトレースで原因特定を容易に
 * - CancellationExceptionはログ不要（正常なキャンセル）
 */
expect object Logger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String)
    fun i(tag: String, message: String)
}
