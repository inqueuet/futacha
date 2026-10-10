package com.valoser.futacha.shared.compat

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val MANUAL_POST_MARKS_KEY = "compat.manualPostMarks"
private val markJson = Json { ignoreUnknownKeys = true }
private val markMutex = Mutex()
private val markPostNoPattern = Regex("[0-9]{1,20}")

@Serializable
data class ManualPostMark(val threadUrl: String, val postNo: String)

fun manualMarkThreadUrl(url: String): String = canonicalizeThreadUrl(url)?.canonicalUrl ?: url.substringBefore('#').substringBefore('?')
fun decodeManualPostMarks(raw: String?): List<ManualPostMark> = raw?.takeIf { it.length <= 18_000 }?.let {
    runCatching { markJson.decodeFromString<List<ManualPostMark>>(it) }.getOrNull()
}?.filter { it.postNo.matches(markPostNoPattern) && it.threadUrl.length <= 240 }
    ?.distinct()?.take(120).orEmpty()

/** The explicit state avoids racing two toggles from different screens; automatic own-post markers are untouched. */
suspend fun setManualPostMark(store: CompatibilityStore, url: String, postNo: String, marked: Boolean) {
    val mark = ManualPostMark(manualMarkThreadUrl(url), postNo)
    require(mark.postNo.matches(markPostNoPattern) && mark.threadUrl.length <= 240)
    store.isLoaded.first { it }
    markMutex.withLock {
        val previous = decodeManualPostMarks(store.preferences.first()[MANUAL_POST_MARKS_KEY]).filterNot { it == mark }
        val next = (if (marked) listOf(mark) + previous else previous).take(120).toMutableList()
        var encoded = markJson.encodeToString(next)
        while (encoded.length > 18_000 && next.isNotEmpty()) { next.removeAt(next.lastIndex); encoded = markJson.encodeToString(next) }
        store.savePreference(MANUAL_POST_MARKS_KEY, encoded)
    }
}
