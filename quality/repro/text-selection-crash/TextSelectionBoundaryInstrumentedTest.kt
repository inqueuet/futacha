@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.valoser.futacha

import android.view.textclassifier.TextClassification
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextSelection
import androidx.compose.foundation.text.selection.PlatformSelectionBehaviorsImpl
import androidx.compose.foundation.text.selection.SelectedTextType
import androidx.compose.ui.text.TextRange
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Direct calls into the resolved Android dependency. These are boundary/fault-injection tests,
 * not reproductions of a user's screen or evidence that a device classifier returns bad output.
 */
class TextSelectionBoundaryInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var manager: TextClassificationManager
    private lateinit var original: TextClassifier
    private var suggestions = 0
    private var classifications = 0
    private var response: ((TextSelection.Request) -> TextSelection)? = null

    @Before fun setClassifier() {
        manager = context.getSystemService(TextClassificationManager::class.java)
        original = manager.textClassifier
        manager.setTextClassifier(object : TextClassifier {
            override fun suggestSelection(request: TextSelection.Request): TextSelection {
                suggestions++
                return response?.invoke(request)
                    ?: TextSelection.Builder(request.startIndex, request.endIndex).build()
            }
            override fun classifyText(request: TextClassification.Request): TextClassification {
                classifications++
                return TextClassification.Builder().build()
            }
        })
    }

    @After fun restoreClassifier() { manager.setTextClassifier(original) }

    private fun suggest(text: String, range: TextRange): TextRange? = runBlocking {
        PlatformSelectionBehaviorsImpl(Dispatchers.Default, context, SelectedTextType.EditableText, null)
            .suggestSelectionForLongPressOrDoubleClick(text, range)
    }

    @Test fun outOfBoundsThrowsBeforeClassifierRuns() {
        assertThrows(IllegalArgumentException::class.java) { suggest("abc", TextRange(1, 4)) }
        assertEquals(0, suggestions)
        assertEquals(0, classifications)
    }

    @Test fun emptyAndCollapsedRequestsAreSkipped() {
        assertNull(suggest("", TextRange(0, 4)))
        assertNull(suggest("abc", TextRange(1, 1)))
        assertEquals(0, suggestions)
    }

    @Test fun reversedValidSelectionIsNormalized() {
        assertEquals(TextRange(1, 3), suggest("abc", TextRange(3, 1)))
        assertEquals(1, suggestions)
        assertEquals(1, classifications)
    }

    @Test fun japaneseAndSurrogatePairUseUtf16Indices() {
        val text = "日本語😀末尾"
        assertEquals(7, text.length)
        assertEquals(TextRange(3, 5), suggest(text, TextRange(3, 5)))
        assertEquals(1, suggestions)
    }

    @Test fun oversizedClassifierOutputThrowsAtClassificationRequest() {
        response = { TextSelection.Builder(0, 99).build() }
        val error = assertThrows(IllegalArgumentException::class.java) {
            suggest("abc", TextRange(0, 1))
        }
        assertTrue(error.stackTrace.any { it.className.contains("TextClassification\$Request\$Builder") })
        assertEquals(1, suggestions)
        assertEquals(0, classifications)
    }

    @Test fun classifierExceptionIsNotConvertedToNull() {
        val expected = IllegalStateException("diagnostic classifier failure")
        response = { throw expected }
        assertSame(expected, assertThrows(IllegalStateException::class.java) {
            suggest("abc", TextRange(0, 1))
        })
    }
}
