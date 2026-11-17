package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import io.ktor.http.encodeURLParameter

/** M60：ビューアの検索で「+ 追加」から足せる検索先（原作で確かめたのは二次元画像詳細検索だけ）。 */
internal enum class FtutterViewerSearchEngine(val id: String, val label: String) {
    ASCII2D("ascii2d", "二次元画像詳細検索")
}

/** 追加した検索先の保存の鍵（カンマ区切りのID）。 */
internal val FTUTTER_VIEWER_SEARCH_ENGINES_KEY: String get() = FtutterKeys.PREFIX + "viewerSearchEngines"

/** 前の形のビューア（従来の動き）。 */
internal val FTUTTER_LEGACY_VIEWER_KEY: String get() = FtutterKeys.PREFIX + "legacyViewer"
internal const val FTUTTER_LEGACY_VIEWER_LABEL = "ビューアの下のバーを前の形にする"
internal const val FTUTTER_LEGACY_VIEWER_NOTE =
    "保存先の枠・保存・帯・レス（小さなダイアログ）・画像検索（検索先を選ぶ）の並びに戻します（原作は保存先の選択・全幅のレス・google画像検索と「+ 追加」です）"

/** 原作の google画像検索のアドレス（画像のアドレスはエンコードして渡す）。 */
internal fun ftutterGoogleImageSearchUrl(imageUrl: String): String =
    "https://www.google.com/searchbyimage?sbisrc=chrome&image_url=" + imageUrl.encodeURLParameter()

/**
 * 「検索に使うURL」の受け方：原作は知っているサービスのトップのアドレスだけを受け、名前で足す
 * （`https://ascii2d.net/` は受け、`https://ascii2d.net/search/url/` や知らないアドレスは「対応していないURLです」）。
 */
internal fun ftutterViewerSearchEngineFor(input: String): FtutterViewerSearchEngine? {
    val text = input.trim()
    val rest = when {
        text.startsWith("https://") -> text.removePrefix("https://")
        text.startsWith("http://") -> text.removePrefix("http://")
        else -> text
    }
    val host = rest.substringBefore('/').lowercase().removePrefix("www.")
    val path = rest.substringAfter('/', "")
    if (path.isNotEmpty()) return null
    return when (host) {
        "ascii2d.net" -> FtutterViewerSearchEngine.ASCII2D
        else -> null
    }
}

internal fun parseFtutterViewerSearchEngines(preferences: Map<String, String>): List<FtutterViewerSearchEngine> =
    preferences[FTUTTER_VIEWER_SEARCH_ENGINES_KEY].orEmpty().split(',').mapNotNull { id ->
        FtutterViewerSearchEngine.entries.firstOrNull { it.id == id.trim() }
    }.distinct()

internal fun encodeFtutterViewerSearchEngines(engines: List<FtutterViewerSearchEngine>): String =
    engines.distinct().joinToString(",") { it.id }

/**
 * M60：原作の検索のダイアログ（題なし）。「google画像検索」、足した検索先、青い「+ 追加」。
 * こちらの共有の画像検索（検索先を選ぶ）と、足した検索先を外すのは「その他の機能」の下。
 */
@Composable
internal fun FtutterViewerSearchDialog(
    engines: List<FtutterViewerSearchEngine>,
    onGoogle: () -> Unit,
    onEngine: (FtutterViewerSearchEngine) -> Unit,
    onSaveEngines: (List<FtutterViewerSearchEngine>) -> Unit,
    onSharedSearch: () -> Unit,
    onNotice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalFtutterColors.current
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    if (adding) {
        var text by remember { mutableStateOf("") }
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("検索に使うURL") },
            text = {
                TextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text("二次元画像詳細検索 など") },
                    trailingIcon = {
                        if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, contentDescription = "消す") }
                    },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().testTag("ftutter-viewer-search-url")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val engine = ftutterViewerSearchEngineFor(text)
                    if (engine == null) {
                        onNotice("対応していないURLです")
                    } else {
                        adding = false
                        onSaveEngines(engines + engine)
                        onNotice("追加しました")
                    }
                }, modifier = Modifier.testTag("ftutter-viewer-search-url-ok")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("キャンセル") } }
        ) }
        return
    }
    if (removing) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text("追加した検索先を外す") },
            text = {
                Column {
                    if (engines.isEmpty()) Text("追加した検索先はありません")
                    engines.forEach { engine ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(engine.label, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onSaveEngines(engines - engine) }, modifier = Modifier.testTag("ftutter-viewer-search-remove-${engine.id}")) {
                                Icon(Icons.Rounded.Close, contentDescription = "外す")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { removing = false }) { Text("閉じる") } }
        ) }
        return
    }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ftutter-viewer-search-dialog"),
        text = {
            Column {
                Text("google画像検索", fontSize = 17.sp, modifier = Modifier.fillMaxWidth()
                    .clickable { onDismiss(); onGoogle() }.padding(vertical = 12.dp).testTag("ftutter-viewer-search-google"))
                engines.forEach { engine ->
                    Text(engine.label, fontSize = 17.sp, modifier = Modifier.fillMaxWidth()
                        .clickable { onDismiss(); onEngine(engine) }.padding(vertical = 12.dp).testTag("ftutter-viewer-search-${engine.id}"))
                }
                Row(Modifier.fillMaxWidth().clickable { adding = true }.padding(vertical = 12.dp).testTag("ftutter-viewer-search-add"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.highlight, modifier = Modifier.size(24.dp))
                    Text("追加", color = colors.highlight, fontSize = 17.sp, modifier = Modifier.padding(start = 16.dp))
                }
                HorizontalDivider(color = colors.outline)
                Text("その他の機能", color = colors.text, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Text("検索先を選んで検索", fontSize = 15.sp, modifier = Modifier.fillMaxWidth()
                    .clickable { onDismiss(); onSharedSearch() }.padding(vertical = 10.dp).testTag("ftutter-viewer-search-shared"))
                if (engines.isNotEmpty()) Text("追加した検索先を外す", fontSize = 15.sp, modifier = Modifier.fillMaxWidth()
                    .clickable { removing = true }.padding(vertical = 10.dp).testTag("ftutter-viewer-search-remove"))
            }
        },
        confirmButton = {}
    ) }
}

/** M58：原作の「保存先選択」（今の保存先に印、下に「保存先追加」）。 */
@Composable
internal fun FtutterSaveDestinationDialog(label: String, onAdd: () -> Unit, onDismiss: () -> Unit) {
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ftutter-save-destination-dialog"),
        title = { Text("保存先選択") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().clickable(onClick = onDismiss), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = true, onClick = onDismiss)
                    Text(label, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                }
                androidx.compose.material3.Button(onClick = { onDismiss(); onAdd() }, modifier = Modifier.padding(top = 12.dp).testTag("ftutter-save-destination-add")) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("保存先追加", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {}
    ) }
}
