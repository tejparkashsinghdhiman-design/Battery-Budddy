package com.example.batterybuddy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SafetyScreen() {
    val (alertCharge, setAlertCharge) = rememberPref("alert_charge", true)
    val (limit, setLimit) = rememberPref("limit", 80)
    val (alertHot, setAlertHot) = rememberPref("alert_hot", true)
    val (hideAdult, setHideAdult) = rememberPref("hide_adult", true)

    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Keep your phone happy 🛡️", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Plum) }
        item {
            SectionCard("Battery care", "🔋") {
                SwitchRow(
                    "Unplug reminder",
                    "I nudge you when charging reaches your limit. Stopping early is gentler on battery lifespan.",
                    alertCharge,
                    setAlertCharge
                )
                LabeledSlider("Charge limit", limit, 50, 100, "%", setLimit)
                SwitchRow(
                    "Overheat warning",
                    "At 43 °C, or when Android says the battery is overheating. I turn red and sweat.",
                    alertHot,
                    setAlertHot
                )
            }
        }
        item {
            SectionCard("Filters", "🔍") {
                SwitchRow(
                    "Hide adult titles",
                    "Characters from adult-only series are left out of the lists and searches.",
                    hideAdult,
                    setHideAdult
                )
            }
        }
        item {
            SectionCard("Good to know", "💖") {
                Text("👆  I can’t be tapped, so I never block your screen.", color = PlumSoft, fontSize = 14.sp)
                Text("😴  I rest at 15% battery to save power.", color = PlumSoft, fontSize = 14.sp)
                Text("⚡  Add the “Battery Buddy” tile to Quick Settings for an instant on/off switch.", color = PlumSoft, fontSize = 14.sp)
                Text("🔒  I never read your screen. The internet is only used to fetch character pictures.", color = PlumSoft, fontSize = 14.sp)
            }
        }
    }
}
