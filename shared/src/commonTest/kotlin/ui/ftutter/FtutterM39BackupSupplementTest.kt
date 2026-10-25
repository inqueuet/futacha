package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FtutterM39BackupSupplementTest {
    @Test fun successfulImportReportsSavedSkippedAndDisplacedMarksSeparately() = runBlocking {
        val fs = InMemoryFileSystem()
        val repository = CookieRepository(PersistentCookieStorage(fs))
        repository.setCookie("https://may.2chan.net/", "ptmt", "keep", domain = "may.2chan.net")
        var markCalls = 0
        val result = ftutterApplyBackupSupplements("ptmt=replace; session=new", repository) { markCalls++; 5 }
        assertEquals(FtutterBackupSupplementResult(cookieSaved = 1, cookieSkipped = 1, droppedMarks = 5), result)
        assertEquals(1, markCalls)
        val notice = ftutterImportNotice(listOf("タブ 3件"), result.droppedMarks, result)
        assertTrue(notice.contains("Cookie 1件を保存"))
        assertTrue(notice.contains("同名の1件"))
        assertTrue(notice.contains("5件外しました"))
        assertTrue(notice.contains("タブ 3件"))
        assertFalse(notice.contains("replace"))
    }

    @Test fun cookieDiskFailureStillImportsMarksAndTheSameBackupRetriesPersistence() = runBlocking {
        val base = InMemoryFileSystem()
        var failed = true
        val fs = object : FileSystem by base {
            override suspend fun writeString(path: String, content: String): Result<Unit> =
                if (failed) Result.failure(IllegalStateException("test disk full")) else base.writeString(path, content)
        }
        val repository = CookieRepository(PersistentCookieStorage(fs))
        val importedMarks = mutableListOf("already-imported")
        val result = ftutterApplyBackupSupplements("ptmt=new", repository) {
            importedMarks += "123:456"
            0
        }
        assertTrue(result.cookieFailed)
        assertFalse(result.marksFailed)
        assertEquals(listOf("already-imported", "123:456"), importedMarks)
        val notice = ftutterImportNotice(emptyList(), 0, result)
        assertTrue(notice.startsWith("一部を取り込みました"))
        assertTrue(notice.contains("Cookieの取り込み・保存を完了できませんでした"))
        assertFalse(notice.contains("マークの取り込みを完了できませんでした"))
        val stillFailed = ftutterApplyBackupSupplements("ptmt=new", repository) {
            importedMarks += "123:457"
            0
        }
        assertTrue(stillFailed.cookieFailed)
        assertTrue(ftutterImportNotice(emptyList(), 0, stillFailed).startsWith("一部を"))
        assertEquals(listOf("already-imported", "123:456", "123:457"), importedMarks)
        failed = false
        val recovered = ftutterApplyBackupSupplements("ptmt=new", repository) {
            importedMarks += "123:458"
            0
        }
        assertFalse(recovered.cookieFailed)
        assertFalse(recovered.incomplete)
        assertEquals(1, recovered.cookieSkipped)
        assertTrue(ftutterImportNotice(emptyList(), 0, recovered).startsWith("取り込みました"))
        assertEquals(listOf("already-imported", "123:456", "123:457", "123:458"), importedMarks)
        assertEquals("new", CookieRepository(PersistentCookieStorage(base)).getCookiesFor("https://img.2chan.net/b/").single().value)
    }

    @Test fun unavailableCookieRepositoryStillRunsTheMarkStageAndReportsPartialCompletion() = runBlocking {
        var marks = false
        val result = ftutterApplyBackupSupplements("ptmt=x", null) { marks = true; 0 }
        assertTrue(marks)
        assertTrue(result.cookieFailed)
        assertTrue(ftutterImportNotice(emptyList(), 0, result).startsWith("一部を"))
    }

    @Test fun aMarkFailureDoesNotEraseSuccessfullyImportedCookiesOrReportFullSuccess() = runBlocking {
        val fs = InMemoryFileSystem()
        val repository = CookieRepository(PersistentCookieStorage(fs))
        val result = ftutterApplyBackupSupplements("ptmt=new", repository) { error("mark write failed") }
        assertEquals(1, result.cookieSaved)
        assertTrue(result.marksFailed)
        assertFalse(result.cookieFailed)
        assertEquals("new", CookieRepository(PersistentCookieStorage(fs)).getCookiesFor("https://may.2chan.net/b/").single().value)
        assertTrue(ftutterImportNotice(emptyList(), 0, result).contains("マークの取り込みを完了できませんでした"))
    }

    @Test fun cancellationDuringCookieImportPropagatesAndDoesNotStartMarks() = runBlocking<Unit> {
        val base = InMemoryFileSystem()
        val fs = object : FileSystem by base {
            override suspend fun exists(path: String): Boolean = throw CancellationException("test cancellation")
        }
        var marks = false
        assertFailsWith<CancellationException> {
            ftutterApplyBackupSupplements("ptmt=x", CookieRepository(PersistentCookieStorage(fs))) { marks = true; 0 }
        }
        assertFalse(marks)
    }

    @Test fun cancellationDuringMarksPropagatesAfterTheCompletedCookieImport() = runBlocking<Unit> {
        val fs = InMemoryFileSystem()
        val repository = CookieRepository(PersistentCookieStorage(fs))
        assertFailsWith<CancellationException> {
            ftutterApplyBackupSupplements("ptmt=x", repository) { throw CancellationException("test cancellation") }
        }
        assertEquals("x", CookieRepository(PersistentCookieStorage(fs)).getCookiesFor("https://may.2chan.net/b/").single().value)
    }

    @Test fun noValidCookieLeavesTheOriginalSuccessMessageAndStillImportsMarks() = runBlocking {
        for (header in listOf(null, "", "invalid")) {
            var marks = false
            val result = ftutterApplyBackupSupplements(header, null) { marks = true; 0 }
            assertTrue(marks)
            assertEquals(FtutterBackupSupplementResult(), result)
            assertEquals("取り込みました", ftutterImportNotice(emptyList(), 0, result))
        }
    }

    @Test fun allCookieConflictsAreReportedWithoutChangingExistingValues() = runBlocking {
        val repository = CookieRepository(PersistentCookieStorage(InMemoryFileSystem()))
        repository.setCookie("https://may.2chan.net/", "ptmt", "keep", domain = "may.2chan.net")
        val result = ftutterApplyBackupSupplements("ptmt=replace", repository) { 0 }
        assertEquals(FtutterBackupSupplementResult(cookieSkipped = 1), result)
        assertEquals("keep", repository.getCookiesFor("https://may.2chan.net/b/").single().value)
        assertTrue(ftutterImportNotice(emptyList(), 0, result).contains("同名の1件"))
    }
}
