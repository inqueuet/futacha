@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.ui.futaber.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import coil3.ImageLoader
import coil3.ColorImage
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader

class FutaberSavedSnapshotsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private var loader: ImageLoader? = null
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader?.shutdown()
    }

    @Test fun twoSnapshotsOfTheSameThreadCanAppearTogether() {
        val one = SavedThread("123", "b", "board", "snapshot-one", "archive-one", null, 1L, 1, 0, 0, 10L, SaveStatus.COMPLETED)
        val two = one.copy(title = "snapshot-two", storageId = "archive-two")
        val images = ImageLoader.Builder(rule.activity).memoryCache(null).diskCache(null).components {
            add(Interceptor { chain -> SuccessResult(ColorImage(android.graphics.Color.BLUE, 64, 64), chain.request) })
        }.build().also { loader = it }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides images, LocalFutachaCatalogImageLoader provides images) {
            FutaberTheme(FutaberThemeMode.Light) {
                FutaberManagePanel(
                    history = emptyList(), boards = emptyList(), tabs = emptyList(),
                    savedBox = FutaberSavedBoxState(threads = listOf(one, two), isLoading = false), savedBoxAvailable = true,
                    onOpenSaved = {}, onDeleteSaved = {},
                    notifications = emptyList(), notificationsAvailable = false, notificationsStatus = "",
                    onOpenNotification = {}, onRemoveNotification = {}, onClearNotifications = {}, onOpenWatcherManager = {},
                    favorites = emptyList(), onOpenFavorite = {}, onRemoveFavorite = {}, onClearFavorites = {},
                    onOpenHistory = {}, onDeleteHistory = {}, onOpenTab = {}, onRemoveTab = {}, onClearTabs = {}, onClose = {}
                )
            }
            }
        }
        rule.onNodeWithTag("futaber-manage-category-SavedBox").performClick()
        rule.onNodeWithText("snapshot-one").assertIsDisplayed()
        rule.onNodeWithText("snapshot-two").assertIsDisplayed()
    }
}
