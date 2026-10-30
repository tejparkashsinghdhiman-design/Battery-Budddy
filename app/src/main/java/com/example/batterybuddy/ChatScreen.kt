package com.example.batterybuddy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val (apiKey, setApiKey) = rememberPref("api_key", "")
    val (name, _) = rememberPref("char_name", "")
    var keyInput by remember { mutableStateOf("") }
    val msgs = remember { mutableStateListOf<Chat.Msg>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val who = if (name.isBlank() || name == "My picture") "Battery Buddy" else name

    LaunchedEffect(msgs.size, sending) {
        val n = msgs.size + (if (sending) 1 else 0)
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        input = ""
        error = null
        msgs.add(Chat.Msg(true, text))
        sending = true
        scope.launch {
            val system = Chat.systemPrompt(name, BuddyState.snapshot(ctx))
            val history = msgs.toList()
            val result = withContext(Dispatchers.IO) { runCatching { Chat.send(apiKey, system, history) } }
            result.onSuccess { msgs.add(Chat.Msg(false, it)) }
            result.onFailure {
                msgs.removeAt(msgs.lastIndex) // take the unanswered message back so the chat stays in turn
                input = text
                error = it.message ?: "Something went wrong."
            }
            sending = false
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Chat with $who 💬", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Plum)

        if (apiKey.isBlank()) {
            SectionCard("Connect Claude", "🔑") {
                Text(
                    "Chatting uses Claude through your own Anthropic API key (get one at console.anthropic.com). " +
                        "Messages go straight from your phone to Anthropic, each one costs a small amount on your account, " +
                        "and the key is stored only on this phone.",
                    color = PlumSoft,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Paste your API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(24.dp)
                )
                PinkButton("Save key") {
                    if (keyInput.isNotBlank()) {
                        setApiKey(keyInput.trim())
                        keyInput = ""
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    if (msgs.isEmpty()) {
                        Text("Say hi! I can chat, and I know how your battery is doing 💖", color = PlumSoft)
                    }
                }
                items(msgs.size) { i -> ChatBubble(msgs[i].text, msgs[i].fromUser) }
                if (sending) item { ChatBubble("…", false) }
            }
            if (error != null) Text(error ?: "", color = Color(0xFFB3261E), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message $who") },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Sakura,
                        unfocusedBorderColor = SakuraSoft,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
                Button(
                    onClick = { send() },
                    enabled = !sending,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Sakura, contentColor = Color.White)
                ) { Text("Send", fontWeight = FontWeight.Bold) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Clear chat") { msgs.clear(); error = null }
                SoftButton("Remove key") { setApiKey(""); msgs.clear(); error = null }
            }
        }
    }
}

@Composable
private fun ChatBubble(text: String, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) Sakura else Color.White,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text,
                color = if (mine) Color.White else Plum,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
