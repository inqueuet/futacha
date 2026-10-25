package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.ui.compat.compatPlatformAiReplyDraft
import kotlin.test.*

/** Synthetic keys only. Assertions intentionally never print key values. */
class M64ExternalDraftDeleteKeyTest {
    private val suppliedKey = "m64-untrusted-fixture"
    private val ownerKey = "m64-owner-fixture"
    private val aliases = listOf("password", "deleteKey", "delete_key", "deletePassword", "delete_password", "PaSsWoRd", "DELETE-KEY")

    @Test fun everyExternalDraftActionIgnoresAllDeleteKeyAliases() {
        for (action in listOf("draft_reply", "draft_thread")) for (alias in aliases) {
            val command = checkNotNull(parseFutachaAiDeepLink("futacha://ai?action=$action&$alias=$suppliedKey&comment=local-body"))
            assertTrue(command.draftPasswordParameter() == null, "An external draft must not supply a deletion key")
            assertTrue(command.draftCommentParameter() == "local-body", "The draft body must remain usable")
        }
    }

    @Test fun duplicateQueryAliasesAndForgedSourceCannotCrossTheLinkBoundary() {
        val command = checkNotNull(parseFutachaAiDeepLink(
            "futacha://ai?action=draft_reply&password=first&password=$suppliedKey&deleteKey=another&source=android-app-functions&Source=internal-ai"))
        assertTrue(command.isFromExternalLink())
        assertTrue(command.source == "deep-link", "The caller owns source; query parameters cannot replace it")
        assertTrue(command.draftPasswordParameter() == null, "Repeated or competing key aliases must all be ignored")
    }

    @Test fun allExistingPlatformLinkSourcesApplyTheSamePolicy() {
        for (source in listOf("platform", "deep-link", "bridge", "ios", "ios-retry", "ios-thread-deep-link", " IOS ")) {
            val command = checkNotNull(parseFutachaAiDeepLink("futacha://ai?action=draft_thread&password=$suppliedKey", source))
            assertTrue(command.isFromExternalLink())
            assertTrue(command.draftPasswordParameter() == null, "An existing platform link source bypassed the policy")
        }
    }

    @Test fun trustedInternalCommandsKeepExplicitDeleteKeys() {
        for (source in listOf("android-app-functions", "ios-app-intents", "siri", "watchos", "wear-os", "unknown")) {
            val command = FutachaAiCommand(FutachaAiAction.DraftReply, mapOf("deletePassword" to ownerKey), source)
            assertTrue(command.draftPasswordParameter() == ownerKey, "An internal command's existing key contract changed")
        }
    }

    @Test fun confirmationExplainsIgnoredKeysWithoutDisplayingTheirValues() {
        val command = checkNotNull(parseFutachaAiDeepLink("futacha://ai?action=draft_reply&password=$suppliedKey&comment=local-body"))
        val message = buildFutachaAiConfirmationMessage(command)
        assertTrue("削除キー" in message && "使用しません" in message, "The existing preview must explain the ignored input")
        assertFalse(suppliedKey in message, "A deletion key must not appear in the preview")
        val internal = buildFutachaAiConfirmationMessage(command.copy(source = "android-app-functions"))
        assertFalse("使用しません" in internal, "Internal draft behavior must retain its existing confirmation")
    }

    @Test fun compatibilityDraftMergePreservesTheExistingKeyAndDoesNotSaveKeyOnlyLinks() {
        val stored = CompatReplyDraft(tabKey = "thread", comment = "old-body", deleteKey = ownerKey, updatedAtEpochMillis = 1)
        val command = checkNotNull(parseFutachaAiDeepLink("futacha://ai?action=draft_reply&password=$suppliedKey&comment=local-body"))
        val merged = checkNotNull(compatPlatformAiReplyDraft(command, "thread", stored, 2))
        assertTrue(merged.deleteKey == ownerKey, "The saved draft's key was replaced by external input")
        assertTrue(merged.comment == "local-body")
        val keyOnly = checkNotNull(parseFutachaAiDeepLink("futacha://ai?action=draft_reply&delete_key=$suppliedKey"))
        assertTrue(compatPlatformAiReplyDraft(keyOnly, "thread", stored, 3) == null,
            "A link with only an ignored key must not rewrite a draft")
    }
}
