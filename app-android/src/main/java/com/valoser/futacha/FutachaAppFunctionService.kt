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
import com.valoser.futacha.shared.ai.describeFutachaAiCommandReception
import com.valoser.futacha.shared.ai.sanitizeFutachaAiCommandParameters
import com.valoser.futacha.shared.util.Logger

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
        val accepted = FutachaAiCommandBridge.enqueue(
            FutachaAiCommand(
                action = action,
                parameters = sanitizeFutachaAiCommandParameters(request.parameters.toStringParameters()),
                source = "android-app-functions"
            )
        )
        if (!accepted) {
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

        val result = GenericDocument.Builder<GenericDocument.Builder<*>>(
            "futacha",
            "ai-command-result",
            "FutachaAiCommandResult"
        )
            .setPropertyString("status", reception.status)
            .setPropertyString("action", reception.actionId)
            .setPropertyString("label", reception.actionLabel)
            .setPropertyString("risk", reception.risk.name)
            .setPropertyString("message", reception.message)
            .setPropertyBoolean("requiresConfirmation", reception.requiresConfirmation)
            .build()
        callback.onResult(ExecuteAppFunctionResponse(result))
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

    private companion object {
        private const val TAG = "FutachaAppFunctionService"
    }
}
