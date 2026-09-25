package com.valoser.futacha

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionService
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appsearch.GenericDocument
import android.content.Intent
import android.content.pm.SigningInfo
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandBridge
import com.valoser.futacha.shared.ai.FutachaAiCommandReception
import com.valoser.futacha.shared.ai.describeFutachaAiCommandReception
import com.valoser.futacha.shared.ai.sanitizeFutachaAiCommandParameters
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.util.Logger
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@RequiresApi(36)
class FutachaAppFunctionService : AppFunctionService() {
    override fun onExecuteFunction(
        request: ExecuteAppFunctionRequest,
        callingPackage: String,
        callingPackageSigningInfo: SigningInfo,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onError(
                AppFunctionException(
                    AppFunctionException.ERROR_CANCELLED,
                    "Futacha AI command was cancelled"
                )
            )
            return
        }

        val requestedAction = request.functionIdentifier.takeIf { it.isNotBlank() }
            ?: return callback.onError(
                AppFunctionException(
                    AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Missing Futacha AI action identifier"
                )
            )
        val reception = describeFutachaAiCommandReception(requestedAction)
            ?: return callback.onError(
                AppFunctionException(
                    AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Unsupported Futacha AI action: $requestedAction"
                )
            )
        val action = FutachaAiAction.fromId(reception.actionId)
            ?: return callback.onError(
                AppFunctionException(
                    AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Unsupported Futacha AI action: $requestedAction"
                )
            )
        val profileStore = (application as? FutachaApplication)?.experienceProfileStore
        if (profileStore?.readActiveProfile() != ExperienceProfile.FUTACHA) {
            callback.onResult(
                ExecuteAppFunctionResponse(
                    buildResultDocument(
                        reception = reception,
                        commandId = "inactive-profile",
                        status = APP_FUNCTION_INACTIVE_PROFILE_STATUS,
                        message = "ふたちゃモードが選択されていないため操作しませんでした。",
                        duplicate = false
                    )
                )
            )
            return
        }
        val sanitizedParameters = sanitizeFutachaAiCommandParameters(request.parameters.toStringParameters())
        val commandId = sanitizedParameters.validCommandIdOrNull() ?: UUID.randomUUID().toString()
        val commandParameters = sanitizedParameters.withoutCommandIdAliases() + ("commandId" to commandId)
        if (!markCommandIdAccepted(commandId)) {
            callback.onResult(
                ExecuteAppFunctionResponse(
                    buildResultDocument(
                        reception = reception,
                        commandId = commandId,
                        status = APP_FUNCTION_DUPLICATE_STATUS,
                        message = "同じ commandId の Futacha AI 操作は既に受け付け済みです。",
                        duplicate = true
                    )
                )
            )
            return
        }
        val command = FutachaAiCommand(
            action = action,
            parameters = commandParameters,
            source = "android-app-functions"
        )
        val app = application as? FutachaApplication
        if (app == null || app.startedMainActivities.value > 0) {
            // The UI is on screen and consumes the bridge immediately.
            if (!enqueueCommand(command, commandId, callback)) return
            openMainActivity()
            callback.onResult(acceptedResponse(reception, commandId))
            return
        }

        // No MainActivity is started. Android 10+ silently blocks activity
        // starts from a background service, and a command left in the bridge
        // would then run whenever the user opens the app later. Only queue it
        // once the activity has actually come to the foreground; otherwise
        // report that the app could not be opened.
        if (!openMainActivity()) {
            forgetAcceptedCommandId(commandId)
            callback.onError(appNotOpenedError())
            return
        }
        app.applicationScope.launch {
            val opened = withTimeoutOrNull(APP_FUNCTION_ACTIVITY_START_TIMEOUT_MILLIS) {
                app.startedMainActivities.first { it > 0 }
            } != null
            if (cancellationSignal.isCanceled) {
                forgetAcceptedCommandId(commandId)
                callback.onError(
                    AppFunctionException(
                        AppFunctionException.ERROR_CANCELLED,
                        "Futacha AI command was cancelled"
                    )
                )
                return@launch
            }
            if (!opened) {
                Logger.w(TAG, "MainActivity did not start for AppFunction command; not queuing it")
                forgetAcceptedCommandId(commandId)
                callback.onError(appNotOpenedError())
                return@launch
            }
            if (!enqueueCommand(command, commandId, callback)) return@launch
            callback.onResult(acceptedResponse(reception, commandId))
        }
    }

    private fun enqueueCommand(
        command: FutachaAiCommand,
        commandId: String,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>
    ): Boolean {
        if (FutachaAiCommandBridge.enqueue(command)) return true
        forgetAcceptedCommandId(commandId)
        callback.onError(
            AppFunctionException(
                AppFunctionException.ERROR_CANCELLED,
                "Futacha is busy. Open the app and try again."
            )
        )
        return false
    }

    /** Returns false only when the start request itself was rejected. */
    private fun openMainActivity(): Boolean {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return runCatching {
            startActivity(intent)
        }.onFailure { error ->
            Logger.w(TAG, "Failed to open MainActivity for AppFunction command: ${error.message}")
        }.isSuccess
    }

    private fun appNotOpenedError() = AppFunctionException(
        AppFunctionException.ERROR_APP_UNKNOWN_ERROR,
        "Futacha could not be brought to the foreground. Open the app and try again."
    )

    private fun acceptedResponse(reception: FutachaAiCommandReception, commandId: String) =
        ExecuteAppFunctionResponse(
            buildResultDocument(
                reception = reception,
                commandId = commandId,
                status = reception.status,
                message = reception.message,
                duplicate = false
            )
        )

    private fun buildResultDocument(
        reception: FutachaAiCommandReception,
        commandId: String,
        status: String,
        message: String,
        duplicate: Boolean
    ): GenericDocument {
        val result = GenericDocument.Builder<GenericDocument.Builder<*>>(
            "futacha",
            "ai-command-result",
            "FutachaAiCommandResult"
        )
            .setPropertyString("commandId", commandId)
            .setPropertyString("status", status)
            .setPropertyString("action", reception.actionId)
            .setPropertyString("label", reception.actionLabel)
            .setPropertyString("risk", reception.risk.name)
            .setPropertyString("message", message)
            .setPropertyBoolean("requiresConfirmation", reception.requiresConfirmation)
            .setPropertyBoolean("duplicate", duplicate)
            .build()
        return result
    }

    private fun GenericDocument?.toStringParameters(): Map<String, String> {
        val document = this ?: return emptyMap()
        return document.propertyNames
            .asSequence()
            .filterNot { it.equals("action", ignoreCase = true) || it.equals("command", ignoreCase = true) }
            .take(APP_FUNCTION_MAX_PROPERTY_COUNT)
            .sorted()
            .mapNotNull { key ->
                document.firstScalarProperty(key)?.takeIf { it.isNotBlank() }?.let { value ->
                    key to value
                }
            }
            .toMap()
    }

    private fun GenericDocument.firstScalarProperty(key: String): String? {
        return runCatching {
            getPropertyStringArray(key)?.firstOrNull { it.isNotBlank() }
                ?: getPropertyString(key)
                ?: getPropertyLongArray(key)?.firstOrNull()?.toString()
                ?: getPropertyDoubleArray(key)?.firstOrNull()?.toString()
                ?: getPropertyBooleanArray(key)?.firstOrNull()?.toString()
        }.getOrNull()
            ?.take(APP_FUNCTION_MAX_PROPERTY_VALUE_CHARS)
            ?.trim()
    }

    private fun Map<String, String>.validCommandIdOrNull(): String? {
        return entries
            .firstOrNull { (key, value) ->
                key.isCommandIdKey() && value.isValidCommandId()
            }
            ?.value
            ?.trim()
    }

    private fun Map<String, String>.withoutCommandIdAliases(): Map<String, String> {
        return filterKeys { key -> !key.isCommandIdKey() }
    }

    private fun String.isCommandIdKey(): Boolean {
        val normalized = trim()
            .lowercase()
            .filter { it != '_' && it != '-' && !it.isWhitespace() }
        return normalized == "commandid"
    }

    private fun String.isValidCommandId(): Boolean {
        val trimmed = trim()
        return trimmed.isNotBlank() &&
            trimmed.length <= APP_FUNCTION_COMMAND_ID_MAX_BYTES &&
            trimmed.encodeToByteArray().size <= APP_FUNCTION_COMMAND_ID_MAX_BYTES
    }

    private fun markCommandIdAccepted(commandId: String): Boolean {
        synchronized(acceptedCommandIdsLock) {
            if (!acceptedCommandIds.add(commandId)) {
                return false
            }
            while (acceptedCommandIds.size > APP_FUNCTION_ACCEPTED_COMMAND_ID_MAX_COUNT) {
                val oldestCommandId = acceptedCommandIds.firstOrNull() ?: break
                acceptedCommandIds.remove(oldestCommandId)
            }
            return true
        }
    }

    private fun forgetAcceptedCommandId(commandId: String) {
        synchronized(acceptedCommandIdsLock) {
            acceptedCommandIds.remove(commandId)
        }
    }

    private companion object {
        private const val TAG = "FutachaAppFunctionService"
        private const val APP_FUNCTION_COMMAND_ID_MAX_BYTES = 128
        private const val APP_FUNCTION_MAX_PROPERTY_COUNT = 64
        private const val APP_FUNCTION_MAX_PROPERTY_VALUE_CHARS = 12_001
        private const val APP_FUNCTION_ACCEPTED_COMMAND_ID_MAX_COUNT = 256
        private const val APP_FUNCTION_DUPLICATE_STATUS = "accepted_duplicate"
        private const val APP_FUNCTION_INACTIVE_PROFILE_STATUS = "inactive_profile"
        private const val APP_FUNCTION_ACTIVITY_START_TIMEOUT_MILLIS = 5_000L
        private val acceptedCommandIdsLock = Any()
        private val acceptedCommandIds = LinkedHashSet<String>()
    }
}
