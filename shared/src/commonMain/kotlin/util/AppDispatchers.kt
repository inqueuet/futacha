package com.valoser.futacha.shared.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * アプリケーション全体で使用するCoroutineDispatcherの集中管理
 *
 * FIX: メインスレッドでの重い処理を防止するためのガイドライン
 * - parsing: HTML/JSONパース処理専用（並列度2）
 *   使用例: withContext(AppDispatchers.parsing) { parseHtml(html) }
 * - mockData: テストデータ生成専用（並列度1）
 * - imageFetch: 画像フェッチ専用（並列度可変）
 * - io: ファイルI/O、ネットワーク処理（Dispatchers.IO）
 *
 * 重要: UIから呼ばれる可能性がある重い処理は、必ずこれらのDispatcherを使用すること
 *
 * Dispatcher選択ガイド：
 * - CPU集約的処理（パース、計算）: parsing または Dispatchers.Default
 * - I/O処理（ファイル、ネットワーク）: io または Dispatchers.IO
 * - UI更新: Dispatchers.Main（自動的に使用される）
 *
 * ## UI層のパフォーマンス最適化ガイドライン：
 *
 * ### 1. Recompositionの最適化
 * - 安定した型（@Stable、data class）を使用
 * - remember { } で計算結果をキャッシュ
 * - derivedStateOf { } で派生状態を最適化
 * - key() を使用して不要なRecompositionを防止
 *
 * ### 2. LaunchedEffectのキー管理
 * - キーは変更時のみeffectを再実行すべき値に限定
 * - 頻繁に変わる値をキーにしない
 * - Unit キーは初回のみ実行（画面表示時の初期化用）
 *
 * ### 3. 大量リストの最適化
 * - LazyColumn/LazyRowを使用（1000+アイテム対応）
 * - items()のkey引数を必ず指定
 * - 複雑なアイテムはサブコンポーネント化
 * - Paging 3ライブラリの検討（超大量データ）
 *
 * ### 4. 状態管理の最適化
 * - collectAsState()は必要最小限に
 * - 複数のFlowを1つに統合（combine、zip）
 * - 状態の分割（大きなStateを小さく分割）
 */
object AppDispatchers {
    // FIX: HTML/JSONパース処理専用Dispatcher（並列度2で過負荷防止）
    val parsing: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)

    // FIX: テストデータ生成専用Dispatcher（並列度1で順序保証）
    val mockData: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

    // FIX: I/O処理専用Dispatcher（ファイル、ネットワーク）
    val io: CoroutineDispatcher = Dispatchers.IO

    /**
     * 画像フェッチ専用Dispatcherファクトリー
     * @param parallelism 並列度（最小1）
     */
    fun imageFetch(parallelism: Int): CoroutineDispatcher {
        return Dispatchers.Default.limitedParallelism(parallelism.coerceAtLeast(1))
    }
}
