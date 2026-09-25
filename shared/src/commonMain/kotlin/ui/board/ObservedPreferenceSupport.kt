package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

@Composable
internal fun <T : R, R> Flow<T>.collectPreferenceAsState(initial: R): State<R> =
    collectAsState(initial = (this as? SharedFlow<T>)?.replayCache?.lastOrNull() ?: initial)
