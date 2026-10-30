package com.example.batterybuddy

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/** Lets the Life tab's buttons send a scripted scene to the stage preview. */
object PreviewBus {
    var scene by mutableStateOf<Scene?>(null)
}

/** The real BuddyView, embedded. It follows every setting live, and the phone's real state every few seconds. */
@Composable
fun BuddyPreview(modifier: Modifier = Modifier, mode: Int = BuddyView.MODE_SIMPLE, scenes: Boolean = false) {
    val ctx = LocalContext.current
    val holder = remember { arrayOfNulls<BuddyView>(1) }
    val scene = if (scenes) PreviewBus.scene else null
    AndroidView(
        modifier = modifier,
        factory = { c -> BuddyView(c).also { it.mode = mode; holder[0] = it } },
        update = { v -> if (scene != null) v.play(scene) }
    )
    LaunchedEffect(Unit) {
        while (true) {
            holder[0]?.state = BuddyState.snapshot(ctx)
            delay(5000)
        }
    }
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val (show, setShow) = rememberPref("show", true)
    val (size, setSize) = rememberPref("size", 26)
    val (x, setX) = rememberPref("x", 6)
    val (y, setY) = rememberPref("y", 0)
    val (name, _) = rememberPref("char_name", "")
    val (custom, _) = rememberPref("custom", false)
    val (style, _) = rememberPref("style", 1)

    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Hero(if (custom && name.isNotEmpty()) name else "Built-in buddy", style == 1) }
        item { StatusBarMock() }
        item {
            SectionCard("Wake me up", "🌸") {
                Text(
                    "Android needs your OK before I can sit on the status bar. Tap below, open Installed apps → Battery Buddy, and switch me on.",
                    color = PlumSoft,
                    fontSize = 14.sp
                )
                PinkButton("Open Accessibility settings") {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                SwitchRow("Show buddy", "Hide me without turning the service off", show, setShow)
            }
        }
        item {
            SectionCard("Put me on the battery", "📍") {
                LabeledSlider("Size", size, 16, 60, "dp", setSize)
                LabeledSlider("From the right edge", x, 0, 120, "dp", setX)
                LabeledSlider("From the top", y, -20, 40, "dp", setY)
                Text(
                    "Tip: slide until my legs dangle over your battery icon.",
                    color = PlumSoft,
                    fontSize = 13.sp
                )
            }
        }
        item {
            SectionCard("Home-screen widget", "🏠") {
                Text(
                    "Long-press an empty spot on your home screen, tap Widgets, and add Battery Buddy. She shows your battery and her mood right there.",
                    color = PlumSoft,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun Hero(who: String, is3d: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(SakuraSoft, LavenderSoft)))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Battery Buddy ✨", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Plum)
            Text("a tiny friend for your battery icon", color = PlumSoft, fontSize = 14.sp)
            BuddyPreview(Modifier.fillMaxWidth().height(190.dp), BuddyView.MODE_STAGE)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(who)
                Pill(if (is3d) "3D figurine" else "2D flat")
            }
        }
    }
}

/** A little fake status bar so you can see roughly how she sits on the battery. */
@Composable
private fun StatusBarMock() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("How she’ll sit up there 👀", fontWeight = FontWeight.Bold, color = Plum)
        Box(Modifier.fillMaxWidth().height(70.dp)) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(Plum)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("9:41", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("5G ▂▄▆", color = Color.White, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(26.dp, 13.dp)
                        .border(1.5.dp, Color.White, RoundedCornerShape(4.dp))
                        .padding(2.dp)
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(0.7f).background(Mint, RoundedCornerShape(2.dp)))
                }
            }
            BuddyPreview(
                Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 14.dp).size(40.dp, 50.dp)
            )
        }
    }
}
