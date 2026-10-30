package com.example.batterybuddy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BASE = BuddyState()

/** Every button on the "Try her moods" card. Each one overrides reality for a few seconds. */
private val SCENES: List<Pair<String, () -> Scene>> = listOf(
    "💤 Sleeping" to { Scene(state = BASE.copy(charging = true, level = 55, watts = 3f)) },
    "⚡ Fast charge" to { Scene(state = BASE.copy(charging = true, level = 40, watts = 20f)) },
    "🎉 Hit 100%" to { Scene(state = BASE.copy(charging = true, level = 100, watts = 2f)) },
    "🥱 Yawning (28%)" to { Scene(state = BASE.copy(level = 28)) },
    "🥺 Begging (9%)" to { Scene(state = BASE.copy(level = 9)) },
    "😵 Fainted (4%)" to { Scene(state = BASE.copy(level = 4)) },
    "🥶 Cold" to { Scene(state = BASE.copy(tempC = 3f)) },
    "🥵 Overheating" to { Scene(state = BASE.copy(tempC = 46f)) },
    "🎧 Music" to { Scene(state = BASE.copy(music = true)) },
    "🤫 Do Not Disturb" to { Scene(state = BASE.copy(dnd = true)) },
    "👋 Wave" to { Scene(state = BASE, action = BuddyView.A_WAVE) },
    "🙆 Stretch" to { Scene(state = BASE, action = BuddyView.A_STRETCH) },
    "👀 Look around" to { Scene(state = BASE, action = BuddyView.A_LOOK) },
    "🫧 Blow a bubble" to { Scene(state = BASE, action = BuddyView.A_BUBBLE) },
    "🦵 Kick" to { Scene(state = BASE, action = BuddyView.A_KICK) },
    "👉 Eye-break poke" to { Scene(state = BASE, action = BuddyView.A_POKE, bubble = "Look away for 20 s \uD83D\uDC40") },
    "🚶 Wander" to { Scene(state = BASE, action = BuddyView.A_WALK) },
    "🕳️ Hide in the camera hole" to { Scene(state = BASE, action = BuddyView.A_HOLE) },
    "🐱 Pet hop" to { Scene(state = BASE, action = BuddyView.A_PET) },
    "⏱️ Time-left bubble" to { Scene(state = BASE, bubble = "About 7 h 20 min left \uD83D\uDD0B") },
    "🌙 Night" to { Scene(state = BASE, tod = DayClock.NIGHT) },
    "☕ Morning" to { Scene(state = BASE, tod = DayClock.MORNING) },
    "😎 Noon" to { Scene(state = BASE, tod = DayClock.NOON) },
    "🎃 Halloween" to { Scene(state = BASE, season = DayClock.S_HALLOWEEN) },
    "🎅 Christmas" to { Scene(state = BASE, season = DayClock.S_XMAS) },
    "🎊 New Year" to { Scene(state = BASE, season = DayClock.S_NEWYEAR) },
    "💘 Valentine’s" to { Scene(state = BASE, season = DayClock.S_VALENTINE) },
    "🇮🇳 Republic / Independence Day" to { Scene(state = BASE, season = DayClock.S_INDIA) },
    "🪔 Diwali" to { Scene(state = BASE, season = DayClock.S_DIWALI) },
    "🌸 Sakura season" to { Scene(state = BASE, season = DayClock.S_SAKURA) }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LifeScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Buddy.prefs(ctx) }
    val (xp, _) = rememberPref("xp", 0)
    val (statsJson, _) = rememberPref("stats", "") // only here so the cards refresh when the diary changes
    val (weekly, setWeekly) = rememberPref("weekly", true)
    val lvl = Stats.level(xp)
    val hearts = remember(statsJson) { Stats.hearts(prefs) }
    val streak = remember(statsJson) { Stats.streak(prefs) }
    val report = remember(statsJson) { Stats.weekly(prefs) }

    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Her life 💖", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Plum) }

        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Brush.verticalGradient(listOf(SakuraSoft, LavenderSoft)))
                    .padding(12.dp)
            ) {
                BuddyPreview(Modifier.fillMaxWidth().height(190.dp), BuddyView.MODE_STAGE, scenes = true)
            }
        }

        item {
            SectionCard("Try her moods", "🎭") {
                Text("Tap anything to see it on the preview above. The real thing happens by itself.", color = PlumSoft, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SCENES.forEach { (label, make) ->
                        FilterChip(
                            selected = false,
                            onClick = { PreviewBus.scene = make() },
                            label = { Text(label) },
                            shape = RoundedCornerShape(50),
                            colors = chipColors()
                        )
                    }
                }
            }
        }

        item {
            SectionCard("Level $lvl buddy", "🌟") {
                LinearProgressIndicator(
                    progress = { Stats.levelProgress(xp) },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)),
                    color = Sakura,
                    trackColor = SakuraSoft
                )
                Text(
                    if (lvl < 20) "$xp XP · ${50 - xp % 50} to level ${lvl + 1}" else "$xp XP · max level!",
                    color = PlumSoft,
                    fontSize = 13.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mood  ", fontWeight = FontWeight.SemiBold, color = Plum)
                    Text("❤️".repeat(hearts) + "🤍".repeat(5 - hearts), fontSize = 18.sp)
                }
                Text("🔥  Streak: $streak day${if (streak == 1) "" else "s"}", fontWeight = FontWeight.SemiBold, color = Plum)
                for (i in 4 until Look.ACC_NAMES.size) {
                    val open = lvl >= Look.ACC_UNLOCK[i]
                    Text(
                        if (open) "✅  ${Look.ACC_NAMES[i]} unlocked" else "🔒  ${Look.ACC_NAMES[i]} · level ${Look.ACC_UNLOCK[i]}",
                        color = if (open) Plum else PlumSoft,
                        fontSize = 14.sp
                    )
                }
                Text(
                    "Earn XP by unplugging near your charge limit (+10) and by staying on duty each day (+2). Overheating costs 5. Hearts and your streak follow how kindly you treat your battery. Unlocked accessories appear on the Create tab.",
                    color = PlumSoft,
                    fontSize = 13.sp
                )
            }
        }

        item {
            SectionCard("Weekly coach", "🩺") {
                Text(
                    "Last 7 days: ✅ ${report.ok} healthy charges · 🔌 ${report.over} over the limit · 💯 ${report.full} times at 100% · 🪫 ${report.low} under 20% · 🥵 ${report.hot} hot moments",
                    color = Plum,
                    fontSize = 14.sp
                )
                report.tips.forEach { Text("💡  $it", color = PlumSoft, fontSize = 14.sp) }
                SwitchRow("Send me a weekly report", "A notification once a week with this summary.", weekly, setWeekly)
            }
        }

        item {
            SectionCard("What she does", "🎬") {
                for ((key, title, sub) in Behave.ITEMS) {
                    val (on, set) = rememberPref(key, true)
                    SwitchRow(title, sub, on, set)
                }
            }
        }
    }
}
