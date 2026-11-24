/**
 * Compose screens: Check (with the optional auto-check switch), verdict, History, Family Circle
 * and Engine.
 */
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.chaukas.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.chaukas.guard.Guard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chaukas.ChaukasViewModel
import app.chaukas.ai.ModelState
import app.chaukas.ai.PrivacyMode
import app.chaukas.ai.Source
import app.chaukas.ai.Verdict
import app.chaukas.data.Repository
import app.chaukas.ui.theme.Chaukas
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector, val tint: Color, val ink: Color) {
    CHECK("Check", Icons.Outlined.Shield, Chaukas.Lilac100, Chaukas.Lilac700),
    HISTORY("History", Icons.Outlined.History, Chaukas.Sky100, Chaukas.Sky600),
    FAMILY("Family", Icons.Outlined.Groups, Chaukas.Peach100, Color(0xFF9A5320)),
    ENGINE("Engine", Icons.Outlined.Memory, Chaukas.Butter100, Chaukas.Butter700),
}

@Composable
fun ChaukasRoot(vm: ChaukasViewModel) {
    var tab by remember { mutableStateOf(Tab.CHECK) }
    Scaffold(
        containerColor = Chaukas.Paper,
        bottomBar = {
            NavigationBar(containerColor = Chaukas.Surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = t.tint, selectedIconColor = t.ink, selectedTextColor = Chaukas.Ink, unselectedIconColor = Chaukas.Ink3, unselectedTextColor = Chaukas.Ink3),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.CHECK -> CheckScreen(vm)
                Tab.HISTORY -> HistoryScreen()
                Tab.FAMILY -> FamilyScreen(vm)
                Tab.ENGINE -> EngineScreen(vm)
            }
        }
    }
}

@Composable
private fun Eyebrow(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Chaukas.Ink3)

@Composable
private fun ChaukasCard(modifier: Modifier = Modifier, color: Color = Chaukas.Surface, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = color, border = BorderStroke(1.dp, Chaukas.Line)) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color, icon: ImageVector? = null) {
    Row(Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(5.dp)) }
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/* ------------------------------ CHECK ------------------------------ */

/** Languages the Speak button can listen in. Each writes in its own script. */
private val SPEAK_LANGS = listOf("en-IN" to "English", "hi-IN" to "हिन्दी", "bn-IN" to "বাংলা", "ta-IN" to "தமிழ்", "te-IN" to "తెలుగు", "mr-IN" to "मराठी")

/**
 * Speak: say the message and the words appear in the box as you talk, in the language picked
 * next to the button. Uses the phone's own speech recogniser, and stops by itself when you pause.
 */
@Composable
private fun SpeakRow(current: String, setText: (String) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("chaukas.speak", android.content.Context.MODE_PRIVATE) }
    var lang by remember { mutableStateOf(prefs.getString("lang", null) ?: if (java.util.Locale.getDefault().language == "hi") "hi-IN" else "en-IN") }
    var listening by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var pickLang by remember { mutableStateOf(false) }
    val recognizer = remember { mutableStateOf<android.speech.SpeechRecognizer?>(null) }
    DisposableEffect(Unit) { onDispose { recognizer.value?.destroy() } }

    fun listen(base: String) {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(ctx)) {
            note = "This phone has no speech recogniser. Install or update the Google app, then try again."
            return
        }
        recognizer.value?.destroy()
        val r = android.speech.SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer.value = r
        val join = { said: String -> if (base.isBlank()) said else "${base.trimEnd()}\n$said" }
        r.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true; note = null }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            override fun onPartialResults(partial: android.os.Bundle?) {
                partial?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { setText(join(it)) }
            }
            override fun onResults(results: android.os.Bundle?) {
                listening = false
                results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { setText(join(it)) }
            }
            override fun onError(error: Int) {
                listening = false
                note = when (error) {
                    android.speech.SpeechRecognizer.ERROR_NO_MATCH, android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch any words. Tap Speak and talk close to the phone."
                    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow the microphone for Chaukas, then try again."
                    android.speech.SpeechRecognizer.ERROR_NETWORK, android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speak needs internet for this language. Or download it offline in the Google app's voice settings."
                    android.speech.SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, android.speech.SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "This phone can't listen in that language yet. Try English or हिन्दी."
                    android.speech.SpeechRecognizer.ERROR_CLIENT -> null
                    else -> "Speak stopped. Please try again."
                }
            }
        })
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        listening = true
        r.startListening(intent)
    }

    var pendingBase by remember { mutableStateOf("") }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen(pendingBase) else note = "Allow the microphone for Chaukas to use Speak."
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (listening) {
                    recognizer.value?.stopListening()
                } else {
                    pendingBase = current
                    if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) listen(pendingBase)
                    else mic.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (listening) Chaukas.Lilac500 else Chaukas.Lilac100, contentColor = if (listening) Color.White else Chaukas.Lilac700),
        ) {
            Icon(if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic, null)
            Spacer(Modifier.width(8.dp))
            Text(if (listening) "Listening… tap to stop" else "Speak", fontSize = 16.sp)
        }
        Box {
            OutlinedButton(onClick = { pickLang = true }, modifier = Modifier.height(48.dp), shape = RoundedCornerShape(14.dp)) {
                Text(SPEAK_LANGS.firstOrNull { it.first == lang }?.second ?: "English", fontSize = 15.sp)
            }
            DropdownMenu(expanded = pickLang, onDismissRequest = { pickLang = false }) {
                SPEAK_LANGS.forEach { (tag, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        lang = tag
                        prefs.edit().putString("lang", tag).apply()
                        pickLang = false
                    })
                }
            }
        }
    }
    note?.let { Text(it, color = Chaukas.Ink2, fontSize = 13.sp) }
}

@Composable
private fun CheckScreen(vm: ChaukasViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val ready = vm.modelState == ModelState.Available
        Pill(if (ready) "Gemini Nano on this phone" else "Cloud fallback ready", if (ready) Chaukas.Mint100 else Chaukas.Sky100, if (ready) Chaukas.Mint700 else Chaukas.Sky600, if (ready) Icons.Outlined.PhoneAndroid else Icons.Outlined.Cloud)
        Text("Got a message that feels off?", style = MaterialTheme.typography.displaySmall)
        Text("Share it to Chaukas from any app, or paste it here.", color = Chaukas.Ink2)
        AutoCheckCard()

        if (vm.trend.isNotBlank()) {
            ChaukasCard(color = Chaukas.Rose50) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocalFireDepartment, null, tint = Chaukas.Rose700, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(vm.trend, color = Chaukas.Rose700, fontSize = 13.sp)
                }
            }
        }

        OutlinedTextField(
            value = vm.text,
            onValueChange = { vm.text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            placeholder = { Text("Paste a text, email or WhatsApp message…") },
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Chaukas.Lilac500, unfocusedBorderColor = Chaukas.Line, focusedContainerColor = Chaukas.Surface, unfocusedContainerColor = Chaukas.Surface),
        )
        SpeakRow(vm.text) { vm.text = it }
        val signals = vm.liveSignals
        if (signals.isNotEmpty() && vm.run == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                signals.forEach { Pill(it.label, Chaukas.Rose50, Chaukas.Rose700) }
            }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PrivacyMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = vm.mode == m,
                    onClick = { vm.mode = m },
                    shape = SegmentedButtonDefaults.itemShape(i, PrivacyMode.entries.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = Chaukas.Lilac100, activeContentColor = Chaukas.Lilac700),
                ) { Text(m.title, fontSize = 13.sp) }
            }
        }
        Text(vm.mode.line, color = Chaukas.Ink2, fontSize = 13.sp)

        Button(onClick = vm::check, enabled = !vm.busy && vm.text.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(Icons.Outlined.VerifiedUser, null)
            Spacer(Modifier.width(8.dp))
            Text(if (vm.busy) "Checking…" else "Check it", fontSize = 16.sp)
        }

        vm.error?.let { ChaukasCard(color = Chaukas.Rose50) { Text(it, color = Chaukas.Rose700) } }

        AnimatedVisibility(vm.run != null, enter = fadeIn() + slideInVertically { it / 6 }) {
            vm.run?.let { ResultView(vm, it) }
        }
    }
}

/**
 * The opt-in switch for automatic warnings. Shows a plain-language disclosure first, then sends
 * the user to the system page to grant notification access. Reflects the real access state
 * every time the screen comes back into view.
 */
@Composable
private fun AutoCheckCard() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Guard.isOn(ctx)) }
    var access by remember { mutableStateOf(Guard.hasAccess(ctx)) }
    var explain by remember { mutableStateOf(false) }
    var showSafe by remember { mutableStateOf(Guard.showSafe(ctx)) }
    var recent by remember { mutableStateOf(Guard.recent(ctx)) }
    var counts by remember { mutableStateOf(Guard.counts(ctx)) }
    var watchSms by remember { mutableStateOf(Guard.watches(ctx, "sms")) }
    var watchChat by remember { mutableStateOf(Guard.watches(ctx, "chat")) }
    LifecycleResumeEffect(Unit) {
        access = Guard.hasAccess(ctx)
        recent = Guard.recent(ctx)
        counts = Guard.counts(ctx)
        // Refresh the list live while the app is open and a message arrives.
        val prefs = ctx.getSharedPreferences("chaukas.guard", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == "log") recent = Guard.recent(ctx)
            if (k == "checked") counts = Guard.counts(ctx)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onPauseOrDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!access) Guard.openAccess(ctx)
    }
    val active = on && access
    ChaukasCard(color = if (active) Chaukas.Mint100 else Chaukas.Surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (active) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone, null, tint = if (active) Chaukas.Mint700 else Chaukas.Lilac700)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Warn me automatically", fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        active -> when {
                            watchSms && watchChat -> "On. New SMS and WhatsApp messages are checked on this phone."
                            watchSms -> "On for SMS. Turn on WhatsApp below too."
                            watchChat -> "On for WhatsApp. Turn on SMS below too."
                            else -> "Pick SMS, WhatsApp or both below."
                        }
                        on -> "Almost there. Allow notification access for Chaukas."
                        else -> "No copy-paste. Get a heads-up when a new message looks like a scam."
                    },
                    color = Chaukas.Ink2, fontSize = 13.sp,
                )
            }
            Switch(checked = active, onCheckedChange = { want ->
                if (want) explain = true else { on = false; Guard.setOn(ctx, false) }
            })
        }
        if (on && !access) {
            TextButton(onClick = { Guard.openAccess(ctx) }) { Text("Open settings") }
        }
        if (active) {
            Spacer(Modifier.height(6.dp))
            WatchRow("SMS messages", "Messages, Truecaller and your phone's SMS app", watchSms) { watchSms = it; Guard.setWatches(ctx, "sms", it) }
            WatchRow("WhatsApp", "Also WhatsApp Business, Telegram and Gmail", watchChat) { watchChat = it; Guard.setWatches(ctx, "chat", it) }
            SelfTest()
            RecentChecks(recent, counts, showSafe) { want -> showSafe = want; Guard.setShowSafe(ctx, want) }
        }
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            icon = { Icon(Icons.Outlined.Lock, null) },
            title = { Text("Check new messages for you?") },
            text = {
                Text(
                    "Chaukas will read the text of new message notifications from SMS, WhatsApp and similar apps, " +
                        "only to look for scam signs. This happens entirely on your phone. The message itself is never stored or sent " +
                        "anywhere unless you tap a warning to run a full check. Only the app name and time of each check are kept, so you can see it working. You can turn it off any time.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    explain = false
                    on = true
                    Guard.setOn(ctx, true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else if (!access) Guard.openAccess(ctx)
                }) { Text("Turn on") }
            },
            dismissButton = { TextButton(onClick = { explain = false }) { Text("Not now") } },
        )
    }
}

/**
 * "Is it really reading my messages?" Posts a quiet test notification and waits for the checker
 * to see it. If it doesn't, offers the fixes that work on Xiaomi, Oppo, Vivo and Samsung phones.
 */
@Composable
private fun SelfTest() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // null = not tested yet, true = working, false = not receiving
    var result by remember { mutableStateOf<Boolean?>(null) }
    var testing by remember { mutableStateOf(false) }
    var noNotify by remember { mutableStateOf(false) }
    val connected = Guard.isConnected(ctx)
    Spacer(Modifier.height(8.dp))
    Surface(shape = RoundedCornerShape(14.dp), color = Chaukas.Surface, border = BorderStroke(1.dp, Chaukas.Mint700.copy(alpha = 0.15f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Is it reading my messages?", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (connected) "Message checker: connected" else "Message checker: not connected yet",
                        fontSize = 12.sp, color = if (connected) Chaukas.Mint700 else Chaukas.Ink2,
                    )
                }
                Button(enabled = !testing, onClick = {
                    val started = System.currentTimeMillis()
                    noNotify = false
                    if (!Guard.sendSelfTest(ctx)) {
                        noNotify = true
                        result = false
                    } else {
                        testing = true
                        scope.launch {
                            var tries = 0
                            while (tries < 10 && Guard.lastSelfTest(ctx) < started) {
                                kotlinx.coroutines.delay(400)
                                tries++
                            }
                            result = Guard.lastSelfTest(ctx) >= started
                            testing = false
                        }
                    }
                }) { Text(if (testing) "Testing…" else "Test it now") }
            }
            when (result) {
                true -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = Chaukas.Mint700, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Working. New SMS and WhatsApp messages reach Chaukas.", fontSize = 13.sp, color = Chaukas.Mint700)
                }
                false -> Column(Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = Chaukas.Rose700, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (noNotify) "Notifications for Chaukas are off, so the test can't run. Turn them on in App settings."
                            else "Not receiving. Your phone is stopping Chaukas from reading messages.",
                            fontSize = 13.sp, color = Chaukas.Rose700,
                        )
                    }
                    Text(
                        "Try these in order, then test again:\n" +
                            "1. Notification access: turn Chaukas off and on again.\n" +
                            "2. App settings: Autostart on, Battery saver set to No restrictions (Xiaomi, Redmi, Poco, Oppo, Vivo).\n" +
                            "3. Lock Chaukas in Recent apps so the phone doesn't close it.\n" +
                            "Muted WhatsApp chats show no notification, so they can't be checked.",
                        fontSize = 12.sp, color = Chaukas.Ink2, modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { Guard.openAccess(ctx) }) { Text("Access") }
                        TextButton(onClick = { Guard.reconnect(ctx); result = null }) { Text("Reconnect") }
                        TextButton(onClick = { Guard.openAppSettings(ctx) }) { Text("App settings") }
                    }
                }
                null -> {}
            }
        }
    }
}

/**
 * Proof that auto-check is working: how many messages were checked today, the warnings of the
 * last 3 days (messages that looked fine are only counted), and an optional silent note for them.
 */
@Composable
private fun WatchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 12.sp, color = Chaukas.Ink2)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

/**
 * Proof that auto-check is working: every message checked today (SMS and WhatsApp apart) and in
 * total, the warnings of the last 3 days with their reasons, and a fix for phones that stop it.
 */
@Composable
private fun RecentChecks(recent: List<Guard.Entry>, counts: Guard.Counts, showSafe: Boolean, onShowSafe: (Boolean) -> Unit) {
    val time = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT) }
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = Chaukas.Mint700.copy(alpha = 0.15f))
    Spacer(Modifier.height(10.dp))
    Text(
        if (counts.today == 0) "No messages checked yet today."
        else "Today: ${counts.today} checked (SMS ${counts.todaySms} · WhatsApp ${counts.todayChat}) · ${counts.todayWarned} warning${if (counts.todayWarned == 1) "" else "s"}",
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
    )
    Text(
        "All time: ${counts.total} checked · ${counts.totalWarned} warnings. Counts never go down, even if you delete a message.",
        fontSize = 12.sp, color = Chaukas.Ink2,
    )
    if (recent.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text("Warnings, last 3 days", fontSize = 12.sp, color = Chaukas.Ink2)
    }
    recent.take(6).forEach { e ->
        val color = if (e.isScam) Chaukas.Rose700 else Chaukas.Butter700
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Warning, null, tint = color, modifier = Modifier.size(16.dp).padding(top = 2.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("${e.app} · ${time.format(java.util.Date(e.at))}", fontSize = 13.sp)
                if (e.reasons.isNotBlank()) Text(e.reasons, fontSize = 12.sp, color = Chaukas.Ink2)
            }
            Text("${if (e.isScam) "Scam" else "Careful"} · ${e.score}", fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
    if (counts.total > 0 && counts.lastChatAt == 0L) {
        Spacer(Modifier.height(8.dp))
        Text(
            "No WhatsApp message seen yet. On Xiaomi, Redmi or Poco: Settings → Apps → Chaukas → Autostart on, and Battery saver → No restrictions. " +
                "On other phones, turn off battery optimisation for Chaukas. Muted chats send no notification, so they can't be checked.",
            fontSize = 12.sp, color = Chaukas.Butter700,
        )
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Also show a silent note when a message looks fine", fontSize = 13.sp, color = Chaukas.Ink2, modifier = Modifier.weight(1f))
        Switch(checked = showSafe, onCheckedChange = onShowSafe)
    }
}

@Composable
private fun ResultView(vm: ChaukasViewModel, run: app.chaukas.ai.AnalysisRun) {
    val a = run.analysis
    val (bg, fg) = when (a.verdict) {
        Verdict.SAFE -> Chaukas.Mint50 to Chaukas.Mint700
        Verdict.SUSPICIOUS -> Chaukas.Butter50 to Chaukas.Butter700
        Verdict.SCAM -> Chaukas.Rose50 to Chaukas.Rose700
    }
    var shared by remember(run.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChaukasCard(color = bg) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dial(a.riskScore, fg)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(a.verdict.label, style = MaterialTheme.typography.headlineSmall, color = fg)
                    Spacer(Modifier.height(4.dp))
                    Text(a.summary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("${run.source.label} · ${run.latencyMs} ms", if (run.source == Source.ON_DEVICE) Chaukas.Mint100 else Chaukas.Sky100, if (run.source == Source.ON_DEVICE) Chaukas.Mint700 else Chaukas.Sky600)
                if (run.redactions > 0) Pill("${run.redactions} details hidden", Chaukas.Lilac100, Chaukas.Lilac700, Icons.Outlined.VisibilityOff)
            }
        }
        if (a.redFlags.isNotEmpty()) {
            ChaukasCard {
                Text("Red flags", style = MaterialTheme.typography.titleMedium)
                a.redFlags.forEachIndexed { i, f ->
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = Chaukas.Rose50) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("${i + 1}. ${f.label}", fontWeight = FontWeight.SemiBold)
                            if (f.evidence.isNotBlank()) Text("“${f.evidence}”", color = Chaukas.Rose700, fontFamily = MaterialTheme.typography.displaySmall.fontFamily)
                            Text(f.why, color = Chaukas.Ink2, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        if (a.nextSteps.isNotEmpty()) {
            ChaukasCard(color = Chaukas.Mint50) {
                Text("What to do now", style = MaterialTheme.typography.titleMedium)
                a.nextSteps.forEach { s ->
                    Row(Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = Chaukas.Mint700, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text(s)
                    }
                }
            }
        }
        // Only risky messages (65 or more) go to the family group.
        if (a.verdict != Verdict.SAFE && a.riskScore >= 65) {
            OutlinedButton(onClick = { shared = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.IosShare, null); Spacer(Modifier.width(8.dp))
                Text(if (shared) "Open the Family tab to share it" else "Tell my circle")
            }
        }
        // One tap to WhatsApp: the warning is written, personal details removed and links made unclickable.
        if (a.verdict != Verdict.SAFE && a.riskScore >= 65) {
            val ctx = LocalContext.current
            OutlinedButton(onClick = {
                val quote = app.chaukas.ai.Redactor.redact(run.input).text.replace(Regex("\\s+"), " ").take(160)
                val text = listOf(
                    "⚠️ ${if (a.verdict == Verdict.SCAM) "Likely scam" else "Be careful"}: ${a.category} (risk ${a.riskScore}/100)",
                    "\"${defang(quote)}\"",
                    defang(app.chaukas.ai.Redactor.redact(a.summary).text),
                    "Don't tap links, share OTPs or send money. If you already did, call 1930 (India) or your bank now.",
                    "Checked with Chaukas",
                ).joinToString("\n")
                val uri = android.net.Uri.parse("https://wa.me/?text=" + android.net.Uri.encode(text))
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Chat, null); Spacer(Modifier.width(8.dp))
                Text("Send to WhatsApp")
            }
        }
    }
}

@Composable
private fun Dial(score: Int, color: Color) {
    val p by animateFloatAsState(score / 100f, tween(900), label = "dial")
    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            drawArc(color.copy(alpha = 0.15f), 135f, 270f, false, Offset(inset, inset), Size(size.width - stroke.width, size.height - stroke.width), style = stroke)
            drawArc(color, 135f, 270f * p, false, Offset(inset, inset), Size(size.width - stroke.width, size.height - stroke.width), style = stroke)
        }
        Text("${(p * 100).toInt()}", style = MaterialTheme.typography.headlineSmall)
    }
}

/* ------------------------------ HISTORY ------------------------------ */

@Composable
private fun HistoryScreen() {
    val items by remember { Repository.checks() }.collectAsState(initial = emptyList())
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Eyebrow("Synced across your devices")
            Text("History", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
        }
        if (items.isEmpty()) item { Text("Checks you make on any device appear here once you are signed in.", color = Chaukas.Ink2) }
        items(items, key = { it.id }) { c ->
            val (bg, fg) = when (c.verdict) { "scam" -> Chaukas.Rose100 to Chaukas.Rose700; "suspicious" -> Chaukas.Butter100 to Chaukas.Butter700; else -> Chaukas.Mint100 to Chaukas.Mint700 }
            ChaukasCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
                        Text("${c.riskScore}", color = fg, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.verdict.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                        Text(c.excerpt, color = Chaukas.Ink2, fontSize = 13.sp, maxLines = 2)
                    }
                }
            }
        }
    }
}

/* ------------------------------ FAMILY ------------------------------ */

@Composable
private fun FamilyScreen(vm: ChaukasViewModel) {
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(Repository.uid != null) }
    var name by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("Look out for each other")
        Text("Family Circle", style = MaterialTheme.typography.displaySmall)
        if (!signedIn) {
            ChaukasCard(color = Color(0xFFFFF6EE)) {
                Text("What should your family call you?", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))
                Text("Choose carefully. Once you confirm your name, it cannot be changed later.", color = Chaukas.Ink2, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                if (!confirming) {
                    OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. Grandma Rose") }, shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { confirming = true }, enabled = name.isNotBlank()) { Text("Continue") }
                } else {
                    Text("Your family will see you as ${name.trim()}. Is that right?", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { confirming = false }) { Text("Edit") }
                        Button(onClick = { scope.launch { runCatching { Repository.signIn(name.trim()) }.onSuccess { signedIn = true }.onFailure { msg = it.message } } }) { Text("Confirm") }
                    }
                }
                msg?.let { Text(it, color = Chaukas.Rose700, fontSize = 13.sp) }
            }
            return@Column
        }
        val circles by remember { Repository.circles() }.collectAsState(initial = emptyList())
        ChaukasCard(color = Chaukas.Lilac50) {
            Text("Join a circle", style = MaterialTheme.typography.titleMedium)
            Text("Create circles in the web app, then enter the 6-letter code here.", color = Chaukas.Ink2, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(code, { code = it.uppercase().take(6) }, Modifier.weight(1f), placeholder = { Text("ABC123") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { scope.launch { runCatching { Repository.joinCircle(code) }.onSuccess { msg = "Joined!"; code = "" }.onFailure { msg = if (it is Repository.GroupLimit) it.message else "No circle with that code" } } }, enabled = code.length == 6) { Text("Join") }
            }
        }
        msg?.let { Text(it, color = Chaukas.Ink2) }
        circles.forEach { c ->
            val alerts by remember(c.id) { Repository.alerts(c.id) }.collectAsState(initial = emptyList())
            Text(c.name, style = MaterialTheme.typography.headlineSmall)
            vm.run?.let { run ->
                OutlinedButton(onClick = { scope.launch { runCatching { Repository.shareAlert(c.id, run, "Watch out for this one") }.onSuccess { msg = "Shared with ${c.name}" } } }) {
                    Icon(Icons.Outlined.IosShare, null); Spacer(Modifier.width(6.dp)); Text("Share my last check here")
                }
            }
            if (alerts.isEmpty()) Text("All quiet.", color = Chaukas.Ink3)
            alerts.forEach { a ->
                ChaukasCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.byName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Pill("${a.verdict} · ${a.riskScore}", if (a.verdict == "scam") Chaukas.Rose50 else Chaukas.Butter50, if (a.verdict == "scam") Chaukas.Rose700 else Chaukas.Butter700)
                    }
                    if (a.note.isNotBlank()) Text(a.note, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                    Text(a.excerpt, color = Chaukas.Ink2, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/* ------------------------------ ENGINE ------------------------------ */

/**
 * Debug builds only: the App Check debug token for this phone, with a Copy button. Register it
 * in the Firebase console (App Check → Apps → Android app → Manage debug tokens) so cloud
 * checks are accepted from this development build.
 */
@Composable
private fun DebugTokenCard() {
    val ctx = LocalContext.current
    var token by remember { mutableStateOf(app.chaukas.DebugToken.read(ctx)) }
    LaunchedEffect(Unit) {
        // The token is created a moment after launch; look again for a few seconds.
        repeat(10) {
            if (token != null) return@LaunchedEffect
            kotlinx.coroutines.delay(1000)
            token = app.chaukas.DebugToken.read(ctx)
        }
    }
    ChaukasCard(color = Chaukas.Butter100) {
        Text("App Check debug token", style = MaterialTheme.typography.titleMedium)
        Text(
            "Only in this development build. Add it in the Firebase console: App Check → Apps → your Android app → ⋮ → Manage debug tokens → Add debug token.",
            color = Chaukas.Ink2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        val t = token
        if (t == null) {
            Text("Creating the token…", color = Chaukas.Ink3, fontSize = 13.sp)
        } else {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(t, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("App Check debug token", t))
                android.widget.Toast.makeText(ctx, "Token copied", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Copy token") }
        }
    }
}

@Composable
private fun EngineScreen(vm: ChaukasViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("Under the hood")
        Text("Engine", style = MaterialTheme.typography.displaySmall)
        val (title, body, color) = when (val s = vm.modelState) {
            ModelState.Checking -> Triple("Checking…", "Asking AICore about Gemini Nano.", Chaukas.Sky100)
            ModelState.Available -> Triple("Gemini Nano is ready", "Checks run on this phone, privately, even offline.", Chaukas.Mint100)
            ModelState.Downloadable -> Triple("Ready to download", "Download Gemini Nano once. It is shared with other apps through AICore.", Chaukas.Butter100)
            is ModelState.Downloading -> Triple("Downloading… ${s.fraction?.let { "${(it * 100).toInt()}%" } ?: ""}", "Smart mode uses the cloud with redaction until it finishes.", Chaukas.Sky100)
            ModelState.Unavailable -> Triple("Not available on this phone", "Chaukas will use the cloud with personal details removed.", Chaukas.Rose100)
        }
        ChaukasCard(color = color) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body, color = Chaukas.Ink2, modifier = Modifier.padding(top = 4.dp))
            (vm.modelState as? ModelState.Downloading)?.fraction?.let {
                Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = Chaukas.Lilac500)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.modelState == ModelState.Downloadable) Button(onClick = { vm.download() }) { Text("Download Gemini Nano") }
                OutlinedButton(onClick = { vm.refreshModel() }) { Text("Re-check") }
            }
        }
        if (app.chaukas.BuildConfig.DEBUG) DebugTokenCard()
        ChaukasCard {
            Text("Privacy", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Remove personal details before using the cloud")
                    Text("Numbers, emails, cards and codes are swapped for placeholders on your phone.", color = Chaukas.Ink3, fontSize = 12.sp)
                }
                Switch(vm.redact, { vm.redact = it }, colors = SwitchDefaults.colors(checkedTrackColor = Chaukas.Lilac500))
            }
        }
    }
}

/** Makes links in a forwarded warning unclickable: http://bad.top/x becomes bad[.]top/x. */
private fun defang(text: String): String =
    Regex("""\b(?:https?://|www\.)(\S+)""", RegexOption.IGNORE_CASE).replace(text) { it.groupValues[1].replace(".", "[.]") }
