package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.COMPAT_THREAD_CACHE_PREFERENCE_KEY
import com.valoser.futacha.shared.model.SaveLocation
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val COMMON_PRIVACY_ALPHA_KEY = "commonPrivacyAlpha"
private const val COMMON_PRIVACY_ALPHA_STORAGE_KEY = "compat.common.commonPrivacyAlpha"
internal const val COMPAT_COMMON_PRIVACY_STORAGE_KEY = "compat.common.commonPrivacy"
internal const val COMPAT_CATALOG_VIEW_MODE_STORAGE_KEY = "compat.catalog.catalogViewMode"
private const val LEGACY_CATALOG_PRIVACY_STORAGE_KEY = "compat.catalog.プライバシー"
private const val LEGACY_THREAD_PRIVACY_STORAGE_KEY = "compat.thread.プライバシー"
private const val COMMON_THREAD_CACHE_KEY = "commonThreadCache"
private const val COMMON_THREAD_CACHE_STABLE_STORAGE_KEY = "compat.storage.commonThreadCache"
private const val LEGACY_LAST_DELETE_KEY_STORAGE_KEY = "compat.lastDeleteKey"

internal const val COMPAT_POST_DELETE_KEY_MAX_LENGTH = 8
internal const val COMPAT_POST_DELETE_KEY_STORAGE_KEY = "compat.common.commonPostDeleteKey"

/**
 * Compatibility preferences use the original APK preference keys, namespaced away from the
 * modern Futacha settings. commonPrivacyAlpha is deliberately shared by Catalog and Thread,
 * matching the APK's single preference key.
 */
internal fun compatPreferenceStorageKey(path: String, preferenceKey: String): String =
    when (preferenceKey) {
        COMMON_PRIVACY_ALPHA_KEY -> COMMON_PRIVACY_ALPHA_STORAGE_KEY
        COMMON_THREAD_CACHE_KEY -> COMPAT_THREAD_CACHE_PREFERENCE_KEY
        else -> "compat.$path.$preferenceKey"
    }

/** Reads the stable key first and then any pre-migration Japanese-title keys. */
internal fun Map<String, String>.compatPreferenceValue(
    path: String,
    preferenceKey: String,
    vararg legacyTitles: String
): String? {
    this[compatPreferenceStorageKey(path, preferenceKey)]?.let { return it }
    if (preferenceKey == COMMON_PRIVACY_ALPHA_KEY) {
        this["compat.catalog.プライバシー透明度"]?.let { return it }
        this["compat.thread.プライバシー透明度"]?.let { return it }
    }
    if (preferenceKey == COMMON_THREAD_CACHE_KEY) {
        // The first compatibility build used the stable English key.  The
        // Android quota enforcer uses the APK-compatible Japanese key, so
        // read both while writes use the enforced key above.
        this[COMMON_THREAD_CACHE_STABLE_STORAGE_KEY]?.let { return it }
    }
    legacyTitles.forEach { title ->
        this["compat.$path.$title"]?.let { return it }
    }
    return null
}

/**
 * Matches the APK's commonPostDeleteKey preference and accepts the temporary key used by the
 * first compatibility implementation so existing users do not lose their saved key.
 */
internal fun Map<String, String>.compatStoredPostDeleteKey(): String {
    return sequenceOf(
        this[COMPAT_POST_DELETE_KEY_STORAGE_KEY],
        this[LEGACY_LAST_DELETE_KEY_STORAGE_KEY]
    ).mapNotNull { value ->
        value?.trim()?.takeIf { it.isNotBlank() }
    }.firstOrNull()?.take(COMPAT_POST_DELETE_KEY_MAX_LENGTH).orEmpty()
}

/**
 * commonPrivacy is one shared preference in old.apk/1.apk. Early compatibility builds
 * accidentally created separate Catalog and Thread keys; keep those as read-only migration
 * fallbacks while every new write uses the stable shared key.
 */
internal fun Map<String, String>.compatPrivacyEnabled(): Boolean =
    sequenceOf(
        this[COMPAT_COMMON_PRIVACY_STORAGE_KEY],
        this[LEGACY_CATALOG_PRIVACY_STORAGE_KEY],
        this[LEGACY_THREAD_PRIVACY_STORAGE_KEY]
    ).firstOrNull { it == "ON" || it == "OFF" } == "ON"

internal fun Map<String, String>.compatCatalogLayout(
    fallback: com.valoser.futacha.shared.compat.CompatCatalogLayout
): com.valoser.futacha.shared.compat.CompatCatalogLayout =
    when (this[COMPAT_CATALOG_VIEW_MODE_STORAGE_KEY]) {
        "0" -> com.valoser.futacha.shared.compat.CompatCatalogLayout.GRID
        "1" -> com.valoser.futacha.shared.compat.CompatCatalogLayout.LIST
        else -> fallback
    }

internal fun compatCatalogLayoutStorageValue(
    layout: com.valoser.futacha.shared.compat.CompatCatalogLayout
): String = if (layout == com.valoser.futacha.shared.compat.CompatCatalogLayout.GRID) "0" else "1"

internal fun compatPostDeleteKeyForStorage(value: String): String {
    return value.trim().take(COMPAT_POST_DELETE_KEY_MAX_LENGTH)
}

internal fun parseCompatPercent(value: String?, defaultPercent: Int = 20): Float =
    (value?.filter(Char::isDigit)?.toIntOrNull() ?: defaultPercent)
        .coerceIn(0, 100) / 100f

/**
 * The legacy setting stores the visible image alpha percentage.  The APK's
 * Catalog/Thread ImageViews use the value directly and its Viewer draws a
 * black overlay with the inverse value. Keep this conversion in one place so
 * all Compose surfaces match that behavior.
 */
internal fun compatPrivacyContentAlpha(transparency: Float): Float =
    transparency.coerceIn(0f, 1f)

internal fun compatPrivacyRenderAlpha(enabled: Boolean, transparency: Float): Float =
    if (enabled) compatPrivacyContentAlpha(transparency) else 1f

internal fun compatPrivacyOverlayAlpha(enabled: Boolean, transparency: Float): Float =
    (1f - compatPrivacyRenderAlpha(enabled, transparency)).coerceIn(0f, 1f)

/**
 * Privacy mode affects media only.  The reference UI does not dim the text or
 * the whole window; it makes attached images harder to identify. Keep the
 * reference alpha and add a small, density-independent blur so the behavior
 * is useful even when a nearly transparent image is still legible.
 */
internal fun Modifier.compatPrivacyImageEffect(contentAlpha: Float): Modifier {
    val alpha = contentAlpha.coerceIn(0f, 1f)
    if (alpha >= 0.999f) return this
    val blurRadius = ((1f - alpha) * 18f).coerceIn(2f, 18f)
    return blur(blurRadius.dp).graphicsLayer { this.alpha = alpha }
}

internal fun parseCompatSaveLocation(value: String?): SaveLocation? {
    val normalized = value?.trim().orEmpty()
    if (normalized.isBlank() || normalized in setOf("未選択", "標準フォルダー", "一時保存")) return null
    return SaveLocation.fromString(normalized)
}
