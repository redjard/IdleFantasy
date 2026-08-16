package com.fantasyidler.repository

import com.fantasyidler.data.json.PrestigeNodeData
import com.fantasyidler.data.json.PrestigeSkillTreeData
import com.fantasyidler.data.model.PlayerFlags
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure prestige-node effect math, callable without DI (composables, tests).
 *
 * Engine rule: for a given skill and effect key, the total is the SUM over the
 * skill's paths of the MAX purchased node value within each path. Node values are
 * therefore authored as totals per tier, and independent paths (e.g. a base path
 * plus a race branch) stack additively. Race-locked nodes only count while the
 * player's race matches. Prestige effects apply to ironman characters too: they
 * are earned through play, unlike purchased boosts.
 */
object PrestigeBoosts {

    /** Effect keys (see [PrestigeNodeData] docs for value semantics). */
    const val XP_PCT = "xp_pct"
    const val YIELD_PCT = "yield_pct"
    const val FLOW_RATE = "flow_rate"
    const val FLOW_INTERVAL_REDUCTION = "flow_interval_reduction"
    const val COMBAT_STAT_FLAT = "combat_stat_flat"
    const val SESSION_FLOOR_MIN = "session_floor_min"
    const val CAPE_SCALING = "cape_scaling"
    const val BONUS_ROLL_PCT = "bonus_roll_pct"
    const val COIN_PCT = "coin_pct"
    const val CROP_ROTATION_PCT = "crop_rotation_pct"
    const val CROP_ROTATION_ALWAYS = "crop_rotation_always"
    const val TOOL_EFF_PCT = "tool_eff_pct"
    const val SUCCESS_CHANCE_PCT = "success_chance_pct"
    const val RECLAIM_PCT = "reclaim_pct"
    const val HEAL_PCT = "heal_pct"
    const val DEATH_KEEP_PCT = "death_keep_pct"
    const val QUEUE_SLOT = "queue_slot"
    const val PET_BOOST_PCT = "pet_boost_pct"
    const val BLESSING_DURATION_PCT = "blessing_duration_pct"
    const val BLESSING_COST_PCT = "blessing_cost_pct"
    const val POTION_BONUS_FLAT = "potion_bonus_flat"
    const val INPUT_SAVE_PCT = "input_save_pct"
    const val BUILDER_DISCOUNT_PCT = "builder_discount_pct"
    const val SLAYER_POINTS_PCT = "slayer_points_pct"
    const val SELL_PRICE_PCT = "sell_price_pct"

    const val FLOW_BASE_INTERVAL_MIN = 60.0
    const val FLOW_CAP_PCT = 100.0

    /** Gameplay race key: lowercase, with unset (skipped setup) treated as human to match the sprite default. */
    fun playerRace(flags: PlayerFlags): String =
        flags.characterRace.lowercase().ifBlank { "human" }

    fun isNodeAvailableToRace(node: PrestigeNodeData, race: String): Boolean =
        node.race == null || node.race == race

    /** Purchased nodes of [skill] that are currently in force (race lock honored). */
    fun activeNodes(
        trees: Map<String, PrestigeSkillTreeData>,
        flags: PlayerFlags,
        skill: String,
    ): List<Pair<String, PrestigeNodeData>> {
        val tree = trees[skill] ?: return emptyList()
        val owned = flags.prestigeNodes[skill].orEmpty().toSet()
        if (owned.isEmpty()) return emptyList()
        val race = playerRace(flags)
        return tree.paths.flatMap { path ->
            path.nodes.filter { it.id in owned && isNodeAvailableToRace(it, race) }
                .map { path.key to it }
        }
    }

    /** Sum over paths of the max purchased value for [effect] in [skill]. 0.0 when nothing applies. */
    fun effectTotal(
        trees: Map<String, PrestigeSkillTreeData>,
        flags: PlayerFlags,
        skill: String,
        effect: String,
    ): Double = activeNodes(trees, flags, skill)
        .filter { (_, node) -> node.effect == effect }
        .groupBy({ (pathKey, _) -> pathKey }, { (_, node) -> node.value })
        .values
        .sumOf { valuesInPath -> valuesInPath.max() }

    /** Points spent on [skill]'s purchased nodes (race-mismatched nodes still count as spent). */
    fun spentPoints(trees: Map<String, PrestigeSkillTreeData>, flags: PlayerFlags, skill: String): Int {
        val tree = trees[skill] ?: return 0
        val owned = flags.prestigeNodes[skill].orEmpty().toSet()
        return tree.paths.sumOf { path -> path.nodes.filter { it.id in owned }.sumOf { it.cost } }
    }

    fun unspentPoints(trees: Map<String, PrestigeSkillTreeData>, flags: PlayerFlags, skill: String): Int =
        ((flags.prestigePointsEarned[skill] ?: 0) - spentPoints(trees, flags, skill)).coerceAtLeast(0)

    /** Skills that have race-locked tree branches, per race (human excluded: XP mastery everywhere). */
    fun raceProficiencies(trees: Map<String, PrestigeSkillTreeData>): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        for ((skill, tree) in trees) {
            for (path in tree.paths) {
                for (node in path.nodes) {
                    val race = node.race ?: continue
                    if (race == "human") continue
                    val list = result.getOrPut(race) { mutableListOf() }
                    if (skill !in list) list.add(skill)
                }
            }
        }
        return result
    }

    /** Lifetime point cap for [skill]: total cost of every node available to [race]. */
    fun pointCapForRace(tree: PrestigeSkillTreeData?, race: String): Int =
        tree?.paths?.sumOf { path ->
            path.nodes.filter { isNodeAvailableToRace(it, race) }.sumOf { it.cost }
        } ?: 0

    /** Cape-bonus scaling multiplier per skill (1 = unmodified), for [resolveCapeMultiplier]. */
    fun capeScalingBySkill(
        trees: Map<String, PrestigeSkillTreeData>,
        flags: PlayerFlags,
    ): Map<String, Int> = trees.keys.mapNotNull { skill ->
        val scaling = effectTotal(trees, flags, skill, CAPE_SCALING).toInt()
        if (scaling > 1) skill to scaling else null
    }.toMap()

    /** Flow-state yield multiplier for [elapsedMs] of continuous activity in [skill]. */
    fun flowMultiplier(
        trees: Map<String, PrestigeSkillTreeData>,
        flags: PlayerFlags,
        skill: String,
        elapsedMs: Long,
    ): Double {
        val rate = effectTotal(trees, flags, skill, FLOW_RATE)
        if (rate <= 0.0 || elapsedMs <= 0L) return 1.0
        val interval = (FLOW_BASE_INTERVAL_MIN - effectTotal(trees, flags, skill, FLOW_INTERVAL_REDUCTION))
            .coerceAtLeast(10.0)
        val intervals = (elapsedMs / 60_000.0 / interval).toInt()
        val pct = (intervals * rate).coerceAtMost(FLOW_CAP_PCT)
        return 1.0 + pct / 100.0
    }
}

/**
 * Single source of truth for every player-facing multiplier (v1.14.0 boost unification).
 *
 * Combines the 2x XP boost purchase, church blessings, and prestige-node effects,
 * with ironman zeroing handled here rather than at each call site. Pet boosts keep
 * their existing per-call plumbing (they are per-equipped-pet and session-specific).
 */
@Singleton
class BoostRepository @Inject constructor(
    private val gameData: GameDataRepository,
) {
    private val trees: Map<String, PrestigeSkillTreeData> get() = gameData.prestigeTrees

    fun effectTotal(skill: String, flags: PlayerFlags, effect: String): Double =
        PrestigeBoosts.effectTotal(trees, flags, skill, effect)

    /**
     * Combined XP multiplier for [skill]: 2x boost purchase, church blessing, and
     * prestige xp_pct nodes. Returns 1.0 for ironman.
     */
    fun xpMultiplier(skill: String, flags: PlayerFlags, now: Long = System.currentTimeMillis()): Double {
        val prestigeMult = 1.0 + effectTotal(skill, flags, PrestigeBoosts.XP_PCT) / 100.0
        // Prestige is earned, not bought, so it applies to ironmen too; the purchased
        // 2x boost and church XP blessings stay inert for them.
        if (flags.ironman) return prestigeMult
        val boostMult = if (flags.xpBoostExpiresAt > now) 2.0 else 1.0
        val blessingMult = ChurchRepository.xpMultiplier(flags).toDouble()
        return boostMult * blessingMult * prestigeMult
    }

    /** Item yield multiplier for [skill] from prestige yield_pct nodes. */
    fun yieldMultiplier(skill: String, flags: PlayerFlags): Double =
        1.0 + effectTotal(skill, flags, PrestigeBoosts.YIELD_PCT) / 100.0

    /** Flow-state multiplier for [elapsedMs] of continuous [skill] activity. */
    fun flowMultiplier(skill: String, flags: PlayerFlags, elapsedMs: Long): Double =
        PrestigeBoosts.flowMultiplier(trees, flags, skill, elapsedMs)

    /** Extra effective combat levels for [skill] (replaces the legacy +5 per prestige). */
    fun combatStatBonus(skill: String, flags: PlayerFlags): Int =
        effectTotal(skill, flags, PrestigeBoosts.COMBAT_STAT_FLAT).toInt()

    /** Minutes shaved off the level-99 session floor (agility Endurance nodes). */
    fun sessionFloorReductionMin(flags: PlayerFlags): Double =
        effectTotal(com.fantasyidler.data.model.Skills.AGILITY, flags, PrestigeBoosts.SESSION_FLOOR_MIN)

    /** Non-combat cape bonus multiplier (1 = unmodified; replaces legacy prestige scaling). */
    fun capeScalingMultiplier(skill: String, flags: PlayerFlags): Int =
        effectTotal(skill, flags, PrestigeBoosts.CAPE_SCALING).toInt().coerceAtLeast(1)

    /** Cape-bonus scaling per skill for [resolveCapeMultiplier] call sites. */
    fun capeScalingBySkill(flags: PlayerFlags): Map<String, Int> =
        PrestigeBoosts.capeScalingBySkill(trees, flags)

    /** Unspent prestige points per skill (skills with 0 unspent are omitted). */
    fun unspentPointsBySkill(flags: PlayerFlags): Map<String, Int> =
        trees.keys.mapNotNull { skill ->
            val unspent = PrestigeBoosts.unspentPoints(trees, flags, skill)
            if (unspent > 0) skill to unspent else null
        }.toMap()

    /** Prestige XP percent for [skill] alone (for display breakdowns). */
    fun prestigeXpPct(skill: String, flags: PlayerFlags): Int =
        effectTotal(skill, flags, PrestigeBoosts.XP_PCT).toInt()

    /** Multiplier on secondary drop chances (mining gem rolls). */
    fun bonusRollMultiplier(skill: String, flags: PlayerFlags): Double =
        1.0 + effectTotal(skill, flags, PrestigeBoosts.BONUS_ROLL_PCT) / 100.0

    /** Coin multiplier for [skill] session payouts from prestige coin_pct nodes. */
    fun coinMultiplier(skill: String, flags: PlayerFlags): Double =
        1.0 + effectTotal(skill, flags, PrestigeBoosts.COIN_PCT) / 100.0

    /** Farming yield bonus percent; [rotated] = the new crop differs from the patch's last one. */
    fun cropRotationBonusPct(flags: PlayerFlags, rotated: Boolean): Double {
        val always = effectTotal(com.fantasyidler.data.model.Skills.FARMING, flags, PrestigeBoosts.CROP_ROTATION_ALWAYS) > 0.0
        if (!rotated && !always) return 0.0
        return effectTotal(com.fantasyidler.data.model.Skills.FARMING, flags, PrestigeBoosts.CROP_ROTATION_PCT)
    }

    /**
     * Continuous-activity time credited to flow-state at collection: this session's
     * wall clock, extended by the streak of immediately preceding sessions of the
     * same skill in the recent activity log.
     */
    fun flowElapsedMs(flags: PlayerFlags, skill: String, sessionDurationMs: Long): Long {
        val streak = flags.recentSessions.takeWhile { it.skillName == skill }.count()
        return sessionDurationMs * (streak + 1)
    }

    /** Gathering tool efficiency multiplier for [skill] (pickaxe, axe, rod). */
    fun toolEffMultiplier(skill: String, flags: PlayerFlags): Float =
        (1.0 + effectTotal(skill, flags, PrestigeBoosts.TOOL_EFF_PCT) / 100.0).toFloat()

    /** Flat thieving success-chance bonus (fraction, e.g. 0.10). */
    fun thievingSuccessBonus(flags: PlayerFlags): Double =
        effectTotal(com.fantasyidler.data.model.Skills.THIEVING, flags, PrestigeBoosts.SUCCESS_CHANCE_PCT) / 100.0

    /** Extra arrow reclaim chance (ranged + fletching trees), as a fraction. */
    fun arrowReclaimBonus(flags: PlayerFlags): Double =
        (effectTotal(com.fantasyidler.data.model.Skills.RANGED, flags, PrestigeBoosts.RECLAIM_PCT) +
         effectTotal(com.fantasyidler.data.model.Skills.FLETCHING, flags, PrestigeBoosts.RECLAIM_PCT)) / 100.0

    /** Extra rune reclaim chance (magic + runecrafting trees), as a fraction. */
    fun runeReclaimBonus(flags: PlayerFlags): Double =
        (effectTotal(com.fantasyidler.data.model.Skills.MAGIC, flags, PrestigeBoosts.RECLAIM_PCT) +
         effectTotal(com.fantasyidler.data.model.Skills.RUNECRAFTING, flags, PrestigeBoosts.RECLAIM_PCT)) / 100.0

    /** Food heal values boosted by cooking + hitpoints heal nodes. */
    fun boostedFoodHeal(flags: PlayerFlags, healValues: Map<String, Int>): Map<String, Int> {
        val pct = effectTotal(com.fantasyidler.data.model.Skills.COOKING, flags, PrestigeBoosts.HEAL_PCT) +
            effectTotal(com.fantasyidler.data.model.Skills.HITPOINTS, flags, PrestigeBoosts.HEAL_PCT)
        if (pct <= 0.0) return healValues
        return healValues.mapValues { (_, v) -> (v * (1.0 + pct / 100.0)).toInt().coerceAtLeast(v) }
    }

    /** Fraction of XP/loot kept on combat death (base 0.10, defense + hitpoints nodes add). */
    fun deathKeepFraction(flags: PlayerFlags): Double =
        (0.10 + (effectTotal(com.fantasyidler.data.model.Skills.DEFENSE, flags, PrestigeBoosts.DEATH_KEEP_PCT) +
            effectTotal(com.fantasyidler.data.model.Skills.HITPOINTS, flags, PrestigeBoosts.DEATH_KEEP_PCT)) / 100.0)
            .coerceAtMost(0.60)

    /** Extra session queue slots from prestige (gnome construction capstone). */
    fun extraQueueSlots(flags: PlayerFlags): Int =
        effectTotal(com.fantasyidler.data.model.Skills.CONSTRUCTION, flags, PrestigeBoosts.QUEUE_SLOT).toInt()

    /** Pet boost percent for [skill], strengthened by pet_boost_pct nodes. */
    fun boostedPetPct(skill: String, flags: PlayerFlags, basePct: Int): Int {
        if (basePct <= 0) return basePct
        val pct = effectTotal(skill, flags, PrestigeBoosts.PET_BOOST_PCT)
        if (pct <= 0.0) return basePct
        return (basePct * (1.0 + pct / 100.0)).toInt().coerceAtLeast(basePct)
    }

    /** Blessing duration multiplier (prayer Devotion + elf Forest Grace). */
    fun blessingDurationMultiplier(flags: PlayerFlags): Double =
        1.0 + effectTotal(com.fantasyidler.data.model.Skills.PRAYER, flags, PrestigeBoosts.BLESSING_DURATION_PCT) / 100.0

    /** Blessing bone-cost multiplier (gnome Trickster's Favor), never below 1 bone. */
    fun blessingCostMultiplier(flags: PlayerFlags): Double =
        (1.0 - effectTotal(com.fantasyidler.data.model.Skills.PRAYER, flags, PrestigeBoosts.BLESSING_COST_PCT) / 100.0)
            .coerceAtLeast(0.5)

    /** Flat bonus added to every stat of an active combat potion (herblore Potent Potions). */
    fun potionBonusFlat(flags: PlayerFlags): Int =
        effectTotal(com.fantasyidler.data.model.Skills.HERBLORE, flags, PrestigeBoosts.POTION_BONUS_FLAT).toInt()

    /** Potion stat map with the herblore flat bonus applied (empty map stays empty). */
    fun boostedPotionEffects(flags: PlayerFlags, effects: Map<String, Int>): Map<String, Int> {
        val bonus = potionBonusFlat(flags)
        if (bonus <= 0 || effects.isEmpty()) return effects
        return effects.mapValues { (_, v) -> v + bonus }
    }

    /** Fraction of crafting inputs refunded for [skill] (smithing/crafting thrift + race branches). */
    fun inputSaveFraction(skill: String, flags: PlayerFlags): Double =
        (effectTotal(skill, flags, PrestigeBoosts.INPUT_SAVE_PCT) / 100.0).coerceAtMost(0.5)

    /** Extra builder discount in per-mille (construction Efficient Builder + dwarf Master Mason). */
    fun builderDiscountPerMille(flags: PlayerFlags): Int =
        (effectTotal(com.fantasyidler.data.model.Skills.CONSTRUCTION, flags, PrestigeBoosts.BUILDER_DISCOUNT_PCT) * 10).toInt()

    /** Slayer task point multiplier (slayer Bounty Hunter + orc Headhunter). */
    fun slayerPointsMultiplier(flags: PlayerFlags): Double =
        1.0 + effectTotal(com.fantasyidler.data.model.Skills.SLAYER, flags, PrestigeBoosts.SLAYER_POINTS_PCT) / 100.0

    /** Shop sell price multiplier (mercantile Trade Baron + dwarf Gold Sense). */
    fun sellPriceMultiplier(flags: PlayerFlags): Double =
        1.0 + effectTotal(com.fantasyidler.data.model.Skills.MERCANTILE, flags, PrestigeBoosts.SELL_PRICE_PCT) / 100.0

    /** All active node effects per skill, for the profile Bonuses tab: skill -> effect -> total. */
    fun activeEffectsBySkill(flags: PlayerFlags): Map<String, Map<String, Double>> =
        trees.keys.mapNotNull { skill ->
            val nodes = PrestigeBoosts.activeNodes(trees, flags, skill)
            if (nodes.isEmpty()) return@mapNotNull null
            val byEffect = nodes.map { (_, n) -> n.effect }.distinct().associateWith { effect ->
                PrestigeBoosts.effectTotal(trees, flags, skill, effect)
            }
            skill to byEffect
        }.toMap()
}
