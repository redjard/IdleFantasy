package com.fantasyidler.simulator

/**
 * Pure prestige-point economy math (v1.14.0 prestige overhaul).
 *
 * Prestiging a level-99 skill always awards exactly [BASE_POINTS]. XP banked
 * past level 99 earns nothing extra (playtest decision: only prestiging pays).
 */
object PrestigePoints {

    const val BASE_POINTS = 2

    /** Points granted per legacy prestige level in the one-time v1.14.0 migration. */
    const val LEGACY_POINTS_PER_LEVEL = 2

    private val LEVEL_99_XP = XpTable.xpForLevel(99)

    /** Points awarded for prestiging a skill holding [xp] total XP (0 if below level 99). */
    fun pointsForXp(xp: Long): Int = if (xp < LEVEL_99_XP) 0 else BASE_POINTS
}
