package com.fantasyidler.data.json

import kotlinx.serialization.Serializable

/**
 * One purchasable node in a prestige skill tree (assets/data/prestige_paths.json).
 *
 * Effect keys and how [value] is interpreted (all values are the TOTAL granted at
 * that node, not an increment — the engine takes the max purchased value per path):
 *  • xp_pct                  — +value% XP for this skill
 *  • yield_pct               — +value% items for this skill
 *  • flow_rate               — +value% yield per flow interval of continuous activity
 *  • flow_interval_reduction — flow interval shortened by value minutes (base 60)
 *  • combat_stat_flat        — +value effective levels for this combat stat
 *  • session_floor_min       — level-99 session floor reduced by value minutes
 *  • cape_scaling            — non-combat cape bonus multiplied by value
 *  • bonus_roll_pct          — secondary drop chance (mining gems) +value%
 *  • coin_pct                — +value% coins from this skill's sessions
 *  • crop_rotation_pct       — +value% farming yield when planting a different crop
 *  • crop_rotation_always    — rotation bonus applies even without rotating
 */
@Serializable
data class PrestigeNodeData(
    val id: String,
    val cost: Int,
    val effect: String,
    val value: Double = 0.0,
    /** Lowercase race key ("human", "elf", ...) this node is locked to, or null for all races. */
    val race: String? = null,
)

/** An ordered tier chain: each node requires the previous node in the same path. */
@Serializable
data class PrestigePathData(
    val key: String,
    val nodes: List<PrestigeNodeData>,
)

@Serializable
data class PrestigeSkillTreeData(
    val skill: String,
    val paths: List<PrestigePathData>,
)
