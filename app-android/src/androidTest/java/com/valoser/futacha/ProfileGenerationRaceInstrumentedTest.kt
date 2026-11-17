package com.valoser.futacha

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.compat.AndroidExperienceProfileStore
import com.valoser.futacha.compat.AndroidLauncherAliasReconciler
import com.valoser.futacha.compat.AndroidModeSwitchCoordinator
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.ExperienceProfileResultGate
import com.valoser.futacha.shared.compat.ExperienceProfileUiController
import com.valoser.futacha.shared.compat.ModeSwitchPhase
import com.valoser.futacha.shared.model.AppIconVariant
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileGenerationRaceInstrumentedTest {
    private lateinit var context: Context
    private val preferenceNames = mutableSetOf<String>()
    private val markerFiles = mutableSetOf<File>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun pendingThreadNavigationSurvivesStoreRecreationUntilAcknowledged() {
        val preferencesName = newPreferencesName("pending_navigation")
        val threadUrl = "https://may.2chan.net/b/res/1431342021.htm"
        AndroidExperienceProfileStore(context, preferencesName).savePendingThreadNavigation(
            url = threadUrl,
            target = ExperienceProfile.TOSHIAKI_COMPAT
        )

        val restored = AndroidExperienceProfileStore(context, preferencesName)
        assertNull(restored.readPendingThreadNavigation(ExperienceProfile.FUTACHA))
        assertEquals(
            threadUrl,
            restored.readPendingThreadNavigation(ExperienceProfile.TOSHIAKI_COMPAT)
        )

        restored.clearPendingThreadNavigation()
        assertNull(restored.readPendingThreadNavigation(ExperienceProfile.TOSHIAKI_COMPAT))
    }

    @After
    fun tearDown() {
        markerFiles.forEach(File::delete)
        preferenceNames.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test(timeout = 120_000L)
    fun oneHundredSwitchesSerializeHistoryAndRejectSaveAndReturnedActivityResult() = runBlocking {
        val preferencesName = newPreferencesName("race")
        val store = AndroidExperienceProfileStore(context, preferencesName)
        val reconciledProfiles = Collections.synchronizedList(mutableListOf<ExperienceProfile>())
        val coordinator = coordinator(store, reconciledProfiles)
        val historyCommits = AtomicInteger()
        val saveCommits = AtomicInteger()
        val newGenerationCommits = AtomicInteger()

        repeat(100) { iteration ->
            val oldProfile = store.readActiveProfile()
            val target = if (oldProfile == ExperienceProfile.FUTACHA) {
                ExperienceProfile.TOSHIAKI_COMPAT
            } else {
                ExperienceProfile.FUTACHA
            }
            val oldGeneration = store.readGeneration()
            val oldController = ExperienceProfileUiController(
                isAvailable = true,
                activeProfile = oldProfile,
                sessionGeneration = oldGeneration,
                isSessionActive = true,
                isSessionAuthoritativelyCurrent = { token ->
                    store.isGenerationCommitAllowed(token.profile, token.generation)
                }
            )
            val returnedActivityResult = ExperienceProfileResultGate().apply {
                markLaunched(oldController)
            }
            val historyEnteredBarrier = CompletableDeferred<Unit>()
            val releaseHistoryCommit = CompletableDeferred<Unit>()
            val quiesceEntered = CompletableDeferred<Unit>()
            val releaseQuiesce = CompletableDeferred<Unit>()

            val historyJob = async(Dispatchers.Default) {
                store.runIfGenerationCurrent(oldProfile, oldGeneration) {
                    historyEnteredBarrier.complete(Unit)
                    releaseHistoryCommit.await()
                    historyCommits.incrementAndGet()
                }
            }
            historyEnteredBarrier.await()
            val switchJob = async(Dispatchers.Default) {
                coordinator.switchTo(target, AppIconVariant.Current) {
                    quiesceEntered.complete(Unit)
                    releaseQuiesce.await()
                }.getOrThrow()
            }

            // The switch is now queued behind the already-authorized history commit.
            releaseHistoryCommit.complete(Unit)
            assertTrue(historyJob.await())
            quiesceEntered.await()
            assertEquals(ModeSwitchPhase.SESSION_FLUSHED, store.readJournal()?.phase)

            // These are the actual callback shape supplied to history auto-save/Worker and
            // to every Activity Result launcher. Once the journal exists, none may commit.
            val rejectedSaveAttempts = List(8) {
                async(Dispatchers.Default) {
                    store.runIfGenerationCurrent(oldProfile, oldGeneration) {
                        saveCommits.incrementAndGet()
                    }
                }
            }.awaitAll()
            assertTrue(rejectedSaveAttempts.all { accepted -> !accepted })
            assertNull(returnedActivityResult.consumeIfCurrent(oldController))

            releaseQuiesce.complete(Unit)
            val committedGeneration = switchJob.await()
            assertEquals(oldGeneration + 1L, committedGeneration)
            assertEquals(target, store.readActiveProfile())
            assertEquals(committedGeneration, store.readGeneration())
            assertNull(store.readJournal())

            assertFalse(store.runIfGenerationCurrent(oldProfile, oldGeneration) {})
            assertTrue(
                store.runIfGenerationCurrent(target, committedGeneration) {
                    newGenerationCommits.incrementAndGet()
                }
            )
            assertEquals(iteration + 1, historyCommits.get())
            assertEquals(0, saveCommits.get())
            assertEquals(iteration + 1, newGenerationCommits.get())
        }

        assertEquals(100, reconciledProfiles.size)
        assertEquals(store.readActiveProfile(), reconciledProfiles.last())
        assertEquals(100L, store.readGeneration())
    }

    @Test(timeout = 30_000L)
    fun aNewRequestRecoversTheOldJournalBeforeStartingAnotherGeneration() = runBlocking {
        val store = AndroidExperienceProfileStore(context, newPreferencesName("journal_first"))
        var journal = store.beginSwitchWithCommitBarrier(
            ExperienceProfile.FUTACHA,
            ExperienceProfile.TOSHIAKI_COMPAT
        )
        journal = store.advanceSwitch(journal, ModeSwitchPhase.OLD_PROFILE_QUIESCED)
        val reconciledProfiles = mutableListOf<ExperienceProfile>()
        val coordinator = coordinator(store, reconciledProfiles)

        val generation = coordinator.switchTo(
            ExperienceProfile.FUTACHA,
            AppIconVariant.Current
        ).getOrThrow()

        assertEquals(2L, generation)
        assertEquals(ExperienceProfile.FUTACHA, store.readActiveProfile())
        assertNull(store.readJournal())
        assertEquals(
            listOf(ExperienceProfile.TOSHIAKI_COMPAT, ExperienceProfile.FUTACHA),
            reconciledProfiles
        )
    }

    @Test(timeout = 30_000L)
    fun completedProfileStartupDoesNotForceLauncherIconAgain() = runBlocking {
        val store = AndroidExperienceProfileStore(context, newPreferencesName("icon_persistence"))
        val reconciledProfiles = mutableListOf<ExperienceProfile>()
        val coordinator = coordinator(store, reconciledProfiles)
        coordinator.switchTo(ExperienceProfile.TOSHIAKI_COMPAT, AppIconVariant.Current).getOrThrow()
        assertEquals(listOf(ExperienceProfile.TOSHIAKI_COMPAT), reconciledProfiles)

        reconciledProfiles.clear()
        assertEquals(ExperienceProfile.TOSHIAKI_COMPAT, coordinator.recoverIfNeeded().getOrThrow())
        assertTrue(reconciledProfiles.isEmpty())
    }

    @Test(timeout = 60_000L)
    fun everyDurableJournalPhaseRecoversAfterRealSeparateProcessDeath() = runBlocking {
        val crashPhases = listOf(
            ModeSwitchPhase.SESSION_FLUSHED,
            ModeSwitchPhase.OLD_PROFILE_QUIESCED,
            ModeSwitchPhase.PROFILE_PERSISTED,
            ModeSwitchPhase.LAUNCHER_ALIAS_UPDATED
        )
        crashPhases.forEachIndexed { index, phase ->
            val preferencesName = newPreferencesName("death_${phase.name.lowercase()}")
            val marker = newMarker("profile_death_${index}")
            val serviceIntent = Intent().setClassName(
                context.packageName,
                "com.valoser.futacha.ProfileSwitchCrashTestService"
            ).putExtra(ProfileSwitchCrashTestService.EXTRA_PREFERENCES_NAME, preferencesName)
                .putExtra(ProfileSwitchCrashTestService.EXTRA_PHASE, phase.name)
                .putExtra(ProfileSwitchCrashTestService.EXTRA_MARKER_NAME, marker.name)
            assertTrue(context.startService(serviceIntent) != null)
            waitFor(marker, timeoutMillis = 8_000L)
            assertTrue("Fault injector failed: ${marker.readText()}", !marker.readText().startsWith("error="))
            delay(1_500L)

            // This is the first open of the isolated preference namespace in this process,
            // equivalent to Application bootstrap after the process which wrote it died.
            val recoveredStore = AndroidExperienceProfileStore(context, preferencesName)
            val reconciledProfiles = mutableListOf<ExperienceProfile>()
            val recovered = coordinator(recoveredStore, reconciledProfiles)
                .recoverIfNeeded()
                .getOrThrow()

            assertEquals(ExperienceProfile.TOSHIAKI_COMPAT, recovered)
            assertEquals(ExperienceProfile.TOSHIAKI_COMPAT, recoveredStore.readActiveProfile())
            assertEquals(1L, recoveredStore.readGeneration())
            assertNull(recoveredStore.readJournal())
            assertEquals(listOf(ExperienceProfile.TOSHIAKI_COMPAT), reconciledProfiles)
        }
    }

    @Test(timeout = 30_000L)
    fun realExternalActivityResultIsDeliveredWhenCurrentAndDroppedAfterSwitch() = runBlocking {
        val currentPreferences = newPreferencesName("activity_current")
        val currentLaunch = newMarker("activity_current_launched")
        val currentDelivery = newMarker("activity_current_delivered", create = false)
        startActivityResultScenario(currentPreferences, currentLaunch, currentDelivery, 500L)
        waitFor(currentLaunch, 5_000L)
        waitFor(currentDelivery, 5_000L)
        assertTrue(currentDelivery.readText().contains("result=${android.app.Activity.RESULT_OK}"))
        delay(500L)

        val stalePreferences = newPreferencesName("activity_stale")
        val staleLaunch = newMarker("activity_stale_launched")
        val staleDelivery = newMarker("activity_stale_delivered", create = false)
        val staleStore = AndroidExperienceProfileStore(context, stalePreferences)
        startActivityResultScenario(stalePreferences, staleLaunch, staleDelivery, 2_000L)
        waitFor(staleLaunch, 5_000L)
        coordinator(staleStore, mutableListOf()).switchTo(
            ExperienceProfile.TOSHIAKI_COMPAT,
            AppIconVariant.Current
        ).getOrThrow()
        delay(2_500L)
        assertFalse("Old-profile Activity Result reached its callback", staleDelivery.exists())
    }

    private fun coordinator(
        store: AndroidExperienceProfileStore,
        reconciledProfiles: MutableList<ExperienceProfile>
    ): AndroidModeSwitchCoordinator = AndroidModeSwitchCoordinator(
        profileStore = store,
        aliasReconciler = AndroidLauncherAliasReconciler { profile, _ ->
            reconciledProfiles += profile
        }
    )

    private fun startActivityResultScenario(
        preferencesName: String,
        launchedMarker: File,
        deliveredMarker: File,
        delayMillis: Long
    ) {
        context.startActivity(
            Intent().setClassName(
                context.packageName,
                "com.valoser.futacha.ProfileActivityResultGateTestActivity"
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ProfileActivityResultGateTestActivity.EXTRA_PREFERENCES_NAME, preferencesName)
                .putExtra(ProfileActivityResultGateTestActivity.EXTRA_LAUNCHED_MARKER, launchedMarker.name)
                .putExtra(ProfileActivityResultGateTestActivity.EXTRA_DELIVERED_MARKER, deliveredMarker.name)
                .putExtra(ProfileActivityResultGateTestActivity.EXTRA_RESULT_DELAY_MILLIS, delayMillis)
        )
    }

    private fun newPreferencesName(label: String): String =
        "profile_generation_${label}_${System.nanoTime()}".also(preferenceNames::add)

    private fun newMarker(label: String, create: Boolean = true): File =
        context.noBackupFilesDir.resolve("${label}_${System.nanoTime()}.marker").also { file ->
            file.delete()
            if (create) {
                // Most markers are created by the target component. `create` exists only to
                // make intent explicit at call sites; never pre-create or waits would be false.
            }
            markerFiles += file
        }

    private suspend fun waitFor(file: File, timeoutMillis: Long) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (!file.exists() && SystemClock.elapsedRealtime() < deadline) delay(20L)
        assertTrue("Timed out waiting for ${file.name}", file.exists())
    }
}
