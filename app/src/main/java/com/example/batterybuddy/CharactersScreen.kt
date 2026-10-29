package com.example.batterybuddy

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CharactersScreen(top: Feed, search: Feed, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val prefs = remember { Buddy.prefs(ctx) }
    val (current, _) = rememberPref("char_name", "")
    val (hideAdult, _) = rememberPref("hide_adult", true)

    var mode by rememberSaveable { mutableStateOf(0) } // 0 = Top 200, 1 = search results, 2 = my buddies
    var query by rememberSaveable { mutableStateOf("") }
    var applying by remember { mutableStateOf<Long?>(null) }
    var mine by remember { mutableStateOf(Saved.load(prefs)) }

    LaunchedEffect(Unit) { if (top.items.isEmpty() && top.hasMore) top.loadNext(hideAdult) }

    fun apply(c: Chara, saved: Boolean) {
        if (applying != null) return
        scope.launch {
            applying = c.id
            val ok = if (saved) Saved.fromSaved(ctx, c) else Saved.fromWeb(ctx, c)
            applying = null
            mine = Saved.load(prefs)
            snackbar.showSnackbar(if (ok) "${c.name} is on duty! 💖" else "Couldn’t load ${c.name}. Try again?")
        }
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        search.reset(q)
        mode = 1
        scope.launch { search.loadNext(hideAdult) }
    }

    fun surprise() {
        val pool = top.items
        if (pool.isEmpty()) {
            scope.launch { snackbar.showSnackbar("Give the list a second to load first 🌸") }
            return
        }
        apply(pool.random(), false)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val ok = Saved.importPicture(ctx, uri)
            snackbar.showSnackbar(if (ok) "Your picture is on duty! 💖" else "Couldn’t load that picture.")
        }
    }

    val list: List<Chara> = when (mode) {
        0 -> top.items
        1 -> search.items
        else -> mine
    }
    val feed: Feed? = when (mode) {
        0 -> top
        1 -> search
        else -> null
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pick your buddy 🌸", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Plum)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search any character… Dazai, Johan, Zero Two") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                trailingIcon = { Text("🔍", fontSize = 20.sp, modifier = Modifier.clickable { runSearch() }.padding(10.dp)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Sakura,
                    unfocusedBorderColor = SakuraSoft,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = mode == 0, onClick = { mode = 0 }, label = { Text("🏆 Top 200") },
                    shape = RoundedCornerShape(50), colors = chipColors()
                )
            }
            item {
                FilterChip(
                    selected = mode == 1, onClick = { mode = 1 }, label = { Text("🔎 Results") },
                    shape = RoundedCornerShape(50), colors = chipColors()
                )
            }
            item {
                FilterChip(
                    selected = mode == 2, onClick = { mine = Saved.load(prefs); mode = 2 }, label = { Text("💖 My buddies") },
                    shape = RoundedCornerShape(50), colors = chipColors()
                )
            }
            item {
                FilterChip(
                    selected = false, onClick = { surprise() }, label = { Text("🎲 Surprise me") },
                    shape = RoundedCornerShape(50), colors = chipColors()
                )
            }
            item {
                FilterChip(
                    selected = false, onClick = { picker.launch(arrayOf("image/*")) }, label = { Text("📷 My photo") },
                    shape = RoundedCornerShape(50), colors = chipColors()
                )
            }
        }
        Text(
            when (mode) {
                0 -> "The most-loved characters on AniList. Tap one to make them your buddy."
                1 -> "Search results. Tap one to make them your buddy."
                else -> "Everyone you have picked before. These work offline."
            },
            fontSize = 13.sp,
            color = PlumSoft,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list, key = { "$mode-${it.id}" }) { c ->
                val model: Any = if (mode == 2) File(ctx.filesDir, "col_${c.id}.png") else c.small
                CharaCard(
                    c = c,
                    model = model,
                    selected = current.isNotEmpty() && c.name == current,
                    busy = applying == c.id,
                    onClick = { apply(c, mode == 2) }
                )
            }
            if (mode == 1 && search.query.isNullOrBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Type a name above and tap 🔍", color = PlumSoft, modifier = Modifier.padding(16.dp))
                }
            }
            if (mode == 2 && mine.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No saved buddies yet. Pick a character and they will show up here 💖",
                        color = PlumSoft,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            if (feed != null && !(mode == 1 && feed.query.isNullOrBlank())) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FeedFooter(feed) { scope.launch { feed.loadNext(hideAdult) } }
                }
            }
        }
    }
}

@Composable
private fun CharaCard(c: Chara, model: Any, selected: Boolean, busy: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Sakura else SakuraSoft)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = model,
                    contentDescription = c.name,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(LavenderSoft),
                    error = ColorPainter(LavenderSoft),
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)
                )
                if (c.rank > 0) {
                    Surface(
                        color = Sakura,
                        shape = RoundedCornerShape(bottomEnd = 14.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            "#${c.rank}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (selected) Text("💖", modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        color = Sakura,
                        strokeWidth = 3.dp
                    )
                }
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Plum)
                Text(c.series, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = PlumSoft)
            }
        }
    }
}

@Composable
private fun FeedFooter(feed: Feed, onMore: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            feed.loading -> {
                CircularProgressIndicator(color = Sakura, modifier = Modifier.size(32.dp))
                Text("Finding cuties…", color = PlumSoft)
            }
            feed.error != null -> {
                Text(feed.error ?: "", color = PlumSoft)
                SoftButton("Try again") { onMore() }
            }
            feed.hasMore -> SoftButton("Load more ✨") { onMore() }
            feed.items.isEmpty() -> Text("Nobody found 😢 Try another spelling?", color = PlumSoft)
            else -> Text("That’s everyone! 🎉", color = PlumSoft)
        }
    }
}
