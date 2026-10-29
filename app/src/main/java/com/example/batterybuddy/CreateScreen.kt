package com.example.batterybuddy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PRESET_EMOJI = listOf("🐱", "🐰", "🦊", "⚡", "😎", "🔮")

@Composable
fun CreateScreen() {
    val ctx = LocalContext.current
    val prefs = Buddy.prefs(ctx)
    val (hair, setHair) = rememberPref("hair", 0)
    val (hairColor, setHairColor) = rememberPref("hair_color", 0)
    val (skin, setSkin) = rememberPref("skin", 0)
    val (eyes, setEyes) = rememberPref("eyes", 0)
    val (outfit, setOutfit) = rememberPref("outfit", 0)
    val (ears, setEars) = rememberPref("ears", 1)
    val (acc, setAcc) = rememberPref("acc", 1)
    val (style, setStyle) = rememberPref("style", 1)
    val (tilt, setTilt) = rememberPref("tilt", true)
    val (speed, setSpeed) = rememberPref("speed", 100)
    val (sprite, setSprite) = rememberPref("sprite", false)
    val (custom, _) = rememberPref("custom", false)
    val current = intArrayOf(hair, hairColor, skin, eyes, outfit, ears, acc)

    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Make your own buddy 🎨", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Plum) }

        if (custom) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = LavenderSoft)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "You’re using a character picture right now, so the look options won’t show until you switch back to the built-in buddy.",
                            color = Plum,
                            fontSize = 14.sp
                        )
                        PinkButton("Use the built-in buddy") { Saved.useBuiltIn(ctx) }
                    }
                }
            }
        }

        item {
            SectionCard("Quick presets", "✨") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Look.PRESETS.forEachIndexed { i, (name, vals) ->
                        item {
                            FilterChip(
                                selected = current.contentEquals(vals),
                                onClick = {
                                    val e = prefs.edit()
                                    Look.KEYS.forEachIndexed { k, key -> e.putInt(key, vals[k]) }
                                    e.putBoolean("custom", false).putString("char_name", "").putLong("rev", System.nanoTime())
                                    e.apply()
                                },
                                label = { Text("${PRESET_EMOJI[i]} $name") },
                                shape = RoundedCornerShape(50),
                                colors = chipColors()
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard("Her look", "💇") {
                ChipChoice("Hair style", Look.HAIR_STYLES, hair, setHair)
                Swatches("Hair colour", Look.HAIR_COLORS, hairColor, setHairColor)
                Swatches("Skin", Look.SKIN_COLORS, skin, setSkin)
                Swatches("Eyes", Look.EYE_COLORS, eyes, setEyes)
                Swatches("Outfit", Look.OUTFIT_COLORS, outfit, setOutfit)
                ChipChoice("Ears and tail", Look.EAR_NAMES, ears, setEars)
                ChipChoice("Accessory", Look.ACC_NAMES, acc, setAcc)
            }
        }

        item {
            SectionCard("Style", "🧊") {
                ChipChoice("Look", Look.STYLE_NAMES, style, setStyle)
                SwitchRow("Tilt with my phone", "3D only. She follows how you hold your phone.", tilt, setTilt)
                LabeledSlider("Animation speed", speed, 50, 200, "%", setSpeed)
            }
        }

        item {
            SectionCard("Your own picture", "📷") {
                Text(
                    "Pick a picture on the Characters tab. A normal picture becomes her head. A full-body PNG with a transparent background looks best drawn as-is.",
                    color = PlumSoft,
                    fontSize = 14.sp
                )
                SwitchRow("Draw it as a full-body sprite", "Turn on for transparent full-body PNGs", sprite, setSprite)
            }
        }
    }
}
