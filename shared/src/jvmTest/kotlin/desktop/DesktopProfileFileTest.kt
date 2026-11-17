package com.valoser.futacha.shared.desktop

import com.valoser.futacha.shared.compat.ExperienceProfile
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopProfileFileTest {
    @Test fun profileSwitchSurvivesABrieflyHeldProfileFile() {
        val directory = Files.createTempDirectory("futacha-profile").toFile()
        try {
            val profile = File(directory, "profile.txt").apply { writeText("${ExperienceProfile.FUTACHA.persistedValue}\n4\n") }
            var moves = 0
            writeDesktopProfileFile(profile, ExperienceProfile.TOSHIAKI_COMPAT, 5, move = { source, target, options ->
                if (++moves <= 2) throw AccessDeniedException(target.toString())
                Files.move(source, target, *options)
            })
            assertEquals(3, moves)
            assertEquals(listOf(ExperienceProfile.TOSHIAKI_COMPAT.persistedValue, "5"), profile.readLines())
            assertFalse(File(directory, "profile.pending").exists())
        } finally { directory.deleteRecursively() }
    }
}
