package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class PostMarkContext(val store: CompatibilityStore, val threadUrl: String,
    posts: List<Pair<String, String>>, val onShowPost: (String) -> Unit) {
    val posts = posts.distinctBy { it.first }
}
internal val LocalManualPostMarkNos = compositionLocalOf<Set<String>> { emptySet() }
internal val LocalPostMarkContext = compositionLocalOf<PostMarkContext?> { null }

@Composable
internal fun ManualPostMarkActions(postNo: String, context: PostMarkContext? = LocalPostMarkContext.current) {
    if (context == null) return
    val preferences by context.store.preferences.collectAsState(emptyMap())
    val marked = remember(preferences[MANUAL_POST_MARKS_KEY], context.threadUrl, postNo) {
        ManualPostMark(manualMarkThreadUrl(context.threadUrl), postNo) in decodeManualPostMarks(preferences[MANUAL_POST_MARKS_KEY])
    }
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(enabled = !busy, onClick = { busy = true; scope.launch {
            try { setManualPostMark(context.store, context.threadUrl, postNo, !marked) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure = "マークを保存できませんでした" }
            finally { busy = false }
        } }) { Text(if (marked) "★ マーク解除" else "☆ レスをマーク") }
        TextButton(onClick = { open = true }) { Text("マーク一覧") }
    }
    failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (open) ManualPostMarkDialog(context, postNo, onDismiss = { open = false })
}

@Composable
internal fun ManualPostMarkDialog(context: PostMarkContext, currentPostNo: String? = null, onDismiss: () -> Unit) {
    val preferences by context.store.preferences.collectAsState(emptyMap())
    val threadUrl = manualMarkThreadUrl(context.threadUrl)
    val marked = remember(preferences[MANUAL_POST_MARKS_KEY], threadUrl) {
        decodeManualPostMarks(preferences[MANUAL_POST_MARKS_KEY]).filter { it.threadUrl == threadUrl }.map { it.postNo }.toSet()
    }
    val posts = context.posts.filter { it.first in marked }
    val currentIndex = context.posts.indexOfFirst { it.first == currentPostNo }
    val previous = context.posts.take(currentIndex.coerceAtLeast(0)).lastOrNull { it.first in marked }
    val next = context.posts.drop((currentIndex + 1).coerceAtLeast(0)).firstOrNull { it.first in marked }
    fun jump(no: String) { onDismiss(); context.onShowPost(no) }
    FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = onDismiss, title = { Text("レスマーク（${marked.size}件）") }, text = {
        Column {
            Text("この端末の3モードで共有します。他の端末とは同期しません。")
            Row {
                TextButton(enabled = previous != null, onClick = { previous?.let { jump(it.first) } }) { Text("前のマーク") }
                TextButton(enabled = next != null, onClick = { next?.let { jump(it.first) } }) { Text("次のマーク") }
            }
            if (posts.isEmpty()) Text("この表示にマークしたレスはありません")
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(posts, key = { it.first }) { (no, body) ->
                    TextButton(onClick = { jump(no) }) { Text("★ No.$no  ${body.toCompatPlainText().take(100)}", maxLines = 3) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }) }
}
