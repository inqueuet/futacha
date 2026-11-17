package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** 原作のメニューの後ろの1行（T-B1）。こちらだけの項目はこの中に入る。 */
internal const val FTUTTER_MORE_LABEL = "その他の機能"

/** 「従来の動き」：メニューの追加の項目をまとめず、前のように並べる（既定OFF）。 */
internal val FTUTTER_LEGACY_MENUS_KEY: String get() = FtutterKeys.PREFIX + "legacyMenus"
internal const val FTUTTER_LEGACY_MENUS_LABEL = "メニューの追加の項目をまとめずに並べる"
internal const val FTUTTER_LEGACY_MENUS_NOTE =
    "ONにすると、カタログ・スレッド・書き込みのメニューに、こちらで足した項目を「その他の機能」にまとめずに前の並びで出します"

internal fun ftutterLegacyMenus(preferences: Map<String, String>): Boolean = preferences.ftutterBool(FTUTTER_LEGACY_MENUS_KEY, false)

/**
 * 原作の項目（[original]）を並べ、こちらの項目（[extras]）は末尾の「その他の機能」を押した時に同じメニューの中で出す。
 * [hasExtras]が偽なら「その他の機能」は出さない。[legacy]（従来の動き）の時は呼び出し側が前の並びで描くので、ここは使わない。
 */
@Composable
internal fun FtutterFoldedMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    tag: String,
    hasExtras: Boolean,
    original: @Composable ColumnScope.() -> Unit,
    extras: @Composable ColumnScope.() -> Unit
) {
    var showExtras by remember { mutableStateOf(false) }
    val dismiss = { showExtras = false; onDismiss() }
    DropdownMenu(expanded = expanded, onDismissRequest = dismiss) {
        if (showExtras) {
            DropdownMenuItem(
                text = { Text(FTUTTER_MORE_LABEL) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "戻る") },
                onClick = { showExtras = false },
                modifier = Modifier.testTag("$tag-more-back")
            )
            extras()
        } else {
            original()
            if (hasExtras) DropdownMenuItem(
                text = { Text(FTUTTER_MORE_LABEL) },
                trailingIcon = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                onClick = { showExtras = true },
                modifier = Modifier.testTag("$tag-more")
            )
        }
    }
    // 閉じたら次に開く時は原作の項目から。
    androidx.compose.runtime.LaunchedEffect(expanded) { if (!expanded) showExtras = false }
}
