package com.fantasyidler.simulator

import org.junit.Assert.assertEquals
import org.junit.Test

class PrestigePointsTest {

    private val l99 = XpTable.xpForLevel(99)

    @Test
    fun `below level 99 earns nothing`() {
        assertEquals(0, PrestigePoints.pointsForXp(l99 - 1))
        assertEquals(0, PrestigePoints.pointsForXp(0))
    }

    @Test
    fun `level 99 earns exactly base points`() {
        assertEquals(2, PrestigePoints.pointsForXp(l99))
    }

    @Test
    fun `banked XP past 99 earns no extra points`() {
        assertEquals(2, PrestigePoints.pointsForXp(l99 + 10_000_000))
        assertEquals(2, PrestigePoints.pointsForXp(l99 + 100_000_000))
        assertEquals(2, PrestigePoints.pointsForXp(Long.MAX_VALUE / 2))
    }
}
