package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A hireable raid mercenary (assets/data/mercenaries.json).
 *
 * [combatStyle] only selects which boss defense their attacks are rolled against;
 * all styles share the melee max-hit formula, with [attackLevel] read as the
 * style's accuracy level. [hp] scales x10 in the simulator like the player's.
 */
@Serializable
data class MercenaryData(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val emoji: String,
    /** "cheap", "seasoned", or "elite" — display grouping only; stats carry the meaning. */
    val tier: String,
    @SerialName("combat_style") val combatStyle: String,
    @SerialName("attack_level") val attackLevel: Int,
    @SerialName("strength_level") val strengthLevel: Int,
    @SerialName("defense_level") val defenseLevel: Int,
    val hp: Int,
    @SerialName("attack_bonus") val attackBonus: Int,
    @SerialName("strength_bonus") val strengthBonus: Int,
    /** Coins for a contract lasting until the next daily reset. */
    @SerialName("hire_cost") val hireCost: Long,
)
