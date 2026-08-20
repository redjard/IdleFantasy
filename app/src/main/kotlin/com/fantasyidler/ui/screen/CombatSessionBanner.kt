package com.fantasyidler.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.viewmodel.CombatViewModel
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.ui.viewmodel.slotDisplayName
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown
import com.fantasyidler.util.toTitleCase
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal fun combatXpBreakdownText(total: Long, bonus: Long, boostWasActive: Boolean): String? {
    if (bonus <= 0L) return null
    val afterBoost = total - bonus
    if (afterBoost <= 0L) return null
    val base = afterBoost / (if (boostWasActive) 2L else 1L)
    val blessMult = total.toDouble() / afterBoost
    val blessStr = "%.2f".format(blessMult).trimEnd('0').trimEnd('.')
    return if (boostWasActive) "(${base.formatXp()} × 2 × $blessStr)"
           else "(${base.formatXp()} × $blessStr)"
}

// ---------------------------------------------------------------------------
// Active session banner
// ---------------------------------------------------------------------------

internal data class CombatLogEntry(
    val isPlayer: Boolean,
    val damage: Int,
    val enemyName: String,
    val isKill: Boolean = false,
    /** HP restored by eating this tick; > 0 renders as an eat line (issue #1431). */
    val heal: Int = 0,
    /** True when this damage came from the raid mercenary party. */
    val ally: Boolean = false,
)

@Composable
internal fun CombatSessionBanner(
    session: SkillSession,
    dungeons: List<DungeonData>,
    bosses: List<BossData>,
    enemies: Map<String, EnemyData>,
    skillLevels: Map<String, Int>,
    modifier: Modifier = Modifier,
    hpPrestigeBonus: Int = 0,
    towerHpBonus: Int = 0,
    attackBonus: Int,
    strengthBonus: Int,
    defenseBonus: Int,
    equippedFood: Map<String, Int>,
    foodHealValues: Map<String, Int>,
    showEndTime: Boolean = true,
    repeatIndex: Int = 0,
    repeatTotal: Int = 0,
    hiredMercs: List<com.fantasyidler.ui.viewmodel.MercContract> = emptyList(),
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit,
) {
    val context = LocalContext.current
    val sessionBoss = bosses.firstOrNull { it.id == session.activityKey }
    val dungeonName = dungeons.firstOrNull { it.name == session.activityKey }
        ?.let { GameStrings.dungeonName(context, it.name) }
        ?: sessionBoss?.let { GameStrings.bossName(context, it.id) }
        ?: run {
            if (session.skillName == "tower") {
                val floor = session.activityKey.removePrefix("tower_floor_").toIntOrNull()
                if (floor != null) context.getString(R.string.tower_floor_label, floor) else session.activityKey
            } else session.activityKey
        }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    val endsAt = session.endsAt
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            now = System.currentTimeMillis()
            delay(500L)
        }
        now = System.currentTimeMillis()
    }

    val isDone = session.completed || now >= endsAt

    // Decode frames once per session
    val frames = remember(session.sessionId) {
        runCatching { Json.decodeFromString<List<SessionFrame>>(session.frames) }.getOrElse { emptyList() }
    }
    val frameCount = if (session.skillName == "boss")
        (bosses.firstOrNull { it.id == session.activityKey }?.durationMinutes ?: 60).coerceAtLeast(1)
    else 60
    val perFrameMs = ((session.endsAt - session.startedAt) / frameCount.toLong()).coerceAtLeast(1L)
    val currentFrameIdx = remember(now) {
        ((now - session.startedAt) / perFrameMs).toInt()
            .coerceIn(0, (frames.size - 1).coerceAtLeast(0))
    }
    val currentFrame = frames.getOrNull(currentFrameIdx)

    val currentEnemyKey: String? = remember(currentFrameIdx) {
        currentFrame?.enemyKey?.takeIf { it.isNotEmpty() }
            ?: frames.take(currentFrameIdx + 1)
                .lastOrNull { it.killsByEnemy.isNotEmpty() }
                ?.killsByEnemy?.keys?.firstOrNull()
    }
    val currentEnemy = currentEnemyKey?.let { enemies[it] }

    val isBoss = session.skillName == "boss"
    // Pace by the session's true tick cadence, not the current frame's own hit count: a
    // partial final frame would otherwise stretch its few hits across the whole minute
    // (issue #935).
    val fullTicks     = remember(session.sessionId) { CombatSimulator.fullFrameTicks(frames) }
    val attackSpeedMs = (perFrameMs / fullTicks).coerceAtLeast(2L)
    val frameStartMs  = session.startedAt + currentFrameIdx.toLong() * perFrameMs
    val maxTick = (currentFrame?.playerHits?.size?.minus(1) ?: 0).coerceAtLeast(0)
    // Half-tick pacing: the player's hit shows on the tick, the enemy's reply half a tick
    // later, so log lines appear one at a time instead of clumping per tick (issue #935).
    val halfTickInFrame = if (!isDone) ((now - frameStartMs) * 2 / attackSpeedMs).toInt().coerceIn(0, maxTick * 2 + 1) else maxTick * 2 + 1
    val tickInFrame = halfTickInFrame / 2

    val killsSoFar: Map<String, Int> = remember(currentFrameIdx, tickInFrame) {
        val acc = frames.take(currentFrameIdx).fold(mutableMapOf<String, Int>()) { a, f ->
            f.killsByEnemy.forEach { (k, v) -> a[k] = (a[k] ?: 0) + v }
            a
        }
        val f = frames.getOrNull(currentFrameIdx)
        if (f != null && !isBoss) {
            val enemy = enemies[f.enemyKey]
            if (enemy != null && f.playerHits.isNotEmpty()) {
                var hp = enemyHpAtFrameStart(frames, currentFrameIdx, enemies) ?: enemy.hp
                var kills = 0
                for (dmg in f.playerHits.take(tickInFrame + 1)) {
                    hp -= dmg
                    if (hp <= 0) { kills++; hp = enemy.hp }
                }
                if (kills > 0) acc[f.enemyKey] = (acc[f.enemyKey] ?: 0) + kills
            }
        }
        acc
    }

    val foodConsumedSoFar: Map<String, Int> = remember(currentFrameIdx) {
        frames.take(currentFrameIdx).fold(mutableMapOf()) { acc, f ->
            f.foodConsumed.forEach { (k, v) -> acc[k] = (acc[k] ?: 0) + v }
            acc
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text  = if (isDone) stringResource(R.string.label_session_complete)
                    else stringResource(R.string.label_session_in_progress),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (sessionBoss != null) {
                BossIcon(
                    bossId        = sessionBoss.id,
                    modifier      = Modifier.size(28.dp),
                    fallbackEmoji = sessionBoss.emoji,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text  = dungeonName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if ((isBoss || session.skillName == "combat") && repeatTotal > 1) {
            Spacer(Modifier.height(4.dp))
            Text(
                text       = if (isBoss) stringResource(R.string.combat_fight_progress, repeatIndex.coerceAtLeast(1), repeatTotal)
                             else stringResource(R.string.combat_run_progress, repeatIndex.coerceAtLeast(1), repeatTotal),
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))

        if (!isDone) {
            Text(
                text       = remember(now, showEndTime) { endsAt.toCountdown(context, showEndTime) },
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
            )

            if (session.skillName == "combat" || session.skillName == "boss" || session.skillName == "tower") {
                val context = LocalContext.current
                val currentBoss = if (isBoss) bosses.firstOrNull { it.id == session.activityKey } else null
                val divColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)

                // Player HP (per-tick if hit data exists, else per-frame fallback).
                // Enemy hits display half a tick after player hits, so only count the
                // newest tick's enemy damage once its log line is visible.
                // Max HP comes from the simulation snapshot so mid-run HP level-ups
                // don't shift an in-flight run's display (issue #1411); live stats are
                // the fallback for sessions recorded before the snapshot existed.
                val maxHp = frames.firstOrNull { it.maxHp > 0 }?.maxHp
                    ?: ((skillLevels[Skills.HITPOINTS] ?: 1) + hpPrestigeBonus + towerHpBonus) * 10
                val enemyTicksShown = tickInFrame + if (halfTickInFrame >= 2 * tickInFrame + 1) 1 else 0
                val currentPlayerHp = if (currentFrame?.enemyHits?.isNotEmpty() == true) {
                    val base = frames.getOrNull(currentFrameIdx - 1)?.hpAfter ?: maxHp
                    // Heals thread per tick alongside enemy damage so HP no longer sags all
                    // frame and snaps up at the boundary (issue #1431).
                    (base - currentFrame.enemyHits.take(enemyTicksShown).sum() +
                        currentFrame.playerHeals.take(enemyTicksShown).sum()).coerceAtLeast(0)
                } else {
                    frames.getOrNull(currentFrameIdx - 1)?.hpAfter ?: maxHp
                }

                // Live enemy HP (cumulative for boss, per-enemy reset for dungeon).
                // Raid mercenary damage counts toward the boss bar alongside the player's.
                val currentEnemyHp = when {
                    currentBoss != null -> {
                        val prevDmg = frames.take(currentFrameIdx).sumOf { it.playerHits.sum() + it.allyHits.sum() }
                        val curDmg = (currentFrame?.playerHits?.take(tickInFrame + 1)?.sum() ?: 0) +
                            (currentFrame?.allyHits?.take(tickInFrame + 1)?.sum() ?: 0)
                        (currentBoss.hp - prevDmg - curDmg).coerceAtLeast(0)
                    }
                    currentEnemy != null && currentFrame?.playerHits?.isNotEmpty() == true -> {
                        var hp = enemyHpAtFrameStart(frames, currentFrameIdx, enemies) ?: currentEnemy.hp
                        for (dmg in currentFrame.playerHits.take(tickInFrame + 1)) {
                            hp -= dmg
                            if (hp <= 0) hp = currentEnemy.hp
                        }
                        hp.coerceAtLeast(0)
                    }
                    else -> currentEnemy?.hp ?: 0
                }

                // Combat log: last 8 entries, interleaved per half-tick. Enemy HP threads
                // across frames like the simulator's carryover so kill lines land on the
                // tick they actually happened (issue #935).
                val combatLog = remember(currentFrameIdx, halfTickInFrame) {
                    buildList<CombatLogEntry> {
                        var hp = 0
                        var prevKey: String? = null
                        for (i in 0..currentFrameIdx) {
                            val f = frames.getOrNull(i) ?: break
                            val eName = bosses.firstOrNull { it.id == f.enemyKey }?.let { GameStrings.bossName(context, it.id) }
                                ?: enemies[f.enemyKey]?.let { GameStrings.enemyName(context, f.enemyKey) } ?: f.enemyKey
                            val enemyHp = if (!isBoss) enemies[f.enemyKey]?.hp ?: Int.MAX_VALUE else Int.MAX_VALUE
                            if (f.enemyKey != prevKey) hp = enemyHp
                            prevKey = f.enemyKey
                            val lastTick = if (i < currentFrameIdx) maxOf(f.playerHits.size, f.enemyHits.size) - 1 else tickInFrame
                            for (t in 0..lastTick) {
                                f.playerHits.getOrNull(t)?.let { dmg ->
                                    add(CombatLogEntry(true, dmg, eName))
                                    hp -= dmg
                                    if (hp <= 0) { add(CombatLogEntry(false, 0, eName, isKill = true)); hp = enemyHp }
                                }
                                f.allyHits.getOrNull(t)?.takeIf { it > 0 }
                                    ?.let { add(CombatLogEntry(true, it, eName, ally = true)) }
                                if (i < currentFrameIdx || 2 * t + 1 <= halfTickInFrame) {
                                    f.enemyHits.getOrNull(t)?.let { add(CombatLogEntry(false, it, eName)) }
                                    f.playerHeals.getOrNull(t)?.takeIf { it > 0 }
                                        ?.let { add(CombatLogEntry(true, 0, eName, heal = it)) }
                                }
                            }
                        }
                    }.takeLast(8)
                }

                // Drops and XP from completed frames
                val dropsSoFar = remember(currentFrameIdx) {
                    frames.take(currentFrameIdx).fold(mutableMapOf<String, Int>()) { acc, f ->
                        f.items.forEach { (k, v) -> acc[k] = (acc[k] ?: 0) + v }
                        acc
                    }
                }
                val xpSoFar = remember(currentFrameIdx) {
                    frames.take(currentFrameIdx).fold(mutableMapOf<String, Long>()) { acc, f ->
                        f.xpBySkill.forEach { (k, v) -> acc[k] = (acc[k] ?: 0L) + v }
                        acc
                    }
                }

                Spacer(Modifier.height(16.dp))
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {

                        // ── Enemy ──────────────────────────────────────────
                        if (currentBoss != null) {
                            Text(
                                text       = "${currentBoss.emoji} ${GameStrings.bossName(context, currentBoss.id)}",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                                progress  = { if (currentBoss.hp > 0) currentEnemyHp / currentBoss.hp.toFloat() else 0f },
                                modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color     = MaterialTheme.colorScheme.error,
                                trackColor = MaterialTheme.colorScheme.errorContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = "${stringResource(R.string.label_hp)} $currentEnemyHp/${currentBoss.hp}  ${stringResource(R.string.combat_atk)} ${currentBoss.combatStats.attackLevel}  ${stringResource(R.string.combat_str)} ${currentBoss.combatStats.strengthLevel}  ${stringResource(R.string.combat_def)} ${currentBoss.combatStats.defenseLevel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else if (currentEnemy != null) {
                            Text(
                                text       = GameStrings.enemyName(context, currentEnemy.name),
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                                progress  = { if (currentEnemy.hp > 0) currentEnemyHp / currentEnemy.hp.toFloat() else 0f },
                                modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color     = MaterialTheme.colorScheme.error,
                                trackColor = MaterialTheme.colorScheme.errorContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = "${stringResource(R.string.label_hp)} $currentEnemyHp/${currentEnemy.hp}  ${stringResource(R.string.combat_atk)} ${currentEnemy.combatStats.attackLevel}  ${stringResource(R.string.combat_str)} ${currentEnemy.combatStats.strengthLevel}  ${stringResource(R.string.combat_def)} ${currentEnemy.combatStats.defenseLevel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else {
                            Text(
                                text  = stringResource(R.string.combat_fighting),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── Player HP + gear ───────────────────────────────
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                        val hpPct   = currentPlayerHp * 100 / maxHp
                        val hpColor = when {
                            hpPct >= 50 -> Color(0xFF4CAF50)
                            hpPct >= 20 -> Color(0xFFFFC107)
                            else        -> MaterialTheme.colorScheme.error
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                text       = "${stringResource(R.string.label_hp)}: $currentPlayerHp / $maxHp",
                                style      = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = hpColor,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                            progress  = { if (maxHp > 0) currentPlayerHp / maxHp.toFloat() else 0f },
                            modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color     = hpColor,
                            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
                        )
                        val atkLabel = stringResource(R.string.combat_atk)
                        val strLabel = stringResource(R.string.combat_str)
                        val defLabel = stringResource(R.string.combat_def)
                        val bonusParts = buildList {
                            if (attackBonus   != 0) add("+$attackBonus $atkLabel")
                            if (strengthBonus != 0) add("+$strengthBonus $strLabel")
                            if (defenseBonus  != 0) add("+$defenseBonus $defLabel")
                        }
                        if (bonusParts.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = bonusParts.joinToString("  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── Raid party ─────────────────────────────────────
                        if (currentBoss?.raid == true && hiredMercs.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.raid_party_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            // Frame order matches the contract list the session started with,
                            // so index i is merc i's HP. Pre-feature sessions lack the
                            // snapshot and fall back to full HP.
                            val allyHpNow = frames.getOrNull(currentFrameIdx - 1)?.allyHpAfter
                            hiredMercs.forEachIndexed { i, contract ->
                                val m         = contract.merc
                                val mercMaxHp = m.hp * 10
                                val mercHpNow = (allyHpNow?.getOrNull(i) ?: mercMaxHp).coerceAtLeast(0)
                                val downed    = mercHpNow <= 0
                                val mercHpPct   = if (mercMaxHp > 0) mercHpNow * 100 / mercMaxHp else 0
                                val mercHpColor = when {
                                    mercHpPct >= 50 -> Color(0xFF4CAF50)
                                    mercHpPct >= 20 -> Color(0xFFFFC107)
                                    else            -> MaterialTheme.colorScheme.error
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text       = "${m.emoji} ${GameStrings.mercName(context, m.id)}",
                                    style      = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (downed) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                                                 else MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text       = "${stringResource(R.string.label_hp)}: $mercHpNow / $mercMaxHp",
                                    style      = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = mercHpColor,
                                )
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    gapSize = 0.dp,
                                    drawStopIndicator = {},
                                    progress  = { if (mercMaxHp > 0) mercHpNow / mercMaxHp.toFloat() else 0f },
                                    modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color     = mercHpColor,
                                    trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text  = "${m.attackLevel + m.attackBonus} ${stringResource(R.string.combat_atk)}  " +
                                        "${m.strengthLevel + m.strengthBonus} ${stringResource(R.string.combat_str)}  " +
                                        "${m.defenseLevel} ${stringResource(R.string.combat_def)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            val alliesDownNow = frames.getOrNull(currentFrameIdx - 1)?.alliesDown ?: 0
                            if (alliesDownNow > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text  = stringResource(R.string.raid_allies_down, alliesDownNow),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        // ── Equipped food ──────────────────────────────────
                        // The frame-0 snapshot keeps the list stable if gear food is
                        // changed mid-run (issue #1411); live gear is the fallback for
                        // sessions recorded before the snapshot existed.
                        val foodAtStart = frames.firstOrNull()?.foodAtStart
                            ?.takeIf { it.isNotEmpty() } ?: equippedFood
                        if (foodAtStart.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.label_food),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            for ((key, startQty) in foodAtStart) {
                                val remaining = (startQty - (foodConsumedSoFar[key] ?: 0)).coerceAtLeast(0)
                                val heal      = foodHealValues[key] ?: 0
                                val name      = GameStrings.itemName(context, key)
                                Text(
                                    text  = "$name ×$remaining (${stringResource(R.string.combat_heals_hp, heal)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (remaining > 0)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f),
                                )
                            }
                            if (foodConsumedSoFar.isNotEmpty()) {
                                val eatenSoFar = stringResource(R.string.combat_eaten_so_far)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text  = foodConsumedSoFar.entries
                                        .sortedByDescending { it.value }
                                        .joinToString(", ") { (k, v) ->
                                            "$v ${GameStrings.itemName(context, k)}"
                                        }
                                        + " $eatenSoFar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }

                        // ── Kills ──────────────────────────────────────────
                        if (killsSoFar.isNotEmpty()) {
                            val defeatedSoFar = stringResource(R.string.combat_defeated_so_far)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = killsSoFar.entries
                                    .sortedByDescending { it.value }
                                    .joinToString(", ") { (k, v) ->
                                        "$v ${bosses.firstOrNull { it.id == k }?.let { GameStrings.bossName(context, it.id) } ?: enemies[k]?.let { GameStrings.enemyName(context, k) } ?: k}"
                                    }
                                    + " $defeatedSoFar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── Drops so far ───────────────────────────────────
                        if (dropsSoFar.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.label_drops),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text  = dropsSoFar.entries
                                    .sortedByDescending { it.value }
                                    .joinToString("  ") { (k, v) ->
                                        "${GameStrings.itemName(context, k)} ×$v"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── XP so far ──────────────────────────────────────
                        if (xpSoFar.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.label_xp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            val xpSkillOrder = listOf(
                                Skills.ATTACK, Skills.STRENGTH, Skills.DEFENSE,
                                Skills.RANGED, Skills.MAGIC, Skills.HITPOINTS,
                            )
                            Text(
                                text  = xpSkillOrder
                                    .mapNotNull { skill -> xpSoFar[skill]?.let { skill to it } }
                                    .joinToString("  ") { (skill, xp) ->
                                        "${GameStrings.skillName(context, skill).take(3).uppercase()} +${xp.formatXp()}"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── Combat log ─────────────────────────────────────
                        if (combatLog.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.combat_log_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            Column {
                                for (entry in combatLog) {
                                    if (entry.heal > 0) {
                                        Text(
                                            text  = stringResource(R.string.combat_log_heal, entry.heal),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF4CAF50),
                                        )
                                    } else if (entry.isKill) {
                                        Text(
                                            text  = stringResource(R.string.combat_log_kill, entry.enemyName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else if (entry.ally) {
                                        Text(
                                            text  = stringResource(R.string.combat_log_ally_hit, entry.enemyName, entry.damage),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF64B5F6),
                                        )
                                    } else if (entry.isPlayer) {
                                        val color = if (entry.damage > 0) Color(0xFF4CAF50)
                                                    else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f)
                                        val text = if (entry.damage > 0)
                                            stringResource(R.string.combat_log_player_hit, entry.enemyName, entry.damage)
                                        else
                                            stringResource(R.string.combat_log_player_miss, entry.enemyName)
                                        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
                                    } else {
                                        val color = if (entry.damage > 0) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f)
                                        val text = if (entry.damage > 0)
                                            stringResource(R.string.combat_log_enemy_hit, entry.enemyName, entry.damage)
                                        else
                                            stringResource(R.string.combat_log_enemy_miss, entry.enemyName)
                                        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        if (isDone) {
            Text(
                text  = stringResource(R.string.worker_manage_from_home),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(8.dp))
        }

        if (!isDone) {
            OutlinedButton(
                onClick  = { showAbandonConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.btn_abandon_session))
            }
        }

        if (showAbandonConfirm) {
            AlertDialog(
                onDismissRequest = { showAbandonConfirm = false },
                title = { Text(stringResource(R.string.session_abandon_title)) },
                text  = { Text(stringResource(R.string.session_abandon_body)) },
                confirmButton = {
                    TextButton(onClick = { showAbandonConfirm = false; onAbandon() }) {
                        Text(stringResource(R.string.btn_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAbandonConfirm = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                },
            )
        }

        if (BuildConfig.DEBUG && !isDone) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDebugFinish) {
                Text("[Debug] Finish Now")
            }
        }
    }
}

/**
 * Enemy HP carried into [frameIdx], mirroring the simulator's cross-frame carryover: a
 * partially damaged enemy persists across minute boundaries, resetting only on a kill or
 * when the enemy type changes. Replaying every frame from full HP would show kills later
 * than they happened (issue #935). Null when the frame's enemy starts fresh or is unknown.
 */
private fun enemyHpAtFrameStart(
    frames: List<SessionFrame>,
    frameIdx: Int,
    enemies: Map<String, EnemyData>,
): Int? {
    val frame = frames.getOrNull(frameIdx) ?: return null
    val full  = enemies[frame.enemyKey]?.hp ?: return null
    var hp = full
    var prevKey: String? = null
    for (i in 0 until frameIdx) {
        val f     = frames.getOrNull(i) ?: break
        val fFull = enemies[f.enemyKey]?.hp ?: continue
        if (f.enemyKey != prevKey) hp = fFull
        for (dmg in f.playerHits) {
            hp -= dmg
            if (hp <= 0) hp = fFull
        }
        prevKey = f.enemyKey
    }
    return if (frame.enemyKey == prevKey) hp else full
}
