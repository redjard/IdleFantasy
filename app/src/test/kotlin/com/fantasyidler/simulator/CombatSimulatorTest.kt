package com.fantasyidler.simulator

import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyCombatStats
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EnemyDefensiveStats
import com.fantasyidler.data.json.EnemySpawn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests for the most complex simulator, [CombatSimulator.simulateDungeon], made
 * deterministic through the injected `random` seam. Combat has too many moving
 * parts to assert exact per-tick damage, so this pins structural invariants
 * (frame count, non-negative HP, XP accrual) plus seeded reproducibility.
 */
class CombatSimulatorTest {

    private fun weakEnemy() = EnemyData(
        name = "rat",
        displayName = "Rat",
        hp = 1,
        combatStats = EnemyCombatStats(
            attackLevel = 1, strengthLevel = 1, defenseLevel = 1,
            attackBonus = 0, strengthBonus = 0,
        ),
        defensiveStats = EnemyDefensiveStats(
            attackDefense = 0, strengthDefense = 0, rangedDefense = 0, magicDefense = 0,
        ),
        // Combat XP per kill is read from the "combat" key (see CombatSimulator).
        xpDrops = mapOf("combat" to 20),
    )

    private fun dungeon(spawns: List<EnemySpawn>) = DungeonData(
        name = "farm",
        displayName = "Farm",
        description = "",
        recommendedLevel = 1,
        encounterRate = 1.0,
        enemySpawns = spawns,
    )

    /** A player strong enough to one-shot the weak enemy and never die. */
    private fun runStrongPlayer(seed: Int) = CombatSimulator.simulateDungeon(
        dungeon = dungeon(listOf(EnemySpawn("rat", 1))),
        enemies = mapOf("rat" to weakEnemy()),
        playerAttack = 99,
        playerStrength = 99,
        playerDefence = 99,
        playerHp = 99,
        weaponStrengthBonus = 64,
        random = Random(seed),
    )

    /** An unkillable wall so every melee swing targets a living enemy. */
    private fun tankyEnemy(defense: Int) = EnemyData(
        name = "golem",
        displayName = "Golem",
        hp = 1_000_000,
        combatStats = EnemyCombatStats(
            attackLevel = 1, strengthLevel = 1, defenseLevel = 1,
            attackBonus = 0, strengthBonus = 0,
        ),
        defensiveStats = EnemyDefensiveStats(
            attackDefense = defense, strengthDefense = defense, rangedDefense = defense, magicDefense = defense,
        ),
        xpDrops = mapOf("combat" to 20),
    )

    private fun totalMeleeDamage(seed: Int, defense: Int = 0, doubleHitChance: Double = 0.0, secondChance: Boolean = false) =
        CombatSimulator.simulateDungeon(
            dungeon = dungeon(listOf(EnemySpawn("golem", 1))),
            enemies = mapOf("golem" to tankyEnemy(defense)),
            playerAttack = 99,
            playerStrength = 99,
            playerDefence = 99,
            playerHp = 999,
            weaponStrengthBonus = 64,
            doubleHitChance = doubleHitChance,
            secondChance = secondChance,
            random = Random(seed),
        ).frames.sumOf { it.playerHits.sum() }

    @Test
    fun `a survivable dungeon run produces 60 frames`() {
        assertEquals(60, runStrongPlayer(seed = 1).frames.size)
    }

    @Test
    fun `guaranteed double hit roughly doubles melee damage`() {
        val base    = totalMeleeDamage(seed = 11)
        val doubled = totalMeleeDamage(seed = 11, doubleHitChance = 1.0)
        assertTrue("expected ~2x damage, got base=$base doubled=$doubled", doubled > base * 1.6)
    }

    @Test
    fun `second chance raises accuracy against a high-defense enemy`() {
        // Hit chance clamps to 0.15 here; one reroll lifts it to 1 - 0.85^2 = 0.2775.
        val base     = totalMeleeDamage(seed = 13, defense = 100_000)
        val rerolled = totalMeleeDamage(seed = 13, defense = 100_000, secondChance = true)
        assertTrue("expected ~1.85x damage, got base=$base rerolled=$rerolled", rerolled > base * 1.4)
    }

    @Test
    fun `the same seed reproduces an identical run`() {
        assertEquals(runStrongPlayer(seed = 42).frames, runStrongPlayer(seed = 42).frames)
    }

    @Test
    fun `a strong player accrues combat XP, scores kills, and never dies`() {
        val frames = runStrongPlayer(seed = 7).frames
        val totalXp = frames.sumOf { frame -> frame.xpBySkill.values.sum() }
        val totalKills = frames.sumOf { it.kills }

        assertTrue("expected positive combat XP", totalXp > 0)
        assertTrue("expected at least one kill", totalKills > 0)
        assertFalse("strong player should not die", frames.any { it.died })
    }

    @Test
    fun `recorded HP is never negative`() {
        assertTrue(runStrongPlayer(seed = 3).frames.all { it.hpAfter >= 0 })
    }

    @Test
    fun `the default attack speed still yields 25-tick frames`() {
        assertTrue(runStrongPlayer(seed = 5).frames.all { it.playerHits.size == 25 })
    }

    @Test
    fun `a fast ranged weapon consumes more than the legacy 1500-arrow cap`() {
        val result = CombatSimulator.simulateDungeon(
            dungeon = dungeon(listOf(EnemySpawn("rat", 1))),
            enemies = mapOf("rat" to weakEnemy()),
            playerAttack = 99,
            playerStrength = 99,
            playerDefence = 99,
            playerHp = 999,
            combatStyle = "ranged",
            playerRanged = 99,
            availableArrows = mapOf("bronze_arrow" to 10_000),
            attackSpeedSec = 1.5,
            random = Random(1),
        )
        assertEquals(60, result.frames.size)
        assertTrue(result.frames.all { it.playerHits.size == 40 })
        val totalArrows = result.frames.sumOf { it.arrowsConsumed.values.sum() }
        assertEquals(2_400, totalArrows)
    }

    @Test
    fun `a fast magic weapon consumes runes at the scaled tick rate`() {
        val result = CombatSimulator.simulateDungeon(
            dungeon = dungeon(listOf(EnemySpawn("rat", 1))),
            enemies = mapOf("rat" to weakEnemy()),
            playerAttack = 99,
            playerStrength = 99,
            playerDefence = 99,
            playerHp = 999,
            combatStyle = "magic",
            playerMagic = 99,
            spellMaxHit = 10,
            runeKey = "fire_rune",
            runeCostPerAttack = 2,
            availableRunes = 1_000_000,
            attackSpeedSec = 1.5,
            random = Random(2),
        )
        assertEquals(60, result.frames.size)
        val totalRunes = result.frames.sumOf { it.runesConsumed.values.sum() }
        assertEquals(60 * 40 * 2, totalRunes)
    }

    @Test
    fun `an empty spawn pool yields no frames but a valid duration`() {
        val result = CombatSimulator.simulateDungeon(
            dungeon = dungeon(emptyList()),
            enemies = emptyMap(),
            playerAttack = 10,
            playerStrength = 10,
            playerDefence = 10,
            agilityLevel = 1,
        )
        assertTrue(result.frames.isEmpty())
        assertEquals(SkillSimulator.sessionDurationMs(1), result.durationMs)
    }
}
