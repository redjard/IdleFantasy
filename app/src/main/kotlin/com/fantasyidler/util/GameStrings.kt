package com.fantasyidler.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.fantasyidler.R
import com.fantasyidler.simulator.PrestigeBoosts

/**
 * Central resolver for game-content display strings.
 *
 * All user-visible game content names and descriptions live in the strings_*.xml resource
 * files so Weblate can manage translations. JSON data files contain only internal snake_case
 * keys — they are never displayed directly. This object converts a key to its localised name.
 *
 * Naming convention in XML: {domain}_{key}_{role}
 *   e.g. item_iron_ore_name, skill_mining_desc, dungeon_dark_cave_name
 *
 * If a string resource is missing (e.g. a new item added before its translation is merged),
 * the key is title-cased as a readable fallback rather than crashing or showing a blank.
 */
object GameStrings {

    fun itemName(context: Context, key: String): String =
        context.stringByName("item_${key}_name")
            ?: context.stringByName("crop_${key}_name")
            ?: key.toTitleCase()

    fun itemDesc(context: Context, key: String): String =
        context.stringByName("item_${key}_desc")
            ?: context.stringByName("crop_${key}_desc")
            ?: ""

    fun skillName(context: Context, key: String): String =
        context.stringByName("skill_${key}_name") ?: key.toTitleCase()

    fun skillDesc(context: Context, key: String): String =
        context.stringByName("skill_${key}_desc") ?: ""

    fun dungeonName(context: Context, key: String): String =
        context.stringByName("dungeon_${key}_name") ?: key.toTitleCase()

    fun dungeonDesc(context: Context, key: String): String =
        context.stringByName("dungeon_${key}_desc") ?: ""

    fun skillingDungeonName(context: Context, key: String, fallback: String): String =
        context.stringByName("skilling_dungeon_${key}_name") ?: fallback

    fun skillingDungeonDesc(context: Context, key: String, fallback: String): String =
        context.stringByName("skilling_dungeon_${key}_desc") ?: fallback

    /** Lore note [index] (0-based) for a skilling dungeon; falls back to the JSON text. */
    fun skillingDungeonNote(context: Context, key: String, index: Int, fallback: String): String =
        context.stringByName("skilling_dungeon_${key}_note_${index + 1}") ?: fallback

    fun enemyName(context: Context, key: String): String =
        context.stringByName("enemy_${key}_name") ?: key.toTitleCase()

    fun bossName(context: Context, key: String): String =
        context.stringByName("boss_${key}_name") ?: key.toTitleCase()

    fun bossDesc(context: Context, key: String): String =
        context.stringByName("boss_${key}_desc") ?: ""

    fun slotName(context: Context, slot: String): String =
        context.stringByName("equip_slot_$slot") ?: slot.toTitleCase()

    fun questName(context: Context, key: String, fallback: String = key.toTitleCase()): String =
        context.stringByName("quest_${key}_name") ?: fallback

    fun questDesc(context: Context, key: String): String =
        context.stringByName("quest_${key}_desc") ?: ""

    fun questObjective(context: Context, key: String): String =
        context.stringByName("quest_${key}_objective") ?: ""

    fun petName(context: Context, key: String): String =
        context.stringByName("pet_${key}_name") ?: key.toTitleCase()

    fun petDesc(context: Context, key: String): String =
        context.stringByName("pet_${key}_desc") ?: ""

    fun spellName(context: Context, key: String): String =
        context.stringByName("spell_${key}_name") ?: key.toTitleCase()

    fun cropName(context: Context, key: String): String =
        context.stringByName("crop_${key}_name") ?: key.toTitleCase()

    fun tradeRouteName(context: Context, id: String, fallback: String = id.toTitleCase()): String =
        context.stringByName("trade_route_${id}_name") ?: fallback

    fun tradeRouteDesc(context: Context, id: String, fallback: String = ""): String =
        context.stringByName("trade_route_${id}_desc") ?: fallback

    fun craftingCategory(context: Context, raw: String): String {
        val resId = context.resources.getIdentifier(
            "crafting_cat_${raw.lowercase()}", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else raw
    }

    fun craftingTier(context: Context, raw: String): String {
        val resId = context.resources.getIdentifier(
            "crafting_tier_${raw.lowercase()}", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else raw
    }

    fun boneName(context: Context, key: String): String =
        context.stringByName("bone_${key}_name") ?: key.toTitleCase()

    fun treeName(context: Context, key: String, fallback: String = key.toTitleCase()): String =
        context.stringByName("tree_${key}_name") ?: fallback

    /** Localised activity name; each skill keys its activities in a different string domain. */
    fun activityName(context: Context, skillName: String, activityKey: String): String = when (skillName) {
        "combat"      -> dungeonName(context, activityKey)
        "boss"        -> bossName(context, activityKey)
        "mercantile"  -> tradeRouteName(context, activityKey)
        "agility"     -> agilityCourse(context, activityKey)
        "woodcutting" -> treeName(context, activityKey)
        "thieving"    -> thievingNpcName(context, activityKey)
        else          -> itemName(context, activityKey)
    }

    fun agilityCourse(context: Context, key: String): String =
        context.stringByName("agility_${key}_name") ?: key.toTitleCase()

    fun agilityCourseDesc(context: Context, key: String): String =
        context.stringByName("agility_${key}_desc") ?: ""

    fun thievingNpcName(context: Context, key: String): String =
        context.stringByName("thieving_npc_${key}_name") ?: key.toTitleCase()

    fun seasonalEventName(context: Context, id: String, fallback: String): String =
        context.stringByName("seasonal_event_${id}_name") ?: fallback

    fun seasonalEventBanner(context: Context, id: String, fallback: String): String =
        context.stringByName("seasonal_event_${id}_banner") ?: fallback

    fun seasonalBountyName(context: Context, id: String, fallback: String): String =
        context.stringByName("seasonal_bounty_${id}_name") ?: fallback

    fun seasonalBountyHint(context: Context, id: String, fallback: String): String =
        context.stringByName("seasonal_bounty_${id}_hint") ?: fallback

    fun seasonalMinigameName(context: Context, id: String, fallback: String): String =
        context.stringByName("seasonal_minigame_${id}_name") ?: fallback

    fun seasonalRewardDesc(context: Context, eventId: String, tokens: Int, fallback: String): String =
        context.stringByName("seasonal_reward_${eventId}_${tokens}_desc") ?: fallback

    fun seasonalMarketName(context: Context, id: String, fallback: String): String =
        context.stringByName("seasonal_market_${id}_name") ?: fallback

    fun skillEmoji(key: String): String = when (key) {
        "mining"      -> "⛏️"
        "fishing"     -> "🎣"
        "woodcutting" -> "🪓"
        "farming"     -> "🌱"
        "firemaking"  -> "🔥"
        "agility"     -> "🏃"
        "smithing"    -> "🔨"
        "cooking"     -> "🍳"
        "fletching"   -> "🪶"
        "crafting"    -> "💍"
        "runecrafting"-> "🔮"
        "attack"      -> "⚔️"
        "strength"    -> "💪"
        "defense"     -> "🛡️"
        "ranged"      -> "🎯"
        "magic"       -> "🪄"
        "hitpoints"   -> "❤️"
        "prayer"      -> "🙏"
        "mercantile"  -> "💰"
        "slayer"      -> "💀"
        "herblore"     -> "🌿"
        "construction" -> "🏗️"
        "thieving"     -> "🥷"
        "combat"       -> "⚔️"
        "carnival"     -> "🎪"
        "tower"        -> "🗼"
        else           -> "🎮"
    }

    /** Drawable icon for a skill, from Shikashi's Fantasy Icons Pack. Null if the skill (or non-skill key like "boss"/"carnival") has no icon; fall back to [skillEmoji]. */
    fun skillIconRes(key: String): Int? = when (key) {
        "mining"       -> R.drawable.skill_mining
        "fishing"      -> R.drawable.skill_fishing
        "woodcutting"  -> R.drawable.skill_woodcutting
        "farming"      -> R.drawable.skill_farming
        "firemaking"   -> R.drawable.skill_firemaking
        "agility"      -> R.drawable.skill_agility
        "smithing"     -> R.drawable.skill_smithing
        "cooking"      -> R.drawable.skill_cooking
        "fletching"    -> R.drawable.skill_fletching
        "crafting"     -> R.drawable.skill_crafting
        "runecrafting" -> R.drawable.skill_runecrafting
        "attack"       -> R.drawable.skill_attack
        "strength"     -> R.drawable.skill_strength
        "defense"      -> R.drawable.skill_defense
        "ranged"       -> R.drawable.skill_ranged
        "magic"        -> R.drawable.skill_magic
        "hitpoints"    -> R.drawable.skill_hitpoints
        "prayer"       -> R.drawable.skill_prayer
        "mercantile"   -> R.drawable.skill_mercantile
        "slayer"       -> R.drawable.skill_slayer
        "herblore"     -> R.drawable.skill_herblore
        "construction" -> R.drawable.skill_construction
        "thieving"     -> R.drawable.skill_thieving
        "combat"       -> R.drawable.skill_combat
        "expedition"   -> R.drawable.skill_expedition
        else           -> null
    }

    fun guildName(context: Context, guild: String): String =
        context.stringByName("guild_name_${guild}") ?: guild.toTitleCase()

    fun guildQuestVerb(context: Context, guild: String, fallback: String): String =
        context.stringByName("daily_verb_${guild}") ?: fallback

    fun guildQuestCombatStyle(context: Context, guild: String): String =
        context.stringByName("guild_combat_${guild}") ?: guild

    fun carnivalPrizeName(context: Context, prizeType: String, prize: String, fallback: String): String =
        when (prizeType) {
            "equipment" -> itemName(context, prize)
            "pet" -> petName(context, prize)
            else -> context.stringByName("carnival_prize_${prize}_name") ?: fallback
        }

    fun carnivalPrizeDesc(context: Context, prizeType: String, prize: String, fallback: String): String =
        when (prizeType) {
            "equipment" -> itemDesc(context, prize)
            "pet" -> petDesc(context, prize)
            else -> context.stringByName("carnival_prize_${prize}_desc") ?: fallback
        }

    fun themeName(context: Context, theme: String): String =
        context.stringByName("settings_theme_${theme}") ?: theme.toTitleCase()

    fun raceName(context: Context, race: String): String =
        context.stringByName("character_race_${race}") ?: race.toTitleCase()

    fun raceNames(context: Context, races: List<String>): String =
        races.joinToString(" & ") { raceName(context, it) }

    fun prestigePathDisplayName(context: Context, skill: String, pathKey: String) =
        context.stringByName("prestige_path_${skill}_${pathKey}")
            ?: context.stringByName("prestige_path_${pathKey}")
            ?: pathKey.toTitleCase()

    fun prestigeEffectDesc(context: Context, effect: String, value: Double, unlock: String? = null): String = when (effect) {
        PrestigeBoosts.XP_PCT            -> context.getString(R.string.prestige_effect_xp_pct, value.toInt())
        PrestigeBoosts.YIELD_PCT         -> context.getString(R.string.prestige_effect_yield_pct, value.toInt())
        PrestigeBoosts.FLOW_RATE         -> context.getString(R.string.prestige_effect_flow_rate, value.trimmed())
        PrestigeBoosts.FLOW_INTERVAL_REDUCTION -> context.getString(R.string.prestige_effect_flow_interval, value.toInt())
        PrestigeBoosts.COMBAT_STAT_FLAT  -> context.getString(R.string.prestige_effect_combat_stat, value.toInt())
        PrestigeBoosts.SESSION_FLOOR_MIN -> context.getString(R.string.prestige_effect_session_floor, value.trimmed())
        PrestigeBoosts.CAPE_SCALING      -> context.getString(R.string.prestige_effect_cape_scaling, value.toInt())
        PrestigeBoosts.BONUS_ROLL_PCT    -> context.getString(R.string.prestige_effect_bonus_roll, value.toInt())
        PrestigeBoosts.COIN_PCT          -> context.getString(R.string.prestige_effect_coin_pct, value.toInt())
        PrestigeBoosts.CROP_ROTATION_PCT -> context.getString(R.string.prestige_effect_crop_rotation, value.toInt())
        PrestigeBoosts.CROP_ROTATION_ALWAYS -> context.getString(R.string.prestige_effect_crop_rotation_always)
        PrestigeBoosts.TOOL_EFF_PCT      -> context.getString(R.string.prestige_effect_tool_eff, value.toInt())
        PrestigeBoosts.SUCCESS_CHANCE_PCT -> context.getString(R.string.prestige_effect_success_chance, value.toInt())
        PrestigeBoosts.RECLAIM_PCT       -> context.getString(R.string.prestige_effect_reclaim, value.toInt())
        PrestigeBoosts.HEAL_PCT          -> context.getString(R.string.prestige_effect_heal, value.toInt())
        PrestigeBoosts.DEATH_KEEP_PCT    -> context.getString(R.string.prestige_effect_death_keep, value.toInt())
        PrestigeBoosts.QUEUE_SLOT        -> context.getString(R.string.prestige_effect_queue_slot, value.toInt())
        PrestigeBoosts.PET_BOOST_PCT     -> context.getString(R.string.prestige_effect_pet_boost, value.toInt())
        PrestigeBoosts.BLESSING_DURATION_PCT -> context.getString(R.string.prestige_effect_blessing_duration, value.toInt())
        PrestigeBoosts.BLESSING_COST_PCT -> context.getString(R.string.prestige_effect_blessing_cost, value.toInt())
        PrestigeBoosts.POTION_BONUS_FLAT -> context.getString(R.string.prestige_effect_potion_bonus, value.toInt())
        PrestigeBoosts.INPUT_SAVE_PCT    -> context.getString(R.string.prestige_effect_input_save, value.toInt())
        PrestigeBoosts.BUILDER_DISCOUNT_PCT -> context.getString(R.string.prestige_effect_builder_discount, value.toInt())
        PrestigeBoosts.SELL_PRICE_PCT    -> context.getString(R.string.prestige_effect_sell_price, value.toInt())
        PrestigeBoosts.SLAYER_POINTS_PCT -> context.getString(R.string.prestige_effect_slayer_points, value.toInt())
        PrestigeBoosts.DOUBLE_HIT_PCT    -> context.getString(R.string.prestige_effect_double_hit, value.toInt())
        PrestigeBoosts.SECOND_CHANCE     -> context.getString(R.string.prestige_effect_second_chance)
        PrestigeBoosts.FORETELL_SLOTS    -> context.getString(R.string.prestige_effect_foretell_slots, value.toInt())
        PrestigeBoosts.SLAYER_MULTI_TASK -> context.getString(R.string.prestige_effect_multi_task)
        PrestigeBoosts.UNLOCK_RECIPE     -> context.getString(R.string.prestige_effect_unlock_recipe, itemName(context, unlock ?: ""))
        else -> ""
    }
}

private fun Double.trimmed(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

// ---------------------------------------------------------------------------
// Returns a context whose locale matches the app's in-app language setting.
// Use this instead of a raw ApplicationContext when looking up game strings
// from non-Activity code (ViewModels, notification receivers, etc.).
// ---------------------------------------------------------------------------

fun Context.withAppLocale(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return this
    val locale = locales[0] ?: return this
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}

// ---------------------------------------------------------------------------
// Extension used internally — resolves a string resource by name at runtime.
// Returns null rather than throwing if the identifier does not exist.
// ---------------------------------------------------------------------------

fun Context.stringByName(name: String): String? {
    // Resolve against the in-app language, not the caller's locale: ViewModels and
    // receivers hold the application context, which follows the system language on
    // pre-33 devices even when the game is set to another language (issue #1434).
    val ctx = withAppLocale()
    val id = ctx.resources.getIdentifier(name, "string", ctx.packageName)
    return if (id != 0) ctx.getString(id) else null
}

// ---------------------------------------------------------------------------
// Resolves a drawable resource by name at runtime (e.g. seasonal banner icons,
// whose resource name is stored as a plain string in JSON/save data).
// Returns null rather than throwing if the identifier does not exist.
// ---------------------------------------------------------------------------

fun Context.drawableByName(name: String): Int? {
    val id = resources.getIdentifier(name, "drawable", packageName)
    return if (id != 0) id else null
}

// ---------------------------------------------------------------------------
// Shared title-case fallback
// ---------------------------------------------------------------------------

fun String.toTitleCase(): String =
    replace('_', ' ')
        .split(' ')
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
