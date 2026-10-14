package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/*
 * アイコンの見た目（2026-10-14、ユーザー指示「AndroidやiOSアプリのはやりのものに」）。
 * - 形：角の丸いアイコン（Compose の Icons.Rounded。Material 3 Expressive の Material Symbols Rounded、iOS の SF Symbols の
 *   丸みに近い）。原作の Icons.Filled（角ばった塗り）から置き換えた。ボタンの位置・数・動きは変えない。
 * - 選んだ状態：色に加えて、アイコンの後ろに淡いカプセルを敷く（Material 3 のナビゲーションの「アクティブ表示」、
 *   iOS 26 のタブバーの選択と同じ見せ方）。
 */

/** 選んだ状態のカプセルの色（ツールバーの上に重ねる）。 */
internal fun ftutterActiveIndicator(colors: FtutterColors): Color = colors.onToolBarActive.copy(alpha = FTUTTER_ACTIVE_INDICATOR_ALPHA)

/** カプセルを重ねた後の、ツールバーの色。 */
internal fun ftutterActiveIndicatorOnBar(colors: FtutterColors): Color = ftutterActiveIndicator(colors).compositeOver(colors.toolBar)

internal const val FTUTTER_ACTIVE_INDICATOR_ALPHA = 0.14f

private val FTUTTER_PILL = RoundedCornerShape(50)

/** 切り替えのボタンのアイコン。オンの時は色を変え、後ろにカプセルを敷く（大きさはオフの時と同じ）。 */
@Composable
internal fun FtutterToggleIcon(icon: ImageVector, contentDescription: String?, active: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalFtutterColors.current
    Icon(
        icon,
        contentDescription = contentDescription,
        tint = if (active) colors.onToolBarActive else colors.onToolBar,
        modifier = modifier
            .background(if (active) ftutterActiveIndicator(colors) else Color.Transparent, FTUTTER_PILL)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}
