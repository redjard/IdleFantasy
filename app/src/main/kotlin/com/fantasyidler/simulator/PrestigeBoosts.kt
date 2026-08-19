package com.fantasyidler.simulator

import com.fantasyidler.data.json.PrestigeNodeData
import com.fantasyidler.data.json.PrestigeSkillTreeData
import com.fantasyidler.data.model.PlayerFlags
import kotlin.collections.iterator

/**
 * Pure prestige-node effect math, callable without DI (composables, tests).
 *
 * Engine rule: for a given skill and effect key, the total is the SUM over the
 * skill's paths of the MAX purchased node value within each path. Node values are
 * therefore authored as totals per tier, and independent paths (e.g. a base path
 * plus a race branch) stack additively. Race-locked nodes only count while the
 * player's race is in the node's races list. Prestige effects apply to ironman characters too: they
 * are earned through play, unlike purchased boosts.
 */
object PrestigeBoosts {

    /** Effect keys (see [com.fantasyidler.data.json.PrestigeNodeData] docs for value semantics). */
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
    const val DOUBLE_HIT_PCT = "double_hit_pct"
    const val SECOND_CHANCE = "second_chance"
    const val FORETELL_SLOTS = "foretell_slots"
    const val SLAYER_MULTI_TASK = "slayer_multi_task"
    const val UNLOCK_RECIPE = "unlock_recipe"

    const val FLOW_BASE_INTERVAL_MIN = 60.0
    const val FLOW_CAP_PCT = 100.0

    /** Gameplay race key: lowercase, with unset (skipped setup) treated as human to match the sprite default. */
    fun playerRace(flags: PlayerFlags): String =
        flags.characterRace.lowercase().ifBlank { "human" }

    fun isNodeAvailableToRace(node: PrestigeNodeData, race: String): Boolean =
        node.races == null || race in node.races

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
                    val races = node.races ?: continue
                    races.forEach { race ->
                        if (race != "human") {
                            val list = result.getOrPut(race) { mutableListOf() }
                            if (skill !in list) list.add(skill)
                        }
                    }
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

    /** Cape-bonus scaling multiplier per skill (1 = unmodified), for [com.fantasyidler.repository.resolveCapeMultiplier]. */
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