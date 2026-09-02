@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.version

import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.StoreKit.SKPaymentQueue
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant

private const val APP_STORE_LOOKUP_URL = "https://itunes.apple.com/lookup"
private const val MAX_APP_STORE_LOOKUP_RESPONSE_BYTES = 256 * 1024
private const val UI_TEST_UPDATE_PROMPT_ENVIRONMENT = "FUTACHA_UI_TEST_UPDATE_PROMPT"
private const val UI_TEST_APP_STORE_URL = "https://apps.apple.com/jp/app/id6756841201"

@Serializable
private data class AppStoreLookupResponse(
    val resultCount: Int = 0,
    val results: List<AppStoreLookupResult> = emptyList()
)

@Serializable
private data class AppStoreLookupResult(
    val version: String,
    val currentVersionReleaseDate: String? = null,
    val trackViewUrl: String? = null
)

/** iOS update checker backed by the public App Store listing. */
class IosVersionChecker(
    private val httpClient: HttpClient
) : VersionChecker {
    companion object {
        private const val TAG = "IosVersionChecker"
        private val json = Json { ignoreUnknownKeys = true }
    }

    override fun getCurrentVersion(): String {
        return try {
            val bundle = NSBundle.mainBundle
            val version = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
            version ?: "1.0.0"
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to get iOS version: ${e.message}")
            "1.0.0"
        }
    }

    override suspend fun checkForUpdate(): UpdateInfo? {
        val currentVersion = getCurrentVersion()
        val bundleId = NSBundle.mainBundle.bundleIdentifier?.takeIf { it.isNotBlank() } ?: return null
        val country = SKPaymentQueue.defaultQueue().storefront?.countryCode?.lowercase() ?: "jp"

        return try {
            val response = httpClient.get(APP_STORE_LOOKUP_URL) {
                parameter("bundleId", bundleId)
                parameter("country", country)
            }
            val body = readBoundedVersionResponseBody(
                response = response,
                maxResponseBytes = MAX_APP_STORE_LOOKUP_RESPONSE_BYTES
            )
            val listing = json.decodeFromString<AppStoreLookupResponse>(body).results.firstOrNull()
                ?: return null
            if (!isNewerVersion(currentVersion, listing.version)) return null

            val releaseEpochMillis = listing.currentVersionReleaseDate
                ?.let { date -> runCatching { Instant.parse(date).toEpochMilliseconds() }.getOrNull() }
            val stalenessDays = releaseEpochMillis?.let { releasedAt ->
                calculateUpdateStalenessDays(
                    releaseEpochMillis = releasedAt,
                    nowEpochMillis = Clock.System.now().toEpochMilliseconds()
                )
            } ?: 0

            UpdateInfo(
                currentVersion = currentVersion,
                latestVersion = listing.version,
                message = buildUpdateMessage(currentVersion, listing.version, null, null),
                updateUrl = listing.trackViewUrl,
                stalenessDays = stalenessDays,
                promptStyle = if (listing.trackViewUrl.isNullOrBlank()) {
                    UpdatePromptStyle.FLEXIBLE
                } else {
                    selectIosUpdatePromptStyle(stalenessDays)
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A missing storefront entry or an offline launch must never block the app.
            Logger.w(TAG, "Failed to check App Store version: ${e.message}")
            null
        }
    }
}

private class IosUpdatePromptUiTestVersionChecker(
    private val productionChecker: VersionChecker,
    private val promptStyle: UpdatePromptStyle
) : VersionChecker {
    override fun getCurrentVersion(): String = productionChecker.getCurrentVersion()

    override suspend fun checkForUpdate(): UpdateInfo {
        val currentVersion = getCurrentVersion()
        val latestVersion = "99.0"
        val stalenessDays = when (promptStyle) {
            UpdatePromptStyle.FLEXIBLE -> 1
            UpdatePromptStyle.IMMEDIATE -> IOS_IMMEDIATE_UPDATE_STALENESS_DAYS
        }
        return UpdateInfo(
            currentVersion = currentVersion,
            latestVersion = latestVersion,
            message = buildUpdateMessage(currentVersion, latestVersion, null, null),
            updateUrl = UI_TEST_APP_STORE_URL,
            stalenessDays = stalenessDays,
            promptStyle = promptStyle
        )
    }
}

actual fun createVersionChecker(httpClient: HttpClient): VersionChecker {
    val productionChecker = IosVersionChecker(httpClient)
    val uiTestPromptStyle = when (
        (NSProcessInfo.processInfo.environment[UI_TEST_UPDATE_PROMPT_ENVIRONMENT] as? String)
            ?.lowercase()
    ) {
        "flexible" -> UpdatePromptStyle.FLEXIBLE
        "immediate" -> UpdatePromptStyle.IMMEDIATE
        else -> null
    }
    return uiTestPromptStyle?.let { promptStyle ->
        Logger.w("IosVersionChecker", "Using $promptStyle update prompt UI-test fixture")
        IosUpdatePromptUiTestVersionChecker(productionChecker, promptStyle)
    } ?: productionChecker
}
