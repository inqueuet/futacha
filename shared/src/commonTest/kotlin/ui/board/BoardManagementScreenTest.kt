package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals

class BoardManagementScreenTest {
    @Test
    fun incrementSaidaneLabel_handlesNullOrBlank() {
        assertEquals("そうだねx1", incrementSaidaneLabel(null))
        assertEquals("そうだねx1", incrementSaidaneLabel(""))
        assertEquals("そうだねx1", incrementSaidaneLabel("+"))
    }

    @Test
    fun incrementSaidaneLabel_incrementsNumericSuffix() {
        assertEquals("そうだねx2", incrementSaidaneLabel("そうだねx1"))
        assertEquals("そうだねx6", incrementSaidaneLabel("そうだねx5"))
    }
}
