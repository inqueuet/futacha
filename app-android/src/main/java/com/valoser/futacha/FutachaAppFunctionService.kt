package com.valoser.futacha

import android.annotation.TargetApi
import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionService
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appsearch.GenericDocument
import android.content.Intent
import android.content.pm.SigningInfo
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandBridge
import com.valoser.futacha.shared.ai.FutachaAiCommandReception
import com.valoser.futacha.shared.ai.describeFutachaAiCommandReception
import com.valoser.futacha.shared.ai.sanitizeFutachaAiCommandParameters
import com.valoser.futacha.shared.util.Logger
import java.util.UUID

@TargetApi(36)
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
        val accepted = FutachaAiCommandBridge.enqueue(
            FutachaAiCommand(
                action = action,
                parameters = commandParameters,
                source = "android-app-functions"
            )
        )
        if (!accepted) {
            forgetAcceptedCommandId(commandId)
            callback.onError(
                AppFunctionException(
                    AppFunctionException.ERROR_CANCELLED,
                    "Futacha is busy. Open the app and try again."
                )
            )
            return
        }

        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching {
            startActivity(intent)
        }.onFailure { error ->
            Logger.w(TAG, "Failed to open MainActivity for AppFunction command: ${error.message}")
        }

        callback.onResult(
            ExecuteAppFunctionResponse(
                buildResultDocument(
                    reception = reception,
                    commandId = commandId,
                    status = reception.status,
                    message = reception.message,
                    duplicate = false
                )
            )
        )
    }

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
        }.getOrNull()?.trim()
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
        return trimmed.isNotBlank() && trimmed.encodeToByteArray().size <= APP_FUNCTION_COMMAND_ID_MAX_BYTES
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
        private const val APP_FUNCTION_ACCEPTED_COMMAND_ID_MAX_COUNT = 256
        private const val APP_FUNCTION_DUPLICATE_STATUS = "accepted_duplicate"
        private val acceptedCommandIdsLock = Any()
        private val acceptedCommandIds = LinkedHashSet<String>()
    }
}
