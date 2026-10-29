package com.example.batterybuddy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Sakura,
    selectedLabelColor = Color.White
)

/** A soft white card with an emoji heading. */
@Composable
fun SectionCard(title: String, emoji: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, SakuraSoft)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$emoji  $title", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Plum)
            content()
        }
    }
}

@Composable
fun Pill(text: String) {
    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.85f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Plum
        )
    }
}

@Composable
fun PinkButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Sakura, contentColor = Color.White)
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = LavenderSoft, contentColor = Plum)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Plum)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = PlumSoft)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Sakura,
                uncheckedTrackColor = LavenderSoft
            )
        )
    }
}

@Composable
fun LabeledSlider(label: String, value: Int, lo: Int, hi: Int, unit: String, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.SemiBold, color = Plum)
            Text("$value $unit", color = Sakura, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = lo.toFloat()..hi.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Sakura,
                activeTrackColor = Sakura,
                inactiveTrackColor = SakuraSoft
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipChoice(label: String, items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, color = Plum)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { i, s ->
                FilterChip(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    label = { Text(s) },
                    shape = RoundedCornerShape(50),
                    colors = chipColors()
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Swatches(label: String, colors: IntArray, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, color = Plum)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEachIndexed { i, c ->
                val on = i == selected
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (on) 3.dp else 1.dp, if (on) Plum else Color(0x22000000), CircleShape)
                        .clickable { onSelect(i) }
                )
            }
        }
    }
}
