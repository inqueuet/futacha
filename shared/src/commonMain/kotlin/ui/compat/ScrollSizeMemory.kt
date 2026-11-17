package com.valoser.futacha.shared.ui.compat

/**
 * Remembers the measured size of every list item seen so far (App Store review 2026-11-16: the scroll bar grew and
 * shrank while scrolling because it was sized from the items on screen only). The average over everything measured
 * settles as the list is read and does not change again when scrolling back over measured items. A plain map: it is
 * written from the draw/derive phase and must not cause recomposition.
 */
internal class ScrollSizeMemory {
    private val sizes = HashMap<Int, Int>()
    private var sum = 0L
    private var lastTotal = -1

    /** Records [index] → [size] for the items now laid out; forgets everything when the list got shorter (reloaded). */
    fun record(totalItems: Int, items: Iterable<Pair<Int, Int>>) {
        if (totalItems < lastTotal) clear()
        lastTotal = totalItems
        for ((index, size) in items) {
            if (size <= 0) continue
            val old = sizes.put(index, size)
            sum += size - (old ?: 0)
        }
    }

    /** Mean size of the measured items, 0 when none. */
    fun averageSize(): Float = if (sizes.isEmpty()) 0f else sum.toFloat() / sizes.size

    fun clear() { sizes.clear(); sum = 0L }
}
