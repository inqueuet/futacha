package com.valoser.futacha.compat

import org.junit.Assert.assertEquals
import org.junit.Test

class CompatibilityBoardKeyRepairTablesTest {
    /**
     * Correcting a board URL deletes the old board row, and every board_key
     * table cascades on that delete. A table missing from the repair list
     * (as the new-thread drafts and dropped-thread records once were) loses
     * its rows for that board.
     */
    @Test
    fun boardKeyRepairMovesEveryBoardKeyedTable() {
        val createTable = Regex("""^CREATE TABLE(?: IF NOT EXISTS)? (\w+)\((.*)\)$""")
        val boardKeyColumn = Regex("""(?:^|,)\s*board_key\s""")
        val boardKeyed = CompatibilityDatabaseSchema.createStatements.mapNotNull { statement ->
            val match = createTable.find(statement.trim()) ?: return@mapNotNull null
            match.groupValues[1].takeIf { boardKeyColumn.containsMatchIn(match.groupValues[2]) }
        }.filterNot { it == "compat_board" }.toSet()

        assertEquals(boardKeyed, CompatibilityDatabaseSchema.boardKeyTables.toSet())
    }
}
