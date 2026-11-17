package com.valoser.futacha.shared.ui.util

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosBackGestureRegistryTest {
    private val calls = mutableListOf<String>()
    private val registered = mutableListOf<IosBackHandlerEntry>()

    @BeforeTest
    fun setUp() {
        IosBackGestureRegistry.rootViewProvider = { null }
    }

    @AfterTest
    fun tearDown() {
        registered.forEach(IosBackGestureRegistry::unregister)
    }

    private fun entry(name: String, enabled: Boolean, edge: Boolean = true) =
        IosBackHandlerEntry { calls += name }.also {
            it.enabled = enabled
            it.edgeGestureEnabled = edge
            IosBackGestureRegistry.register(it)
            registered += it
        }

    @Test
    fun lastRegisteredEnabledHandlerReceivesTheEdgeGesture() {
        entry("catalog", enabled = true)
        val settings = entry("settings", enabled = true)

        IosBackGestureRegistry.dispatch()
        assertEquals(listOf("settings"), calls)

        settings.enabled = false
        IosBackGestureRegistry.refresh()
        IosBackGestureRegistry.dispatch()
        assertEquals(listOf("settings", "catalog"), calls)
    }

    @Test
    fun enablingAnEarlierHandlerDoesNotMoveItAboveLaterOnes() {
        val catalog = entry("catalog", enabled = false)
        entry("overlay", enabled = true)
        catalog.enabled = true
        IosBackGestureRegistry.refresh()

        IosBackGestureRegistry.dispatch()
        assertEquals(listOf("overlay"), calls)
    }

    @Test
    fun topmostHandlerThatOptsOutOfTheEdgeGestureDisablesItInsteadOfFallingThrough() {
        entry("catalog", enabled = true)
        entry("drawerOwner", enabled = true, edge = false)

        assertFalse(IosBackGestureRegistry.edgeGestureWanted())
        IosBackGestureRegistry.dispatch()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun unregisteredHandlerIsNeverInvoked() {
        entry("catalog", enabled = true)
        val dialog = entry("dialog", enabled = true)
        IosBackGestureRegistry.unregister(dialog)

        IosBackGestureRegistry.dispatch()
        assertEquals(listOf("catalog"), calls)
    }
}
