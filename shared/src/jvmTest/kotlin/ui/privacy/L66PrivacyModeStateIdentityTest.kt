package com.valoser.futacha.shared.ui.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.ui.compat.COMPAT_CATALOG_VIEW_MODE_STORAGE_KEY
import com.valoser.futacha.shared.ui.compat.COMPAT_COMMON_PRIVACY_STORAGE_KEY
import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@Composable
private fun PrivacyProbe(record: (PrivacyModeState?) -> Unit) {
    val state = LocalPrivacyMode.current
    SideEffect { record(state) }
}

/**
 * 2026-10-20の監査L66：共有設定が1つ保存されるたびに、`PrivacyModeHost`が新しい`PrivacyModeState`を作り、
 * `LocalPrivacyMode`を読む全画像セル・上部バーが再合成されていた。プライバシーに関係ない保存では同じ状態のままで、
 * プライバシーに関係する変更（画像の強さ・としあき(仮)の透明度・ON/OFF・範囲）では今までどおり新しい値の状態になる。
 */
class L66PrivacyModeStateIdentityTest {
    /** A store whose preferences this test sets; savePreference writes them as the real stores do. */
    private class ControlledStore(base: CompatibilityStore, initial: Map<String, String>) : CompatibilityStore by base {
        val values = MutableStateFlow(initial)
        override val preferences: Flow<Map<String, String>> get() = values
        override suspend fun savePreference(key: String, value: String) {
            values.value = values.value + (key to value)
        }
    }

    private class Harness(content: @Composable () -> Unit) : AutoCloseable {
        private val scene = ImageComposeScene(100, 100, Density(1f), content = content)
        private var frame = 0L
        fun render() {
            Snapshot.sendApplyNotifications()
            frame += 16_000_000L
            scene.render(frame)
        }
        /** The first frames: the stored preferences (and switch) arrive after the first composition. */
        fun settle() = repeat(3) { render() }
        override fun close() = scene.close()
    }

    private fun <T> withStore(initial: Map<String, String>, block: (ControlledStore) -> T): T {
        val root = Files.createTempDirectory("futacha-l66").toFile()
        try {
            return block(ControlledStore(DesktopCompatibilityStore(JvmFileSystem(root)), initial))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun anUnrelatedSavedPreferenceKeepsTheStateAndPrivacyChangesStillMakeANewOne() = withStore(
        mapOf(COMPAT_COMMON_PRIVACY_STORAGE_KEY to "ON")
    ) { store ->
        val seen = mutableListOf<PrivacyModeState?>()
        Harness {
            PrivacyModeHost(store, stateStore = null, compatibilityMode = true) { PrivacyProbe { seen += it } }
        }.use { scene ->
            scene.settle()
            val initial = assertNotNull(seen.last())
            assertEquals(true, initial.enabled)
            assertEquals(null, initial.options.imageStrength)
            assertEquals(80, initial.inheritedImageStrength)

            // A preference that has nothing to do with privacy: the same state, and its readers are not recomposed.
            val compositions = seen.size
            runBlocking { store.savePreference(COMPAT_CATALOG_VIEW_MODE_STORAGE_KEY, "LIST") }
            runBlocking { store.savePreference("compat.thread.threadDisplayMode", "TREE") }
            scene.render()
            assertSame(initial, seen.last())
            assertEquals(compositions, seen.size)

            // The image strength of the privacy settings.
            runBlocking { store.savePreference(PRIVACY_IMAGE_STRENGTH_KEY, "30") }
            scene.render()
            val strengthened = assertNotNull(seen.last())
            assertNotSame(initial, strengthened)
            assertEquals(30, strengthened.options.imageStrength)

            // としあき(仮)'s shared transparency (commonPrivacyAlpha) is what the image strength starts from.
            runBlocking { store.savePreference("compat.common.commonPrivacyAlpha", "75") }
            scene.render()
            val inherited = assertNotNull(seen.last())
            assertNotSame(strengthened, inherited)
            assertEquals(25, inherited.inheritedImageStrength)

            // The kept setEnabled and save still write, and their changes still make a new state.
            inherited.setEnabled(false)
            scene.render()
            val off = assertNotNull(seen.last())
            assertNotSame(inherited, off)
            assertEquals(false, off.enabled)
            assertEquals("OFF", store.values.value[COMPAT_COMMON_PRIVACY_STORAGE_KEY])

            off.save(PRIVACY_SCOPE_KEY, PrivacyScope.SCREEN.name)
            scene.render()
            val screen = assertNotNull(seen.last())
            assertNotSame(off, screen)
            assertEquals(PrivacyScope.SCREEN, screen.options.scope)

            off.setEnabled(true)
            scene.render()
            val on = assertNotNull(seen.last())
            assertNotSame(screen, on)
            assertEquals(true, on.enabled)
        }
    }

    @Test
    fun theOtherModesFollowTheirOwnSwitchAndIgnoreUnrelatedPreferences() = withStore(emptyMap()) { store ->
        val stateStore = AppStateStore(FakePlatformStateStorage())
        val seen = mutableListOf<PrivacyModeState?>()
        Harness {
            PrivacyModeHost(store, stateStore = stateStore, compatibilityMode = false) { PrivacyProbe { seen += it } }
        }.use { scene ->
            scene.settle()
            val initial = assertNotNull(seen.last())
            val stored = runBlocking { stateStore.isPrivacyFilterEnabled.first() }
            assertEquals(stored, initial.enabled)
            assertEquals(50, initial.inheritedImageStrength)

            runBlocking { store.savePreference(COMPAT_CATALOG_VIEW_MODE_STORAGE_KEY, "LIST") }
            scene.render()
            assertSame(initial, seen.last())

            runBlocking { stateStore.setPrivacyFilterEnabled(!stored) }
            scene.render()
            val toggled = assertNotNull(seen.last())
            assertNotSame(initial, toggled)
            assertEquals(!stored, toggled.enabled)
        }
    }
}
