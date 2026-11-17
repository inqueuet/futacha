package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.ui.futaber.futaberAppLockPasswordError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatAppStateSettingsTest {
    @Test
    fun theRootSchemaIsUnchangedWithoutAnAppStateStore() {
        assertEquals(compatRootSettingsGroups("8.5"), compatRootSettingsGroups("8.5", includeAppStateEntries = false))
        val extras = compatRootSettingsGroups("8.5").first { it.first == "ふたちゃ拡張" }.second
        assertTrue(extras.none { it.preferenceKey == "appLock" || it.preferenceKey == "appIcon" })
    }

    @Test
    fun theLockAndIconRowsJoinTheFutachaExtensionsRightAfterThePrivacyRow() {
        val groups = compatRootSettingsGroups("8.5", includeAppStateEntries = true)
        assertEquals(compatRootSettingsGroups("8.5").map { it.first }, groups.map { it.first })
        // Every other group is exactly as it was.
        compatRootSettingsGroups("8.5").zip(groups).filter { it.first.first != "ふたちゃ拡張" }.forEach { (a, b) -> assertEquals(a, b) }
        val extras = groups.first { it.first == "ふたちゃ拡張" }.second
        val privacy = extras.indexOfFirst { it.route == "privacy" }
        assertEquals(listOf("起動ロック", "アプリアイコン"), extras.subList(privacy + 1, privacy + 3).map { it.title })
        assertEquals(compatRootSettingsGroups("8.5").first { it.first == "ふたちゃ拡張" }.second.size + 2, extras.size)
        // Asking twice does not add the rows twice.
        assertEquals(groups, compatRootSettingsGroups("8.5", includeAppStateEntries = true))
    }

    @Test
    fun theRowsAreNeitherSwitchesNorValueRowsAndTheirKeysDoNotClash() {
        compatAppStateEntries().forEach { entry ->
            assertFalse(compatIsBooleanPreference("root", entry), entry.title)
            assertTrue(compatPreferenceOptions("root", entry).isEmpty(), entry.title)
            assertEquals(entry.preferenceKey, CompatAppStateSetting.fromPreferenceKey(entry.preferenceKey)?.preferenceKey)
        }
        val all = compatRootSettingsGroups("8.5", includeAppStateEntries = true).flatMap { it.second }.map { it.preferenceKey }
        assertEquals(all.size, all.toSet().size)
        assertNull(CompatAppStateSetting.fromPreferenceKey("mode"))
    }

    @Test
    fun passwordRulesAndWordingMatchTheOtherModes() {
        listOf("" to "", "abc" to "abc", "abcd" to "abce", "abcd" to "abcd", "a".repeat(256) to "a".repeat(256), "a".repeat(257) to "a".repeat(257)).forEach { (p, c) ->
            assertEquals(futaberAppLockPasswordError(p, c), compatAppLockPasswordError(p, c), "$p/$c")
        }
        assertEquals("4文字以上で入力してください。", compatAppLockPasswordError("abc", "abc"))
        assertEquals("確認用パスワードが一致しません。", compatAppLockPasswordError("abcd", "abce"))
        assertNull(compatAppLockPasswordError("abcd", "abcd"))
    }

    @Test
    fun takeCharsNeverCutsASurrogatePair() {
        assertEquals("abc", "abcdef".compatTakeChars(3))
        assertEquals("ab", "ab😀".compatTakeChars(3))
        assertEquals("ab😀", "ab😀".compatTakeChars(4))
        assertEquals("", "abc".compatTakeChars(0))
    }

    @Test
    fun summariesSayWhatIsSetAndOnlyTheTwoSharedIconsAreOffered() {
        assertEquals("有効・起動時にパスワードを要求", CompatAppStateSettingsState(true, AppIconVariant.Current).summary(CompatAppStateSetting.AppLock))
        assertEquals("無効・パスワードを設定すると起動時にロック", CompatAppStateSettingsState(false, AppIconVariant.Current).summary(CompatAppStateSetting.AppLock))
        assertEquals("クラシック", CompatAppStateSettingsState(false, AppIconVariant.Classic).summary(CompatAppStateSetting.AppIcon))
        assertEquals(listOf(AppIconVariant.Current, AppIconVariant.Classic), COMPAT_OFFERED_APP_ICONS)
    }

    @Test
    fun theAiCommandRejectionNamesThePageThatNowHasTheSwitch() {
        assertTrue("AI・補助機能" in COMPAT_AI_COMMANDS_DISABLED_MESSAGE)
        assertFalse("ふたちゃモード" in COMPAT_AI_COMMANDS_DISABLED_MESSAGE)
    }
}
