/**
 * Compose screens: Check, verdict Result, History, Family Circle and Engine.
 */
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.chaukas.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import app.chaukas.data.I18n
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.chaukas.ChaukasViewModel
import app.chaukas.ai.AnalysisRun
import app.chaukas.ai.ModelState
import app.chaukas.ai.PrivacyMode
import app.chaukas.ai.Source
import app.chaukas.ai.Verdict
import app.chaukas.data.Circle
import app.chaukas.data.CircleAlert
import app.chaukas.data.Invite
import app.chaukas.data.InviteUtils
import app.chaukas.data.PersonInfo
import app.chaukas.data.QrCodeView
import app.chaukas.data.QrFinding
import app.chaukas.data.RegionalVerdict
import app.chaukas.data.Repository
import app.chaukas.data.SyncedCheck
import app.chaukas.data.categoryLabel
import app.chaukas.data.excerpt
import app.chaukas.data.historyCsv
import app.chaukas.data.historyInsights
import app.chaukas.data.historyStats
import app.chaukas.data.sourceLabel
import app.chaukas.data.timeAgo
import app.chaukas.guard.Guard
import app.chaukas.ui.theme.Chaukas
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector, val tint: Color, val ink: Color) {
    CHECK("Check", Icons.Outlined.Shield, Chaukas.Lilac100, Chaukas.Lilac700),
    HISTORY("History", Icons.Outlined.History, Chaukas.Sky100, Chaukas.Sky600),
    FAMILY("Family", Icons.Outlined.Groups, Chaukas.Peach100, Chaukas.Peach700),
    DOJO("Dojo", Icons.Outlined.GpsFixed, Chaukas.Mint100, Chaukas.Mint700),
    ENGINE("Engine", Icons.Outlined.Memory, Chaukas.Butter100, Chaukas.Butter700),
}

@Composable
fun ChaukasRoot(vm: ChaukasViewModel) {
    var tab by remember { mutableStateOf(Tab.CHECK) }
    LaunchedEffect(vm.run, vm.busy) {
        if (vm.run != null || vm.busy) {
            tab = Tab.CHECK
        }
    }
    Scaffold(
        containerColor = Chaukas.Paper,
        bottomBar = {
            NavigationBar(containerColor = Chaukas.Surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(I18n.t(t.label, vm.language), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = t.tint,
                            selectedIconColor = t.ink,
                            selectedTextColor = Chaukas.Ink,
                            unselectedIconColor = Chaukas.Ink3,
                            unselectedTextColor = Chaukas.Ink3,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.CHECK -> CheckScreen(vm, onGoToFamily = { tab = Tab.FAMILY })
                Tab.HISTORY -> HistoryScreen(vm, onCheck = { tab = Tab.CHECK })
                Tab.FAMILY -> FamilyScreen(vm)
                Tab.DOJO -> DojoScreen(vm)
                Tab.ENGINE -> EngineScreen(vm)
            }

            // Pretty in-app notification banner when an alert arrives while app is in foreground
            vm.inAppAlert?.let { alert ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .align(Alignment.TopCenter),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!alert.text.isNullOrBlank()) {
                                    vm.text = alert.text
                                    tab = Tab.CHECK
                                    vm.check(isKnown = alert.isKnownContact, fromSender = alert.from.orEmpty())
                                }
                                vm.dismissInAppAlert()
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF1E1B26),
                        border = BorderStroke(1.dp, if (alert.isScam) Color(0xFFE53935) else Color(0xFF2F8F6B)),
                        shadowElevation = 10.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (alert.isScam) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (alert.isScam) Icons.Outlined.WarningAmber else Icons.Outlined.Shield,
                                    null,
                                    tint = if (alert.isScam) Color(0xFFC62828) else Color(0xFF2E7D32),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (alert.isScam) Color(0xFFC62828) else Color(0xFF2E7D32),
                                    ) {
                                        Text(
                                            if (alert.isScam) "SCAM ALERT ${alert.score ?: 85}%" else "CHECKED SAFE",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    if (!alert.from.isNullOrBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(alert.from, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    alert.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    alert.message,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { vm.dismissInAppAlert() },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Outlined.Close, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Eyebrow(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Chaukas.Ink3)

@Composable
private fun ChaukasCard(
    modifier: Modifier = Modifier,
    color: Color = Chaukas.Surface,
    borderColor: Color = Chaukas.Line,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = color,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color, icon: ImageVector? = null) {
    Row(
        Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Avatar(name: String, size: Dp = 32.dp) {
    val hues = listOf(Color(0xFFD8EEEB), Color(0xFFDCF3E6), Color(0xFFFFF0C7), Color(0xFFFFE2DC), Color(0xFFDDEBFB), Color(0xFFFFE8D4))
    val inks = listOf(Color(0xFF145651), Color(0xFF226B47), Color(0xFF845B00), Color(0xFF9C3524), Color(0xFF2B5F99), Color(0xFF8A4A17))
    val sum = name.fold(0) { acc, c -> acc + c.code }
    val idx = (if (sum < 0) -sum else sum) % hues.size
    val letter = name.trim().take(1).uppercase().ifEmpty { "?" }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(hues[idx]),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = inks[idx],
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.44f).sp,
        )
    }
}

/* ------------------------------ CHECK SCREEN ------------------------------ */

private val QUICK_LANGS = listOf(
    "English" to "English",
    "Hindi" to "हिन्दी",
    "Bengali" to "বাংলা",
    "Tamil" to "தமிழ்",
    "Telugu" to "తెలుగు",
    "Marathi" to "मराठी",
)

private val SAMPLES = listOf(
    Triple("Electricity bill (India)", "Power cut tonight", "Electricity bill SMS: Dear customer, your electricity power will be disconnected tonight at 9.30 PM because your previous month bill was not updated. Please immediately contact our electricity officer at 9876543210."),
    Triple("KYC update (India)", "KYC expired", "Dear SBI customer, your bank account has been suspended due to pending KYC verification. Please click the link to update your Aadhaar and PAN card immediately: https://sbi-kyc-update.xyz/login"),
    Triple("UPI refund (India)", "Refund on UPI", "upi://pay?pa=refundcenter@okhdfcbank&pn=Flipkart%20Refund&am=4999.00&tn=Flipkart%20Cashback%20Refund%20Approved"),
)

/**
 * Wobbling 3-layer animated orb matching Web's .thinking-orb (@keyframes orb).
 */
@Composable
private fun ThinkingOrb(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val transition = rememberInfiniteTransition(label = "orbTransition")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbP",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        val layers = listOf(
            Triple(0f, Color(0xFFDDD0FC), 0.85f), // brand lavender
            Triple(0.33f, Color(0xFFFFD9C7), 0.85f), // peach
            Triple(0.66f, Color(0xFFD0F4DE), 0.85f), // mint
        )
        layers.forEach { (offset, color, alpha) ->
            val phase = (p + offset) % 1f
            val rad = phase * 2f * Math.PI.toFloat()
            val dx = (kotlin.math.sin(rad) * 7f).dp
            val dy = (-kotlin.math.cos(rad) * 5f).dp
            val scale = 0.82f + 0.16f * kotlin.math.sin(rad + 0.5f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = dx, y = dy)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun CheckScreen(vm: ChaukasViewModel, onGoToFamily: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var shareRunToCircle by remember { mutableStateOf<AnalysisRun?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.checkImage(ctx, uri)
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraUri != null) {
            vm.checkImage(ctx, cameraUri!!)
        }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero Header matching Web CSS
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, Color(0xFFF6E2C8)),
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFFF1E3), Color(0xFFFDF6D8), Color(0xFFE6F5EC)),
                        ),
                    )
                    .padding(20.dp),
            ) {
                // Status pill
                val ready = vm.modelState == ModelState.Available
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, Chaukas.Line),
                    shadowElevation = 1.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (ready) Chaukas.Mint600 else Chaukas.Sky600),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (ready) "Gemini Nano on this phone" else "Cloud fallback ready",
                            fontSize = 12.sp,
                            color = Chaukas.Ink2,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = I18n.t("Is this message a scam?", vm.language),
                    style = MaterialTheme.typography.displaySmall,
                    color = Chaukas.Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = I18n.t("Speak it, paste it or add a screenshot. You get a clear answer in seconds, the reasons, and exactly what to do next.", vm.language),
                    color = Chaukas.Ink2,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )

                Spacer(Modifier.height(14.dp))
                // Quick languages row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(I18n.t("Language", vm.language), fontSize = 12.sp, color = Chaukas.Ink3, fontWeight = FontWeight.Medium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        QUICK_LANGS.forEach { (name, label) ->
                            val on = vm.language == name
                            Surface(
                                onClick = { vm.setLang(name) },
                                shape = CircleShape,
                                color = if (on) Chaukas.Brand600 else Color.White,
                                border = BorderStroke(1.dp, if (on) Chaukas.Brand600 else Chaukas.Line),
                                shadowElevation = 1.dp,
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (on) Color.White else Chaukas.Ink,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sleek compact Auto-scan toggle for SMS & WhatsApp & Scam wave trend banner
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AutoScanToggleBar(vm.language)

            // Trend banner
            val displayTrend = vm.trend.ifBlank { Repository.DEFAULT_TREND_BANNER }
            if (displayTrend.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Chaukas.Rose50,
                    border = BorderStroke(1.dp, Color(0xFFFDE1D3)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            shadowElevation = 0.5.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.LocalFireDepartment, null, tint = Chaukas.Rose700, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    I18n.t("This week", vm.language),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Chaukas.Rose600,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = I18n.t(displayTrend, vm.language),
                            color = Color(0xFF7B3F22),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Main Composer Card
        ChaukasCard {
            OutlinedTextField(
                value = vm.text,
                onValueChange = {
                    vm.text = it
                    if (it.contains("upi://") || it.startsWith("http://") || it.startsWith("https://")) {
                        vm.qrFinding = InviteUtils.inspectQr(it)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp),
                placeholder = { Text(I18n.t("Paste a text, email, WhatsApp or DM here…", vm.language), color = Chaukas.Ink4) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Chaukas.Brand300,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )

            // Try one sample chips (matching Web examples row)
            if (vm.text.isBlank() && vm.attachedImageUri == null && vm.attachedBitmap == null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(I18n.t("Or try one:", vm.language), fontSize = 12.sp, color = Chaukas.Ink3)
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // QR refund sample chip
                    Surface(
                        onClick = {
                            vm.text = I18n.t("Congratulations! Your Flipkart cashback refund of Rs 4,999 is approved. Scan this QR to receive the money in your account now.", vm.language)
                            vm.qrFinding = InviteUtils.inspectQr("upi://pay?pa=refundcenter@okhdfcbank&pn=Flipkart%20Refund&am=4999.00&tn=Flipkart%20Cashback%20Refund%20Approved")
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Chaukas.Rose100),
                        shadowElevation = 0.5.dp,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, null, tint = Chaukas.Rose600, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(I18n.t("QR \"refund\" (India)", vm.language), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Chaukas.Rose700)
                        }
                    }

                    // Other sample chips
                    val samples = listOf(
                        Triple("KYC update (India)", Chaukas.Rose600, "Dear SBI customer, your bank account has been suspended due to pending KYC verification. Please click the link to update your Aadhaar and PAN card immediately: https://sbi-kyc-update.xyz/login"),
                        Triple("UPI refund (India)", Chaukas.Rose600, "Hello sir, I sent Rs 5,000 to your Google Pay by mistake, it was for my mother hospital bill. Please return it, I have sent you a collect request, just enter your UPI PIN to approve. Please help 🙏"),
                        Triple("Electricity bill (India)", Chaukas.Rose600, "Dear customer, your electricity power will be disconnected tonight at 9.30 PM because your previous month bill was not updated. Please immediately contact our electricity officer at 9876543210."),
                        Triple("Job offer on WhatsApp", Chaukas.Butter600, "Hello! I am Linda from TalentBridge recruiting. Your profile matches a remote part-time role: 60-90 mins a day rating products, $300-$800/day. No experience needed. Interested? Add me on Telegram @linda_hr_tb"),
                        Triple("Real delivery update", Chaukas.Mint600, "Your Amazon order #113-4421987-2201 has been delivered to your front door. Track your package or report a problem in Your Orders at amazon.com/orders."),
                    )

                    samples.forEach { (lbl, dotColor, sampleText) ->
                        Surface(
                            onClick = {
                                vm.text = I18n.t(sampleText, vm.language)
                                vm.qrFinding = null
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Chaukas.Line),
                            shadowElevation = 0.5.dp,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                                Spacer(Modifier.width(6.dp))
                                Text(I18n.t(lbl, vm.language), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Chaukas.Ink)
                            }
                        }
                    }
                }
            }

            // Attached image thumbnail preview
            if (vm.attachedImageUri != null || vm.attachedBitmap != null) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Chaukas.Paper2)
                        .padding(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vm.attachedBitmap != null) {
                            Image(
                                bitmap = vm.attachedBitmap!!.asImageBitmap(),
                                contentDescription = "Captured photo",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(Icons.Outlined.Image, null, tint = Chaukas.Sky600, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (vm.attachedBitmap != null) I18n.t("Photo attached", vm.language) else I18n.t("Screenshot attached", vm.language),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            color = Chaukas.Ink,
                        )
                        IconButton(onClick = { vm.removeAttachedImage() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.Close, "Remove", tint = Chaukas.Ink2, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // QR Card if finding present
            vm.qrFinding?.let { qr ->
                Spacer(Modifier.height(8.dp))
                val isUpi = qr.kind == "upi"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isUpi) Chaukas.Rose50 else Chaukas.Sky50,
                    border = BorderStroke(1.dp, if (isUpi) Chaukas.Rose100 else Chaukas.Sky100),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.QrCodeScanner, null, tint = if (isUpi) Chaukas.Rose600 else Chaukas.Sky600)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isUpi) "UPI payment QR · decoded on this device" else "QR code decoded",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUpi) Chaukas.Rose700 else Chaukas.Sky600,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(qr.headline, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isUpi) Chaukas.Rose700 else Chaukas.Ink)
                        qr.details.forEach { (k, v) ->
                            Text("$k: $v", fontSize = 13.sp, color = Chaukas.Ink2)
                        }
                        if (qr.warnings.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            qr.warnings.forEach { w ->
                                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Outlined.Warning, null, tint = Chaukas.Rose700, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(w, color = Chaukas.Rose700, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Instant Live Signals
            val signals = vm.liveSignals
            if (signals.isNotEmpty() && vm.run == null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bolt, null, tint = Chaukas.Rose700, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Spotted instantly:", style = MaterialTheme.typography.labelSmall, color = Chaukas.Ink3)
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    signals.forEach { Pill(it.label, Chaukas.Rose50, Chaukas.Rose700) }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Chaukas.Line)
            Spacer(Modifier.height(10.dp))

            // Action Bar: Big, Bold, Colorful Buttons matching Web CSS
            WebToolButtonsRow(
                vm = vm,
                onCamera = {
                    val file = java.io.File(ctx.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                },
                onScreenshot = { photoPicker.launch("image/*") },
                onPaste = {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString()
                    if (!clip.isNullOrBlank()) {
                        vm.text = clip
                        if (clip.contains("upi://") || clip.startsWith("http")) {
                            vm.qrFinding = InviteUtils.inspectQr(clip)
                        }
                    } else {
                        Toast.makeText(ctx, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        // Privacy Mode Selection (Clean segmented pill container matching Web)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Chaukas.Paper2,
                border = BorderStroke(1.dp, Chaukas.Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val modes = listOf(
                        Triple(PrivacyMode.PRIVATE, Icons.Outlined.Lock, "Private"),
                        Triple(PrivacyMode.SMART, Icons.Outlined.Shield, "Smart"),
                        Triple(PrivacyMode.CLOUD, Icons.Outlined.Cloud, "Most accurate"),
                    )
                    modes.forEach { (m, icon, _) ->
                        val isSelected = vm.mode == m
                        Surface(
                            onClick = { vm.mode = m },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color.White else Color.Transparent,
                            shadowElevation = if (isSelected) 1.5.dp else 0.dp,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Chaukas.Ink else Chaukas.Ink3,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = I18n.t(m.title, vm.language),
                                    color = if (isSelected) Chaukas.Ink else Chaukas.Ink3,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = I18n.t(vm.mode.line, vm.language),
                color = Chaukas.Ink2,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }

        // Big Bright Check Button (Teal-green gradient)
        Button(
            onClick = { vm.check() },
            enabled = !vm.busy && (vm.text.isNotBlank() || vm.attachedImageUri != null || vm.attachedBitmap != null),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Chaukas.Brand600,
                contentColor = Color.White,
                disabledContainerColor = Chaukas.Brand200,
                disabledContentColor = Chaukas.Brand700,
            ),
        ) {
            if (vm.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(I18n.t("Checking…", vm.language), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Outlined.VerifiedUser, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(I18n.t("Check it", vm.language), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Thinking / Progress Stages with wobbling animated orb matching Web's .thinking-orb
        if (vm.busy) {
            val stages = listOf("Reading the message", "Inspecting links and patterns", "Weighing the evidence")
            ChaukasCard(color = Color(0xFFFAF7FF), borderColor = Color(0xFFE9DDFD)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ThinkingOrb(size = 58.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            I18n.t("Checking with Chaukas…", vm.language),
                            fontWeight = FontWeight.Bold,
                            color = Chaukas.Brand700,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        stages.forEachIndexed { idx, title ->
                            val done = idx < vm.stage
                            val now = idx == vm.stage
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (done) Chaukas.Mint600 else if (now) Chaukas.Brand600 else Color.Transparent,
                                    border = BorderStroke(1.5.dp, if (done) Chaukas.Mint600 else if (now) Chaukas.Brand600 else Chaukas.Line),
                                    modifier = Modifier.size(18.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (done) {
                                            Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    I18n.t(title, vm.language),
                                    fontSize = 13.sp,
                                    fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
                                    color = if (now) Chaukas.Ink else if (done) Chaukas.Ink2 else Chaukas.Ink4,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error message
        vm.error?.let {
            ChaukasCard(color = Chaukas.Rose50, borderColor = Chaukas.Rose100) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = Chaukas.Rose700)
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = Chaukas.Rose700, fontSize = 14.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        // Result View
        AnimatedVisibility(
            visible = vm.run != null,
            enter = fadeIn() + slideInVertically { it / 6 },
        ) {
            vm.run?.let { run ->
                ResultView(
                    vm = vm,
                    run = run,
                    onTellCircle = { shareRunToCircle = run },
                )
            }
        }

        // When no result & not busy: How It Works cards
        if (vm.run == null && !vm.busy) {
            HowItWorksSection(vm.language)
        }
    }

    // Modal dialog to share alert to Family Circle
    shareRunToCircle?.let { r ->
        ShareToCircleDialog(
            run = r,
            language = vm.language,
            onDismiss = { shareRunToCircle = null },
            onGoToFamily = {
                shareRunToCircle = null
                onGoToFamily()
            },
        )
    }
}

/**
 * Three big, colorful tool buttons matching the web action bar:
 * Speak (Teal), Paste (Gold), Screenshot (Sky blue).
 */
@Composable
private fun WebToolButtonsRow(
    vm: ChaukasViewModel,
    onCamera: () -> Unit,
    onScreenshot: () -> Unit,
    onPaste: () -> Unit,
) {
    val ctx = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    val recognizer = remember { mutableStateOf<SpeechRecognizer?>(null) }
    DisposableEffect(Unit) {
        onDispose { recognizer.value?.destroy() }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            Toast.makeText(ctx, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        recognizer.value?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer.value = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            override fun onPartialResults(partial: android.os.Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                    vm.text = it
                }
            }
            override fun onResults(results: android.os.Bundle?) {
                listening = false
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                    vm.text = it
                }
            }
            override fun onError(error: Int) { listening = false }
        })
        val tag = when (vm.language) {
            "Hindi" -> "hi-IN"
            "Bengali" -> "bn-IN"
            "Tamil" -> "ta-IN"
            "Telugu" -> "te-IN"
            "Marathi" -> "mr-IN"
            else -> "en-IN"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        listening = true
        r.startListening(intent)
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening()
        else Toast.makeText(ctx, "Microphone permission required for Speak", Toast.LENGTH_SHORT).show()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val hasClear = vm.text.isNotBlank() || vm.attachedImageUri != null || vm.attachedBitmap != null
        val clearWidth = if (hasClear) 40.dp else 0.dp
        val gaps = 6.dp * 3
        val buttonWidth = (maxWidth - clearWidth - gaps) / 4
        val showText = buttonWidth >= 80.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Speak button (Teal or Red when recording)
            val speakText = if (listening) I18n.t("Stop", vm.language) else I18n.t("Speak", vm.language)
            val speakIcon = if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic
            Button(
                onClick = {
                    if (listening) {
                        recognizer.value?.stopListening()
                        listening = false
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (listening) Chaukas.Rose600 else Chaukas.Brand600,
                    contentColor = Color.White,
                ),
            ) {
                if (showText) {
                    Icon(speakIcon, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = speakText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Icon(speakIcon, contentDescription = speakText, modifier = Modifier.size(20.dp))
                }
            }

            // Paste button (Gold/Amber)
            val pasteText = I18n.t("Paste", vm.language)
            Button(
                onClick = onPaste,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF7C23C),
                    contentColor = Color(0xFF3D2A00),
                ),
            ) {
                if (showText) {
                    Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = pasteText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = pasteText, modifier = Modifier.size(20.dp))
                }
            }

            // Camera button (Mint/Teal)
            val cameraText = I18n.t("Camera", vm.language)
            Button(
                onClick = onCamera,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF226B47),
                    contentColor = Color.White,
                ),
            ) {
                if (showText) {
                    Icon(Icons.Outlined.PhotoCamera, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = cameraText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = cameraText, modifier = Modifier.size(20.dp))
                }
            }

            // Photo / Screenshot button (Sky Blue)
            val shotText = I18n.t("Photo", vm.language)
            Button(
                onClick = onScreenshot,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3A78BF),
                    contentColor = Color.White,
                ),
            ) {
                if (showText) {
                    Icon(Icons.Outlined.Image, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = shotText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Icon(Icons.Outlined.Image, contentDescription = shotText, modifier = Modifier.size(20.dp))
                }
            }

            if (hasClear) {
                IconButton(onClick = { vm.clear() }) {
                    Icon(Icons.Outlined.Clear, I18n.t("Clear", vm.language), tint = Chaukas.Ink3)
                }
            }
        }
    }
}

/**
 * Result component matching web/src/components/Result.tsx.
 */
@Composable
private fun ResultView(
    vm: ChaukasViewModel,
    run: AnalysisRun,
    onTellCircle: () -> Unit,
) {
    val ctx = LocalContext.current
    val a = run.analysis
    val (bg, fg, border) = when (a.verdict) {
        Verdict.SAFE -> Triple(Chaukas.Mint50, Chaukas.Mint700, Chaukas.Mint100)
        Verdict.SUSPICIOUS -> Triple(Chaukas.Butter50, Chaukas.Butter700, Chaukas.Butter100)
        Verdict.SCAM -> Triple(Chaukas.Rose50, Chaukas.Rose700, Chaukas.Rose100)
    }

    val doneSteps = remember(run.id) { mutableStateListOf<Int>() }
    var urgentOpen by remember(run.id) { mutableStateOf(false) }
    var expandedFlag by remember(run.id) { mutableStateOf<app.chaukas.ai.RedFlag?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Main Verdict Card
        ChaukasCard(color = bg, borderColor = border) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dial(a.riskScore, fg)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Pill(categoryLabel(a.category, vm.language), border, fg, if (a.verdict == Verdict.SCAM) Icons.Outlined.Shield else Icons.Outlined.VerifiedUser)
                    Spacer(Modifier.height(6.dp))
                    Text(a.verdict.label, style = MaterialTheme.typography.headlineSmall, color = fg)
                    val regional = RegionalVerdict.forVerdict(vm.language, a.verdict)
                    if (vm.language != "English") {
                        Text(regional, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = fg)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(a.summary, color = Chaukas.Ink, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(
                    "${sourceLabel(run.source.wire)} · ${run.latencyMs} ms",
                    if (run.source == Source.ON_DEVICE) Chaukas.Mint100 else Chaukas.Sky100,
                    if (run.source == Source.ON_DEVICE) Chaukas.Mint700 else Chaukas.Sky600,
                )
                if (run.redactions > 0) {
                    Pill("${run.redactions} details hidden", Chaukas.Lilac100, Chaukas.Lilac700, Icons.Outlined.VisibilityOff)
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = fg.copy(alpha = 0.15f))
            Spacer(Modifier.height(10.dp))

            if (vm.isKnownContact) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFF8F0),
                    border = BorderStroke(1.5.dp, Color(0xFFFFB74D)),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (vm.senderName.isNotBlank()) "⚠️ Scam alert: Message from ${vm.senderName}" else "⚠️ Scam alert from saved contact",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFFE65100),
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Complete message received:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Chaukas.Ink3,
                        )
                        Spacer(Modifier.height(4.dp))

                        // Complete Message Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFFFCC80)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = run.input,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Serif,
                                color = Chaukas.Ink,
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 20.sp,
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (a.redFlags.isNotEmpty()) {
                                "Red flags detected: ${a.redFlags.joinToString(" · ") { it.label }}. Choose what to do:"
                            } else {
                                "This sender is in your contacts, but this message has red flags. Their account may be compromised. Choose what to do:"
                            },
                            fontSize = 12.sp,
                            color = Chaukas.Ink2,
                            lineHeight = 16.sp,
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = {
                                    vm.saveCurrentRunToHistory()
                                    Toast.makeText(
                                        ctx,
                                        I18n.t("Marked as scam and shared to circle & history", vm.language),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                enabled = !vm.isSavedToHistory,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFC62828),
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(44.dp),
                            ) {
                                Icon(Icons.Outlined.Warning, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (vm.isSavedToHistory) I18n.t("Shared to circle & history", vm.language) else I18n.t("Mark as scam", vm.language),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    vm.dismissRun()
                                    Toast.makeText(
                                        ctx,
                                        I18n.t("Dismissed", vm.language),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(44.dp),
                                border = BorderStroke(1.dp, Color(0xFFD0D5DD)),
                            ) {
                                Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(16.dp), tint = Color(0xFF2E7D32))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = I18n.t("It's okay", vm.language),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = Chaukas.Ink2,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else if (a.verdict != Verdict.SAFE && a.riskScore >= 60) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Chaukas.Mint50,
                    border = BorderStroke(1.dp, Chaukas.Mint600),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Shield, null, tint = Chaukas.Mint700, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                I18n.t("Auto-shared to Family Circle & saved to History", vm.language),
                                color = Chaukas.Mint700,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            onClick = {
                                vm.dismissRun()
                                Toast.makeText(ctx, I18n.t("Dismissed", vm.language), Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFD0D5DD)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(13.dp), tint = Color(0xFF2E7D32))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    I18n.t("It's okay", vm.language),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = Chaukas.Ink2,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Actions row: Read aloud, Call 1930, Send to WhatsApp, Tell my circle
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Read aloud
                OutlinedButton(
                    onClick = { vm.speakRun(run) },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.VolumeUp, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Read aloud")
                }

                // Call 1930 (Helpline)
                if (a.verdict == Verdict.SCAM) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))
                            runCatching { ctx.startActivity(intent) }
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Phone, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Call 1930")
                    }
                }

                // Send to WhatsApp
                if (a.verdict != Verdict.SAFE && a.riskScore >= 65) {
                    Button(
                        onClick = {
                            val text = InviteUtils.resultWhatsAppText(run)
                            val uri = Uri.parse("https://wa.me/?text=" + Uri.encode(text))
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCF8E6), contentColor = Color(0xFF11663A)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Chat, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Send to WhatsApp", fontWeight = FontWeight.Bold)
                    }
                }

                // Add to History
                if (!vm.isSavedToHistory) {
                    Button(
                        onClick = {
                            vm.saveCurrentRunToHistory()
                            Toast.makeText(ctx, I18n.t("Saved to History", vm.language), Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Chaukas.Brand600, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.BookmarkAdd, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(I18n.t("Add to History", vm.language), fontWeight = FontWeight.Bold)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Chaukas.Mint50,
                        border = BorderStroke(1.dp, Chaukas.Mint600),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Check, null, tint = Chaukas.Mint700, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(I18n.t("Saved to History", vm.language), color = Chaukas.Mint700, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                // Tell my circle
                if (a.verdict != Verdict.SAFE && a.riskScore >= 65) {
                    Button(
                        onClick = {
                            if (!vm.isSavedToHistory) {
                                vm.saveCurrentRunToHistory()
                            }
                            onTellCircle()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A68DE), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(I18n.t("Tell my circle", vm.language), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Red Flags
        if (a.redFlags.isNotEmpty()) {
            ChaukasCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = Chaukas.Rose700)
                    Spacer(Modifier.width(8.dp))
                    Text(I18n.t("Red flags", vm.language), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Pill("${a.redFlags.size}", Chaukas.Rose50, Chaukas.Rose700)
                }
                Spacer(Modifier.height(10.dp))
                a.redFlags.forEachIndexed { i, f ->
                    Surface(
                        onClick = { expandedFlag = f },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Chaukas.Rose50,
                        border = BorderStroke(1.dp, Chaukas.Rose100),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${i + 1}. ${f.label}", fontWeight = FontWeight.Bold, color = Chaukas.Ink, modifier = Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Search, null, tint = Chaukas.Rose700, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(I18n.t("Details", vm.language), fontSize = 11.sp, color = Chaukas.Rose700, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (f.evidence.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text("“${f.evidence}”", color = Chaukas.Rose700, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(f.why, color = Chaukas.Ink2, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Expanded Red Flag Dialog ("vo bada ho kyu flag hua hai")
        expandedFlag?.let { f ->
            AlertDialog(
                onDismissRequest = { expandedFlag = null },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vm.isKnownContact) {
                            Button(
                                onClick = {
                                    vm.saveCurrentRunToHistory()
                                    expandedFlag = null
                                    Toast.makeText(
                                        ctx,
                                        I18n.t("Shared as scam to circle & history", vm.language),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                enabled = !vm.isSavedToHistory,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    if (vm.isSavedToHistory) I18n.t("Shared to circle & history", vm.language) else I18n.t("Mark as scam", vm.language),
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    expandedFlag = null
                                    vm.dismissRun()
                                    Toast.makeText(
                                        ctx,
                                        I18n.t("Dismissed", vm.language),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(I18n.t("It's okay", vm.language))
                            }
                        } else if (!vm.isSavedToHistory) {
                            Button(
                                onClick = {
                                    vm.saveCurrentRunToHistory()
                                    Toast.makeText(ctx, I18n.t("Saved to History", vm.language), Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Chaukas.Brand600),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Outlined.BookmarkAdd, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(I18n.t("Add to History", vm.language))
                            }
                        } else {
                            Button(
                                onClick = {
                                    expandedFlag = null
                                    onTellCircle()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A68DE)),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Outlined.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(I18n.t("Tell my circle", vm.language))
                            }
                        }
                        OutlinedButton(
                            onClick = { expandedFlag = null },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(I18n.t("Close", vm.language))
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = Chaukas.Rose700, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(f.label, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (f.evidence.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Chaukas.Rose50,
                                border = BorderStroke(1.dp, Chaukas.Rose100),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("FLAGGED QUOTE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Chaukas.Rose700)
                                    Spacer(Modifier.height(4.dp))
                                    Text("“${f.evidence}”", fontFamily = FontFamily.Serif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Chaukas.Ink, fontSize = 13.sp)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        Text("Why this is flagged:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Chaukas.Ink)
                        Spacer(Modifier.height(4.dp))
                        Text(f.why, fontSize = 13.sp, color = Chaukas.Ink2, lineHeight = 18.sp)
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Chaukas.Paper2,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("How scammers use this trick:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Chaukas.Ink)
                                Spacer(Modifier.height(3.dp))
                                Text("Fraudsters use artificial urgency, threat of service disconnection, or fake authority to rush you into acting before checking. Legitimate banks and utilities never ask for OTPs or payments over random texted links.", fontSize = 12.sp, color = Chaukas.Ink2, lineHeight = 16.sp)
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = Color.White,
            )
        }

        // What to do now (interactive checklist)
        val defaultScamSteps = listOf(
            "Do not tap any links, open attachments, or call the phone number in the message.",
            "Block the sender on WhatsApp/SMS and tap \"Report as Spam\" immediately.",
            "Never share OTP, passwords, or enter your UPI PIN to receive any money.",
            "If money was sent or OTP shared, call 1930 (Cyber Crime Helpline) or file at cybercrime.gov.in within 2 hours.",
            "If an APK or remote app (AnyDesk/TeamViewer) was downloaded, disconnect from internet and uninstall it.",
            "Warn your family in Family Circle so they don't fall for this exact scam wave.",
        )
        val defaultSafeSteps = listOf(
            "This message shows normal legitimate patterns and no common fraud signals.",
            "Still, remember: no bank, company, or government officer ever asks for your OTP or UPI PIN.",
            "If you ever feel uncertain, verify directly through official apps or known numbers.",
        )
        val displaySteps = when {
            a.nextSteps.size >= 5 -> a.nextSteps
            a.verdict == Verdict.SCAM || a.verdict == Verdict.SUSPICIOUS -> (a.nextSteps + defaultScamSteps).distinct().take(6)
            else -> if (a.nextSteps.isNotEmpty()) a.nextSteps else defaultSafeSteps
        }

        ChaukasCard(color = Chaukas.Mint50, borderColor = Chaukas.Mint100) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircleOutline, null, tint = Chaukas.Mint700)
                Spacer(Modifier.width(8.dp))
                Text(I18n.t("What to do now", vm.language), style = MaterialTheme.typography.titleMedium, color = Chaukas.Mint700)
            }
            Spacer(Modifier.height(10.dp))
            displaySteps.forEachIndexed { idx, s ->
                val isDone = doneSteps.contains(idx)
                Surface(
                    onClick = {
                        if (isDone) doneSteps.remove(idx) else doneSteps.add(idx)
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, if (isDone) Chaukas.Mint600 else Chaukas.Mint100),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isDone) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            null,
                            tint = if (isDone) Chaukas.Mint600 else Chaukas.Mint700,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            I18n.t(s, vm.language),
                            color = if (isDone) Chaukas.Ink3 else Chaukas.Ink,
                            textDecoration = if (isDone) TextDecoration.LineThrough else null,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // Safe reply card
        if (a.safeReply.isNotBlank()) {
            ChaukasCard {
                Text(I18n.t("Safe reply", vm.language), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Chaukas.Paper,
                    border = BorderStroke(1.dp, Chaukas.Line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(a.safeReply, fontSize = 15.sp, fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val cm = ctx.getSystemService(ClipboardManager::class.java)
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Safe reply", a.safeReply))
                                Toast.makeText(ctx, I18n.t("Reply copied", vm.language), Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(I18n.t("Copy reply", vm.language))
                        }
                    }
                }
            }
        }

        // Report Card - Clean, Undistorted, Full-width Stacked Layout
        ChaukasCard(color = Color(0xFFFFF7EF), borderColor = Color(0xFFFDE1D3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ReportProblem, null, tint = Chaukas.Peach700)
                Spacer(Modifier.width(8.dp))
                Text(I18n.t("Report it, protect others", vm.language), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text("National Cyber Crime Reporting Portal: cybercrime.gov.in or call 1930 (India).", fontSize = 13.sp, color = Chaukas.Ink2)
            Spacer(Modifier.height(12.dp))

            // Official Reporting Quick Actions matching Web & README
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))) } },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFFDE1D3)),
                    modifier = Modifier.weight(1f).height(44.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.Phone, null, tint = Chaukas.Peach700, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("1930", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Chaukas.Peach700)
                    }
                }

                Surface(
                    onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in"))) } },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFFDE1D3)),
                    modifier = Modifier.weight(1.4f).height(44.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.Public, null, tint = Chaukas.Ink, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("cybercrime.gov.in", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Chaukas.Ink)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sancharsaathi.gov.in/sfc/"))) } },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFFDE1D3)),
                    modifier = Modifier.weight(1f).height(44.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.Security, null, tint = Chaukas.Ink, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Chakshu (DoT)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Chaukas.Ink)
                    }
                }

                Surface(
                    onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:1909")).apply { putExtra("sms_body", "Spam: ${excerpt(run.input, 100)}") }) } },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFFDE1D3)),
                    modifier = Modifier.weight(1f).height(44.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.Chat, null, tint = Chaukas.Ink, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SMS 1909", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Chaukas.Ink)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Button 1: Copy Report (Balanced full-width)
                OutlinedButton(
                    onClick = {
                        val report = "Chaukas Scam Report:\nVerdict: ${a.verdict.label}\nCategory: ${a.category}\nScore: ${a.riskScore}/100\nMessage excerpt: ${excerpt(run.input, 140)}"
                        val cm = ctx.getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Scam report", report))
                        Toast.makeText(ctx, I18n.t("Report text copied", vm.language), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Chaukas.Line),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Chaukas.Ink),
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(I18n.t("Copy report", vm.language), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                // Button 2: Emergency Action "I already paid" (Eye-catching, full-width, clean)
                Surface(
                    onClick = { urgentOpen = !urgentOpen },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (urgentOpen) Chaukas.Rose700 else Chaukas.Rose50,
                    border = BorderStroke(1.dp, if (urgentOpen) Chaukas.Rose700 else Chaukas.Rose100),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Outlined.Bolt,
                                null,
                                tint = if (urgentOpen) Color.White else Chaukas.Rose700,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = I18n.t("I already paid or shared a code", vm.language),
                                color = if (urgentOpen) Color.White else Chaukas.Rose700,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                        Icon(
                            if (urgentOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            null,
                            tint = if (urgentOpen) Color.White else Chaukas.Rose700,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (urgentOpen) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF1F0),
                    border = BorderStroke(1.dp, Color(0xFFFFA39E)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Warning, null, tint = Chaukas.Rose700, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = I18n.t("Emergency Action (Golden Hour)", vm.language),
                                fontWeight = FontWeight.Bold,
                                color = Chaukas.Rose700,
                                fontSize = 14.sp,
                            )
                        }
                        Text(
                            text = "1. " + I18n.t("Call 1930 immediately to freeze the beneficiary account.", vm.language),
                            fontWeight = FontWeight.Bold,
                            color = Chaukas.Rose700,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = "2. " + I18n.t("Contact your bank to block cards, net-banking and freeze UPI IDs.", vm.language),
                            fontSize = 13.sp,
                            color = Chaukas.Ink,
                        )
                        Text(
                            text = "3. " + I18n.t("Change your UPI PIN and email/banking passwords.", vm.language),
                            fontSize = 13.sp,
                            color = Chaukas.Ink,
                        )
                        Text(
                            text = "4. " + I18n.t("If you installed AnyDesk or an APK, disconnect internet and uninstall it immediately.", vm.language),
                            fontSize = 13.sp,
                            color = Chaukas.Ink,
                        )
                    }
                }
            }
        }
    }
}

/**
 * How it works section matching Web's HowItWorks.
 */
@Composable
private fun HowItWorksSection(language: String = "English") {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(I18n.t("HOW IT WORKS", language), style = MaterialTheme.typography.labelSmall, color = Chaukas.Ink3)
        ChaukasCard(color = Chaukas.Mint50, borderColor = Chaukas.Mint100) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhoneAndroid, null, tint = Chaukas.Mint700, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(I18n.t("Checked on your device", language), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(I18n.t("Gemini Nano runs inside AICore on this phone, so your messages stay private, even offline.", language), fontSize = 13.sp, color = Chaukas.Ink2)
                }
            }
        }
        ChaukasCard(color = Chaukas.Lilac50, borderColor = Chaukas.Lilac100) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Visibility, null, tint = Chaukas.Lilac700, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(I18n.t("Shows its working", language), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(I18n.t("Every warning points at the exact words, links and urgent language that gave the scam away.", language), fontSize = 13.sp, color = Chaukas.Ink2)
                }
            }
        }
        ChaukasCard(color = Chaukas.Peach50, borderColor = Color(0xFFFDE1D3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Groups, null, tint = Chaukas.Peach700, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(I18n.t("Looks after family", language), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(I18n.t("Share a warning with your family circle in one tap. Personal details are removed automatically.", language), fontSize = 13.sp, color = Chaukas.Ink2)
                }
            }
        }
    }
}

/**
 * Modal dialog to share alert to Family Circle.
 */
@Composable
private fun ShareToCircleDialog(
    run: AnalysisRun,
    language: String = "English",
    onDismiss: () -> Unit,
    onGoToFamily: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val circles by remember { Repository.listenMyCircles() }.collectAsState(initial = emptyList())
    var selectedCircleId by remember(circles) { mutableStateOf(circles.firstOrNull()?.id ?: "") }
    var note by remember { mutableStateOf("Watch out, I got this message today.") }
    var busy by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Eyebrow(I18n.t("Family Circle", language))
                Spacer(Modifier.height(4.dp))
                Text(I18n.t("Warn your circle", language))
            }
        },
        text = {
            if (circles.isEmpty()) {
                Column {
                    Text("You haven't set up a family circle yet. It only takes a minute to create or join one.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onGoToFamily) {
                        Icon(Icons.Outlined.Groups, null)
                        Spacer(Modifier.width(8.dp))
                        Text(I18n.t("Family Circle", language))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Send to:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        circles.forEach { c ->
                            val on = selectedCircleId == c.id
                            FilterChip(
                                selected = on,
                                onClick = {
                                    selectedCircleId = c.id
                                    Repository.setActiveCircleId(ctx, c.id)
                                },
                                label = { Text(c.name) },
                                leadingIcon = { Icon(Icons.Outlined.Groups, null, modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(280) },
                        label = { Text("Add a note") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Chaukas.Paper2,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("THEY WILL SEE (PERSONAL DETAILS REMOVED)", style = MaterialTheme.typography.labelSmall, color = Chaukas.Ink3)
                            Spacer(Modifier.height(4.dp))
                            Text(excerpt(app.chaukas.ai.Redactor.redact(run.input).text, 160), fontSize = 13.sp, color = Chaukas.Ink2)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (circles.isNotEmpty()) {
                Button(
                    enabled = !busy && selectedCircleId.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                Repository.shareAlert(selectedCircleId, run, note)
                            }.onSuccess {
                                Toast.makeText(ctx, "Alert shared with your circle", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }.onFailure {
                                Toast.makeText(ctx, "Could not share: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                            busy = false
                        }
                    },
                ) {
                    Text(if (busy) "Sharing…" else I18n.t("Share alert", language))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(I18n.t("Cancel", language)) }
        },
    )
}

/* ------------------------------ FAMILY SCREEN ------------------------------ */

@Composable
private fun FamilyScreen(vm: ChaukasViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val authUser by remember { Repository.authUserFlow() }.collectAsState(initial = Repository.currentUser)
    val user = authUser ?: Repository.currentUser
    val currentUid = user?.uid
    var savedName by remember { mutableStateOf(Repository.getSavedName(ctx)) }
    val signedIn = !savedName.isNullOrBlank() || (user != null && !user.displayName.isNullOrBlank() && user.displayName != "Me")

    var name by remember { mutableStateOf("") }
    var myHandle by remember {
        mutableStateOf(
            Repository.getSavedHandle(ctx) ?: if (signedIn) Repository.ensureMyHandleSync(Repository.myDisplayName, ctx) else ""
        )
    }
    var activeCircleId by remember { mutableStateOf(Repository.getActiveCircleId(ctx) ?: "") }
    var askingHandle by remember { mutableStateOf<String?>(null) }
    var openAddPerson by remember { mutableStateOf(false) }
    var openInviteLink by remember { mutableStateOf(false) }
    var confirmLeaveCircle by remember { mutableStateOf<Circle?>(null) }
    var confirmDeleteCircle by remember { mutableStateOf<Circle?>(null) }
    var joinCode by remember { mutableStateOf("") }

    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val handle = vm.scanHandleFromImage(ctx, uri)
                if (!handle.isNullOrBlank()) {
                    askingHandle = handle
                } else {
                    Toast.makeText(ctx, "No Chaukas ID found in that picture", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Foldable section states
    var myIdOpen by remember { mutableStateOf(true) }
    var notifyOpen by remember { mutableStateOf(false) }
    var joinCodeOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!savedName.isNullOrBlank() || Repository.uid == null) {
            runCatching { Repository.ensureAuth(ctx) }
        }
    }

    val circles by remember(currentUid) { Repository.listenMyCircles(currentUid) }.collectAsState(initial = emptyList())
    val invites by remember(currentUid) { Repository.listenMyInvites(currentUid) }.collectAsState(initial = emptyList())

    // Update active circle
    LaunchedEffect(circles) {
        if (circles.isNotEmpty()) {
            if (activeCircleId.isBlank() || circles.none { it.id == activeCircleId }) {
                val savedId = Repository.getActiveCircleId(ctx)
                activeCircleId = if (!savedId.isNullOrBlank() && circles.any { it.id == savedId }) savedId else circles.first().id
                Repository.setActiveCircleId(ctx, activeCircleId)
            }
        }
    }

    // Load handle when signed in
    LaunchedEffect(signedIn, user?.displayName, savedName, currentUid) {
        if (signedIn) {
            if (myHandle.isBlank()) {
                myHandle = Repository.ensureMyHandleSync(Repository.myDisplayName, ctx)
            }
            runCatching {
                val synced = Repository.ensureMyHandle(Repository.myDisplayName, ctx)
                if (synced.isNotBlank()) myHandle = synced
            }
        }
    }

    val activeCircle = circles.firstOrNull { it.id == activeCircleId }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow(I18n.t("Look out for each other", vm.language))
                Text(I18n.t("Family Circle", vm.language), style = MaterialTheme.typography.displaySmall)
            }
            if (signedIn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(Repository.myDisplayName, size = 36.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(Repository.myDisplayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }

        // SIGN IN STATE
        if (!signedIn) {
            ChaukasCard(color = Color(0xFFFFF6EE), borderColor = Color(0xFFFDE1D3)) {
                // Overlapping avatars art
                Row(modifier = Modifier.padding(bottom = 12.dp)) {
                    Avatar("Maya", size = 44.dp)
                    Spacer(Modifier.width(-8.dp))
                    Avatar("Dad", size = 48.dp)
                    Spacer(Modifier.width(-8.dp))
                    Avatar("Leo", size = 44.dp)
                }

                Text(I18n.t("What should your family call you?", vm.language), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(I18n.t("Just a name. No email, no password, no phone number.", vm.language), color = Chaukas.Ink2, fontSize = 14.sp)

                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFFDE1D3)),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, null, tint = Chaukas.Peach700, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            I18n.t("Choose carefully. Once you confirm your name, it cannot be changed later. Your Chaukas ID is made from it.", vm.language),
                            fontSize = 12.sp,
                            color = Chaukas.Ink2,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Nani, Papa, Riya") },
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val clean = name.trim()
                        if (clean.isNotBlank()) {
                            scope.launch {
                                runCatching {
                                    Repository.saveUserName(ctx, clean)
                                    savedName = clean
                                    Repository.signIn(clean, ctx)
                                    val h = Repository.ensureMyHandle(clean, ctx)
                                    if (h.isNotBlank()) myHandle = h
                                }.onFailure { Toast.makeText(ctx, it.message ?: "Sign in failed", Toast.LENGTH_SHORT).show() }
                            }
                        }
                    },
                    enabled = name.trim().isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Chaukas.Brand600),
                ) {
                    Text(I18n.t("Confirm", vm.language))
                }
            }

            // Pitch steps
            CirclePitchSection(vm.language)
            return@Column
        }

        // SIGNED IN STATE

        // Waiting Invites
        val pendingInvites = invites.filter { inv -> circles.none { it.id == inv.id } }
        if (pendingInvites.isNotEmpty()) {
            ChaukasCard(color = Chaukas.Butter50, borderColor = Chaukas.Butter100) {
                Eyebrow("Waiting for you")
                Spacer(Modifier.height(8.dp))
                pendingInvites.forEach { inv ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(inv.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${inv.by} wants to keep you", fontSize = 13.sp, color = Chaukas.Ink2)
                        }
                        TextButton(
                            onClick = { scope.launch { runCatching { Repository.leaveInvite(inv.id) } } },
                        ) {
                            Text("Don't keep", color = Chaukas.Ink3)
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        Repository.stayInCircle(inv.id)
                                        activeCircleId = inv.id
                                    }.onFailure {
                                        Toast.makeText(ctx, it.message ?: "Could not join", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Keep")
                        }
                    }
                }
            }
        }

        // Group Switcher (if user is in > 1 groups)
        if (circles.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                circles.forEach { c ->
                    val on = c.id == activeCircleId
                    Surface(
                        onClick = {
                            activeCircleId = c.id
                            Repository.setActiveCircleId(ctx, c.id)
                        },
                        shape = CircleShape,
                        color = if (on) Color(0xFFFFF7EF) else Chaukas.Surface,
                        border = BorderStroke(1.dp, if (on) Color(0xFFFDE1D3) else Chaukas.Line),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Groups, null, tint = if (on) Chaukas.Peach700 else Chaukas.Ink3, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (on) Chaukas.Peach700 else Chaukas.Ink)
                        }
                    }
                }
            }
        }

        // Active Group Card & Feed
        if (activeCircle != null) {
            val myUid = user?.uid ?: Repository.uid ?: ""
            GroupCard(
                circle = activeCircle,
                myUid = myUid,
                language = vm.language,
                openAdd = openAddPerson,
                onToggleAdd = { openAddPerson = !openAddPerson },
                openLink = openInviteLink,
                onToggleLink = { openInviteLink = !openInviteLink },
                onLeave = { confirmLeaveCircle = activeCircle },
                onDelete = { confirmDeleteCircle = activeCircle },
                onAddHandle = { h -> askingHandle = h },
                onScanQr = { qrPicker.launch("image/*") },
            )

            // Circle alerts feed
            CircleFeed(circle = activeCircle, myUid = myUid, language = vm.language)
        } else {
            // No circle yet: AddById card + pitch
            ChaukasCard(color = Chaukas.Lilac50, borderColor = Chaukas.Lilac100) {
                Eyebrow(I18n.t("Make your family group", vm.language))
                Spacer(Modifier.height(4.dp))
                Text(
                    I18n.t("Scan a family member's QR or type their ID, then tap Keep. Your group is made with them.", vm.language),
                    color = Chaukas.Ink2,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                var idInput by remember { mutableStateOf("") }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = idInput,
                        onValueChange = { idInput = it.lowercase() },
                        placeholder = { Text(I18n.t("Their ID, e.g. riya-4821", vm.language)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val clean = InviteUtils.normalizeHandle(idInput).ifBlank { idInput.trim().lowercase() }
                                if (clean.isNotBlank()) {
                                    askingHandle = clean
                                    idInput = ""
                                }
                            },
                            enabled = idInput.trim().isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(I18n.t("Add", vm.language))
                        }
                        OutlinedButton(
                            onClick = { qrPicker.launch("image/*") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(I18n.t("Scan QR", vm.language))
                        }
                    }
                }
            }

            CirclePitchSection(vm.language)
        }

        // FOLDABLE 1: Your Chaukas ID
        ChaukasCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { myIdOpen = !myIdOpen },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.QrCode, null, tint = Chaukas.Brand600)
                Spacer(Modifier.width(10.dp))
                Text(I18n.t("Your Chaukas ID", vm.language), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Icon(
                    if (myIdOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Chaukas.Ink3,
                )
            }

            if (myIdOpen) {
                val effectiveHandle = myHandle.ifBlank {
                    Repository.ensureMyHandleSync(Repository.myDisplayName, ctx)
                }
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    QrCodeView(text = InviteUtils.personUrl(effectiveHandle), size = 160.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(effectiveHandle, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = Chaukas.Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        I18n.t("Family scan this with their phone camera, or type your ID. You get a popup to keep their group or not.", vm.language),
                        fontSize = 13.sp,
                        color = Chaukas.Ink2,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Add me to your Chaukas family group. My Chaukas ID is $effectiveHandle: ${InviteUtils.personUrl(effectiveHandle)}")
                                }
                                ctx.startActivity(Intent.createChooser(send, I18n.t("Share your Chaukas ID", vm.language)))
                            },
                        ) {
                            Icon(Icons.Outlined.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(I18n.t("Share", vm.language))
                        }
                        OutlinedButton(
                            onClick = {
                                val cm = ctx.getSystemService(ClipboardManager::class.java)
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Chaukas ID", effectiveHandle))
                                Toast.makeText(ctx, I18n.t("ID copied", vm.language), Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(I18n.t("Copy ID", vm.language))
                        }
                    }
                }
            }
        }

        // FOLDABLE 2: Notifications and sharing
        ChaukasCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { notifyOpen = !notifyOpen },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Settings, null, tint = Chaukas.Brand600)
                Spacer(Modifier.width(10.dp))
                Text(I18n.t("Notifications and sharing", vm.language), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Icon(
                    if (notifyOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Chaukas.Ink3,
                )
            }

            if (notifyOpen) {
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    var groupNotifyOn by remember { mutableStateOf(true) }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (groupNotifyOn) Chaukas.Brand50 else Chaukas.Paper2,
                        border = BorderStroke(1.dp, if (groupNotifyOn) Chaukas.Brand100 else Chaukas.Line),
                        modifier = Modifier.fillMaxWidth().clickable { groupNotifyOn = !groupNotifyOn },
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.NotificationsActive,
                                null,
                                tint = if (groupNotifyOn) Chaukas.Brand700 else Chaukas.Ink3,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    I18n.t(if (groupNotifyOn) "Group notifications: on" else "Group notifications: off", vm.language),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    I18n.t("Get notified when someone in your family circle shares a warning.", vm.language),
                                    fontSize = 12.sp,
                                    color = Chaukas.Ink2,
                                )
                            }
                            Switch(
                                checked = groupNotifyOn,
                                onCheckedChange = { groupNotifyOn = it },
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (vm.autoShareCircle) Chaukas.Brand50 else Chaukas.Paper2,
                        border = BorderStroke(1.dp, if (vm.autoShareCircle) Chaukas.Brand100 else Chaukas.Line),
                        modifier = Modifier.fillMaxWidth().clickable { vm.setAutoShare(!vm.autoShareCircle) },
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                null,
                                tint = if (vm.autoShareCircle) Chaukas.Brand700 else Chaukas.Ink3,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    I18n.t(if (vm.autoShareCircle) "Auto-share scams: on" else "Auto-share scams: off", vm.language),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    I18n.t("Automatically post warnings for risky messages (score 65+) to your family circles.", vm.language),
                                    fontSize = 12.sp,
                                    color = Chaukas.Ink2,
                                )
                            }
                            Switch(
                                checked = vm.autoShareCircle,
                                onCheckedChange = { vm.setAutoShare(it) },
                            )
                        }
                    }
                }
            }
        }

        // FOLDABLE 3: Got a group code? (if no active circle)
        if (activeCircle == null) {
            ChaukasCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { joinCodeOpen = !joinCodeOpen },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Groups, null, tint = Chaukas.Brand600)
                    Spacer(Modifier.width(10.dp))
                    Text(I18n.t("Got a group code?", vm.language), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Icon(
                        if (joinCodeOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = Chaukas.Ink3,
                    )
                }

                if (joinCodeOpen) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = joinCode,
                            onValueChange = { joinCode = it.uppercase().take(6) },
                            placeholder = { Text("ABC123") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val id = Repository.joinCircle(joinCode)
                                        activeCircleId = id
                                        joinCode = ""
                                        Toast.makeText(ctx, "Joined circle!", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(ctx, it.message ?: "Failed to join", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = joinCode.length == 6,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Join")
                        }
                    }
                }
            }
        }
    }

    // KeepDialog modal
    askingHandle?.let { handle ->
        KeepDialog(
            handle = handle,
            myGroup = activeCircle,
            language = vm.language,
            onDismiss = { askingHandle = null },
            onKept = { groupId ->
                activeCircleId = groupId
                askingHandle = null
            },
        )
    }

    // Confirm Leave Circle dialog
    confirmLeaveCircle?.let { circle ->
        AlertDialog(
            onDismissRequest = { confirmLeaveCircle = null },
            title = { Text("${I18n.t("Leave group", vm.language)}: ${circle.name}?") },
            text = { Text("You will stop seeing its scam warnings. You can rejoin later if someone adds you again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching { Repository.leaveCircle(circle) }
                            confirmLeaveCircle = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Chaukas.Rose700),
                ) {
                    Text(I18n.t("Leave", vm.language))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeaveCircle = null }) { Text(I18n.t("Cancel", vm.language)) }
            },
        )
    }

    // Confirm Delete Circle dialog
    confirmDeleteCircle?.let { circle ->
        AlertDialog(
            onDismissRequest = { confirmDeleteCircle = null },
            title = { Text(I18n.t("Delete group", vm.language)) },
            text = { Text("Delete ${circle.name}? This will delete the group for all members. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching { Repository.deleteCircle(circle) }
                            confirmDeleteCircle = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Chaukas.Rose700),
                ) {
                    Text(I18n.t("Delete group", vm.language))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteCircle = null }) { Text(I18n.t("Cancel", vm.language)) }
            },
        )
    }
}

/**
 * Group at a glance: members, handles, Add person, Invite link, Leave, Delete.
 */
@Composable
private fun GroupCard(
    circle: Circle,
    myUid: String,
    language: String,
    openAdd: Boolean,
    onToggleAdd: () -> Unit,
    openLink: Boolean,
    onToggleLink: () -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit,
    onAddHandle: (String) -> Unit,
    onScanQr: () -> Unit,
) {
    val ctx = LocalContext.current
    ChaukasCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(circle.name, style = MaterialTheme.typography.headlineSmall, color = Chaukas.Ink)
                Text(
                    text = "${I18n.t("Code", language)}: ${circle.id}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Chaukas.Peach700,
                    modifier = Modifier.clickable {
                        val clip = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clip.setPrimaryClip(android.content.ClipData.newPlainText("Group Code", circle.id))
                        Toast.makeText(ctx, I18n.t("Group code copied", language), Toast.LENGTH_SHORT).show()
                    }
                )
            }
            Pill("${circle.members.size} ${if (circle.members.size == 1) I18n.t("member", language) else I18n.t("members", language)}", Chaukas.Peach100, Chaukas.Peach700)
        }

        Spacer(Modifier.height(14.dp))
        // Members list with avatars and handles
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            circle.members.forEach { m ->
                val isMe = m == myUid
                val mName = if (isMe) I18n.t("You", language) else circle.memberNames[m] ?: "Member"
                val handle = circle.memberHandles[m]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(min = 60.dp, max = 110.dp),
                ) {
                    Avatar(mName, size = 44.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(mName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (handle != null) {
                        Text(
                            text = handle,
                            fontSize = 10.sp,
                            color = Chaukas.Ink3,
                            fontFamily = FontFamily.Monospace,
                            softWrap = true,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }

        // Waiting for
        val waiting = circle.invited.mapNotNull { circle.invitedNames[it] }
        if (waiting.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Waiting for: ${waiting.joinToString(", ")}.", fontSize = 12.sp, color = Chaukas.Ink3)
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Chaukas.Line)
        Spacer(Modifier.height(10.dp))

        // Actions: Spacious primary buttons of equal size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onToggleAdd,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (openAdd) Chaukas.Brand600 else Chaukas.Brand50,
                    contentColor = if (openAdd) Color.White else Chaukas.Brand700,
                ),
                modifier = Modifier.weight(1f).height(46.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Icon(Icons.Outlined.PersonAdd, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = I18n.t("Add person", language),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = onToggleLink,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (openLink) Chaukas.Brand600 else Chaukas.Brand50,
                    contentColor = if (openLink) Color.White else Chaukas.Brand700,
                ),
                modifier = Modifier.weight(1f).height(46.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Icon(Icons.Outlined.Link, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = I18n.t("Invite link", language),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Leave & Delete options (clean secondary row, never congested)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onLeave,
                colors = ButtonDefaults.textButtonColors(contentColor = Chaukas.Ink3),
            ) {
                Icon(Icons.Outlined.Logout, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(I18n.t("Leave group", language), fontSize = 12.sp)
            }

            val isCreator = circle.ownerUid.isNotBlank() && circle.ownerUid == myUid
            if (isCreator) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Chaukas.Rose700),
                ) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(I18n.t("Delete group", language), fontSize = 12.sp)
                }
            }
        }

        // Add person form: Balanced, sleek matching layout
        if (openAdd) {
            Spacer(Modifier.height(10.dp))
            var idInput by remember { mutableStateOf("") }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Chaukas.Paper2,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(I18n.t("Add by Chaukas ID", language), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Chaukas.Ink)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = idInput,
                            onValueChange = { idInput = it.lowercase() },
                            placeholder = { Text(I18n.t("e.g. riya-4821", language), fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                        )
                        Button(
                            onClick = {
                                val clean = InviteUtils.normalizeHandle(idInput).ifBlank { idInput.trim().lowercase() }
                                if (clean.isNotBlank()) {
                                    onAddHandle(clean)
                                    idInput = ""
                                }
                            },
                            enabled = idInput.trim().isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(52.dp),
                        ) {
                            Text(I18n.t("Add", language), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = onScanQr,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(52.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Invite link section: Spacious 3-button equal layout
        if (openLink) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Chaukas.Paper2,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Invite to ${circle.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Chaukas.Ink,
                        )
                        Pill(circle.id, Chaukas.Brand50, Chaukas.Brand700)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "For family who don't have Chaukas yet. One tap joins ${circle.name}.",
                        fontSize = 12.sp,
                        color = Chaukas.Ink2,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val text = InviteUtils.inviteText(circle.id, circle.name)
                                val uri = Uri.parse("https://wa.me/?text=" + Uri.encode(text))
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCF8E6), contentColor = Color(0xFF11663A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                        ) {
                            Icon(Icons.Outlined.Chat, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                val text = InviteUtils.inviteText(circle.id, circle.name)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                ctx.startActivity(Intent.createChooser(send, "Invite family"))
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                        ) {
                            Icon(Icons.Outlined.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(I18n.t("Share", language), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                val cm = ctx.getSystemService(ClipboardManager::class.java)
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Invite link", InviteUtils.inviteUrl(circle.id)))
                                Toast.makeText(ctx, "Link copied", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                        ) {
                            Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun circleEmptyText(circleName: String, language: String): String = when (language) {
    "Hindi" -> "जब ${circleName} में कोई संदिग्ध मैसेज जाँचेगा, तो वह अपने-आप यहाँ दिखेगा। सिर्फ़ जोखिम भरे मैसेज (65 या ज़्यादा) ही शेयर होते हैं, और हर चेतावनी 3 दिन बाद हट जाती है।"
    "Bengali" -> "যখন ${circleName}-এর কেউ কোনো প্রতারণামূলক মেসেজ যাচাই করবেন, তা নিজ থেকেই এখানে দেখা যাবে। শুধু ঝুঁকিপূর্ণ মেসেজ (৬৫ বা বেশি) শেয়ার হয়, এবং প্রতিটি সতর্কতা ৩ দিন পর মুছে যায়।"
    "Tamil" -> "${circleName}-இல் யாராவது மோசடி போன்ற மெசேஜைச் சரிபார்க்கும்போது, அது தானாகவே இங்கே தோன்றும். ஆபத்தான மெசேஜ்கள் (65 அல்லது அதற்கு மேல்) மட்டுமே பகிரப்படும், ஒவ்வொரு எச்சரிக்கையும் 3 நாட்களுக்குப் பிறகு மறைந்துவிடும்."
    "Telugu" -> "${circleName}లో ఎవరైనా మోసపూరిత సందేశాన్ని తనిఖీ చేసినప్పుడు, అది స్వయంచాలకంగా ఇక్కడ కనిపిస్తుంది. ప్రమాదకరమైన సందేశాలు (65 లేదా అంతకంటే ఎక్కువ) మాత్రమే షేర్ అవుతాయి, ప్రతి హెచ్చరిక 3 రోజుల తర్వాత తొలగిపోతుంది."
    "Marathi" -> "जेव्हा ${circleName} मधील कोणी फसव्या वाटणाऱ्या मेसेजची तपासणी करेल, तेव्हा तो आपोआप इथे दिसेल. फक्त जोखमीचे मेसेज (६५ किंवा त्याहून अधिक) शेअर केले जातात, आणि प्रत्येक सूचना ३ दिवसांनंतर नाहीशी होते."
    else -> "When someone in $circleName checks a message that looks like a scam, it shows up here by itself. Only risky messages (65 or more) are shared, and each warning disappears after 3 days."
}

/**
 * Circle scam warnings feed.
 */
@Composable
private fun CircleFeed(circle: Circle, myUid: String, language: String = "English") {
    val alerts by remember(circle.id) { Repository.alerts(circle.id) }.collectAsState(initial = emptyList())
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    if (alerts.isEmpty()) {
        ChaukasCard {
            Text(I18n.t("All quiet", language), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                circleEmptyText(circle.name, language),
                fontSize = 13.sp,
                color = Chaukas.Ink2,
            )
        }
    } else {
        alerts.forEach { alert ->
            val isScam = alert.verdict == "scam"
            ChaukasCard(
                color = if (isScam) Chaukas.Rose50 else Chaukas.Butter50,
                borderColor = if (isScam) Chaukas.Rose100 else Chaukas.Butter100,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(alert.byName, size = 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(if (alert.byUid == myUid) I18n.t("You", language) else alert.byName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(timeAgo(alert.createdAt), fontSize = 11.sp, color = Chaukas.Ink3)
                        }
                    }
                    Pill(
                        "${I18n.t(if (isScam) "Likely scam" else "Be careful", language)} · ${alert.riskScore}",
                        if (isScam) Chaukas.Rose100 else Chaukas.Butter100,
                        if (isScam) Chaukas.Rose700 else Chaukas.Butter700,
                    )
                }

                if (alert.note.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(alert.note, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Chaukas.Ink)
                }

                Spacer(Modifier.height(8.dp))
                // Excerpt in blockquote style
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Chaukas.Line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        alert.excerpt,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Chaukas.Ink2,
                        modifier = Modifier.padding(10.dp),
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text("${categoryLabel(alert.category, language)}. ${alert.summary}", fontSize = 13.sp, color = Chaukas.Ink2)

                Spacer(Modifier.height(10.dp))
                // Reactions row: got, scam, seen, call, love
                val reactions = listOf(
                    Triple("got", "I got this too", Icons.Outlined.Warning),
                    Triple("scam", "Agree, scam", Icons.Outlined.Shield),
                    Triple("seen", "Seen it", Icons.Outlined.Visibility),
                    Triple("call", "I'll call you", Icons.Outlined.Phone),
                    Triple("love", "Thanks", Icons.Outlined.Favorite),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    reactions.forEach { (key, label, icon) ->
                        val count = alert.reactions.values.count { it == key }
                        val mine = alert.reactions[myUid] == key
                        Surface(
                            onClick = {
                                scope.launch {
                                    runCatching { Repository.reactToAlert(circle.id, alert.id, key) }
                                }
                            },
                            shape = CircleShape,
                            color = if (mine) Chaukas.Brand100 else Color.White,
                            border = BorderStroke(1.dp, if (mine) Chaukas.Brand500 else Chaukas.Line),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(icon, null, modifier = Modifier.size(13.dp), tint = if (mine) Chaukas.Brand700 else Chaukas.Ink2)
                                Spacer(Modifier.width(4.dp))
                                Text(I18n.t(label, language), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (mine) Chaukas.Brand700 else Chaukas.Ink2)
                                if (count > 0) {
                                    Spacer(Modifier.width(4.dp))
                                    Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Chaukas.Ink)
                                }
                            }
                        }
                    }

                    // Forward Share button
                    Surface(
                        onClick = {
                            val fwd = InviteUtils.alertForwardText(
                                verdict = alert.verdict,
                                riskScore = alert.riskScore,
                                category = alert.category,
                                summary = alert.summary,
                                excerpt = alert.excerpt,
                                byName = alert.byName,
                            )
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, fwd)
                            }
                            ctx.startActivity(Intent.createChooser(send, "Forward warning"))
                        },
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(1.dp, Chaukas.Line),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Share, null, modifier = Modifier.size(13.dp), tint = Chaukas.Ink2)
                            Spacer(Modifier.width(4.dp))
                            Text(I18n.t("Share", language), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Chaukas.Ink2)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3 steps illustration pitch.
 */
@Composable
private fun CirclePitchSection(language: String = "English") {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple("1", "Scan or type an ID", "Scan a family member's Chaukas QR, or type their ID, like riya-4821."),
            Triple("2", "Tap Keep", "Your family group is made with them. They tap Keep on their phone too."),
            Triple("3", "Warn each other", "Scam warnings reach everyone by themselves, with personal details removed. Safe messages stay private."),
        ).forEach { (n, t, b) ->
            ChaukasCard {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Chaukas.Peach100),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(n, fontWeight = FontWeight.Bold, color = Chaukas.Peach700, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(I18n.t(t, language), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(I18n.t(b, language), fontSize = 13.sp, color = Chaukas.Ink2)
                    }
                }
            }
        }
    }
}

/**
 * KeepDialog modal when scanning/typing someone's ID.
 */
@Composable
private fun KeepDialog(
    handle: String,
    myGroup: Circle?,
    language: String = "English",
    onDismiss: () -> Unit,
    onKept: (groupId: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val cleanHandle = remember(handle) {
        InviteUtils.normalizeHandle(handle).ifBlank { handle.trim().lowercase().removePrefix("@") }
    }
    var person by remember(cleanHandle) { mutableStateOf<PersonInfo?>(null) }
    var loaded by remember(cleanHandle) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(cleanHandle) {
        loaded = false
        try {
            person = Repository.findPerson(cleanHandle, ctx)
        } catch (_: Exception) {
            person = null
        } finally {
            loaded = true
        }
    }

    val isMe = person != null && person?.uid == Repository.uid
    val alreadyInGroup = person != null && myGroup != null && myGroup.members.contains(person!!.uid)
    val alreadyInvited = person != null && myGroup != null && myGroup.invited.contains(person!!.uid)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            if (!loaded) Text(I18n.t("Looking up ID…", language))
            else if (person == null) Text(I18n.t("No one has this ID", language))
            else if (isMe) Text(I18n.t("This is your own ID", language))
            else if (alreadyInGroup) Text("${person!!.name} is already in ${myGroup.name}")
            else if (alreadyInvited) Text("${person!!.name} is already invited")
            else Text("${I18n.t("Keep", language)} ${person!!.name}?")
        },
        text = {
            if (!loaded) {
                Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (person == null) {
                Text(
                    if (InviteUtils.normalizeHandle(cleanHandle).isBlank()) {
                        "\"$cleanHandle\" is not a valid Chaukas ID. A Chaukas ID looks like name-1234."
                    } else {
                        "$cleanHandle is not a Chaukas ID yet. Ask them to check it in Family Circle on their phone."
                    }
                )
            } else if (isMe || alreadyInGroup || alreadyInvited) {
                Text(
                    if (alreadyInvited) "They have already been invited to ${myGroup.name}. Ask them to tap Stay in their app."
                    else "Chaukas ID: $cleanHandle"
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Avatar(person!!.name, size = 56.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(cleanHandle, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Chaukas.Ink3)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (myGroup != null) "They join ${myGroup.name}. They get the same popup on their phone. Only scam warnings are shared in the group. Safe messages are not."
                        else "This makes your family group with ${person!!.name}. They get the same popup on their phone. Only scam warnings are shared. Safe messages are not.",
                        fontSize = 14.sp,
                        color = Chaukas.Ink2,
                    )
                }
            }
        },
        confirmButton = {
            if (loaded && person != null && !isMe && !alreadyInGroup && !alreadyInvited) {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                Repository.keepPerson(cleanHandle, myGroup, ctx)
                            }.onSuccess { res ->
                                Toast.makeText(ctx, "${res.name} added to ${res.group}!", Toast.LENGTH_SHORT).show()
                                onKept(res.groupId)
                            }.onFailure { err ->
                                Toast.makeText(ctx, err.message ?: "Failed to keep", Toast.LENGTH_SHORT).show()
                                busy = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Chaukas.Brand600),
                ) {
                    Text(if (busy) "Adding…" else I18n.t("Keep", language))
                }
            } else if (loaded) {
                Button(onClick = onDismiss) { Text("OK") }
            }
        },
        dismissButton = {
            if (loaded && person != null && !isMe && !alreadyInGroup && !alreadyInvited) {
                TextButton(onClick = onDismiss, enabled = !busy) { Text(I18n.t("Don't keep", language)) }
            }
        },
    )
}

/* ------------------------------ SLEEK AUTO-SCAN TOGGLE BAR ------------------------------ */

@Composable
private fun AutoScanToggleBar(language: String = "English") {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Guard.isOn(ctx)) }
    var access by remember { mutableStateOf(Guard.hasAccess(ctx)) }
    var watchSms by remember { mutableStateOf(Guard.watches(ctx, "sms")) }
    var watchChat by remember { mutableStateOf(Guard.watches(ctx, "chat")) }
    var isOptimized by remember { mutableStateOf(Guard.isBatteryOptimized(ctx)) }
    var hasContactsPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var hasSmsPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    var explain by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        access = Guard.hasAccess(ctx)
        on = Guard.isOn(ctx)
        watchSms = Guard.watches(ctx, "sms")
        watchChat = Guard.watches(ctx, "chat")
        isOptimized = Guard.isBatteryOptimized(ctx)
        hasContactsPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        hasSmsPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (on && access) {
            Guard.start(ctx)
            Guard.forceRebind(ctx)
        }
        onPauseOrDispose { }
    }

    val requestContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasContactsPermission = granted
    }

    val requestSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasSmsPermission = granted
    }

    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (!access) {
                Guard.openAccess(ctx)
            } else {
                Guard.simulateTestAlert(ctx, if (watchSms) "sms" else "chat")
                Toast.makeText(
                    ctx,
                    I18n.t("Test alert sent! Check your notifications", language),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else if (!access) {
            Guard.openAccess(ctx)
        }
    }

    val onRunTest: (String) -> Unit = { kind ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notify.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Guard.simulateTestAlert(ctx, kind)
            Toast.makeText(
                ctx,
                I18n.t("Test alert sent! Check your notifications", language),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    var showTestMenu by remember { mutableStateOf(false) }

    val active = on && access && (watchSms || watchChat)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (active) Color(0xFFF0FAF4) else Color(0xFFF9FAFB),
        border = BorderStroke(1.dp, if (active) Color(0xFFC7EBD7) else Color(0xFFE5E7EB)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Row 1: Header (Shield Icon + Auto-Scan Title + Subtitle on Left, Switch on Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (active) Color(0xFFDCFCE7) else Color(0xFFE5E7EB)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = if (active) Color(0xFF15803D) else Chaukas.Ink2,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = I18n.t("Auto-Scan", language),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color(0xFF1E6140) else Chaukas.Ink,
                        )
                        Text(
                            text = if (active) {
                                when {
                                    watchSms && watchChat -> I18n.t("Active · WhatsApp & SMS", language)
                                    watchChat -> I18n.t("Active · WhatsApp", language)
                                    watchSms -> I18n.t("Active · SMS", language)
                                    else -> I18n.t("None selected", language)
                                }
                            } else if (on && !access) {
                                I18n.t("Fix permission", language)
                            } else {
                                I18n.t("Turned off", language)
                            },
                            fontSize = 11.sp,
                            color = if (active) Color(0xFF2C7D54) else if (on && !access) Color(0xFFB42318) else Chaukas.Ink3,
                        )
                    }
                }

                Switch(
                    checked = active,
                    onCheckedChange = { want ->
                        if (want) {
                            if (!watchSms && !watchChat) {
                                watchSms = true
                                watchChat = true
                                Guard.setWatches(ctx, "sms", true)
                                Guard.setWatches(ctx, "chat", true)
                            }
                            explain = true
                        } else {
                            on = false
                            Guard.setOn(ctx, false)
                            Guard.stop(ctx)
                        }
                    },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Chaukas.Mint600,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFD1D5DB),
                    ),
                )
            }

            // Row 2: Scan targets on left, Test button on right (Spacious, clean, never congested)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left side: Scan label + SMS chip + WhatsApp chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = I18n.t("Scan:", language),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (active) Color(0xFF2C7D54) else Chaukas.Ink3,
                    )

                    // SMS option pill
                    Surface(
                        onClick = {
                            val next = !watchSms
                            watchSms = next
                            Guard.setWatches(ctx, "sms", next)
                            if (next) {
                                if (!on) {
                                    on = true
                                    Guard.setOn(ctx, true)
                                }
                                Guard.start(ctx)
                                Guard.forceRebind(ctx)
                                if (!access) explain = true
                            } else if (!watchChat) {
                                on = false
                                Guard.setOn(ctx, false)
                                Guard.stop(ctx)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (watchSms && on) (if (access) Chaukas.Mint600 else Color(0xFF475467)) else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (watchSms && on) (if (access) Chaukas.Mint600 else Color(0xFF475467)) else Color(0xFFD0D5DD),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (watchSms && on) {
                                Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(
                                text = "SMS",
                                fontSize = 11.sp,
                                fontWeight = if (watchSms && on) FontWeight.Bold else FontWeight.Medium,
                                color = if (watchSms && on) Color.White else Chaukas.Ink2,
                            )
                        }
                    }

                    // WhatsApp option pill
                    Surface(
                        onClick = {
                            val next = !watchChat
                            watchChat = next
                            Guard.setWatches(ctx, "chat", next)
                            if (next) {
                                if (!on) {
                                    on = true
                                    Guard.setOn(ctx, true)
                                }
                                Guard.start(ctx)
                                Guard.forceRebind(ctx)
                                if (!access) explain = true
                            } else if (!watchSms) {
                                on = false
                                Guard.setOn(ctx, false)
                                Guard.stop(ctx)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (watchChat && on) (if (access) Chaukas.Mint600 else Color(0xFF475467)) else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (watchChat && on) (if (access) Chaukas.Mint600 else Color(0xFF475467)) else Color(0xFFD0D5DD),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (watchChat && on) {
                                Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(
                                text = "WhatsApp",
                                fontSize = 11.sp,
                                fontWeight = if (watchChat && on) FontWeight.Bold else FontWeight.Medium,
                                color = if (watchChat && on) Color.White else Chaukas.Ink2,
                            )
                        }
                    }
                }

                // Right side: Distinct Test button with Dropdown menu
                Box {
                    Surface(
                        onClick = { showTestMenu = true },
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) Color(0xFFDCFCE7) else Color(0xFFF3F4F6),
                        border = BorderStroke(1.dp, if (active) Color(0xFF86EFAC) else Color(0xFFD1D5DB)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = if (active) Color(0xFF15803D) else Chaukas.Ink2,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "${I18n.t("Test alert", language)} ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color(0xFF15803D) else Chaukas.Ink2,
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showTestMenu,
                        onDismissRequest = { showTestMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("WhatsApp Scam (Unknown Sender)", fontSize = 12.sp) },
                            onClick = {
                                showTestMenu = false
                                onRunTest("chat")
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Chat,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(15.dp),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("SMS Scam (Unknown Sender)", fontSize = 12.sp) },
                            onClick = {
                                showTestMenu = false
                                onRunTest("sms")
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Sms,
                                    contentDescription = null,
                                    tint = Color(0xFF1E40AF),
                                    modifier = Modifier.size(15.dp),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Saved Contact Scam (Ask Action)", fontSize = 12.sp) },
                            onClick = {
                                showTestMenu = false
                                onRunTest("known")
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.PermContactCalendar,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(15.dp),
                                )
                            },
                        )
                    }
                }
            }

            // Compact 1-line warning banner if notification access is missing
            if (on && !access) {
                Surface(
                    onClick = {
                        Guard.openAccess(ctx)
                        Guard.forceRebind(ctx)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3F2),
                    border = BorderStroke(1.dp, Color(0xFFFECDCA)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = Color(0xFFB42318),
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = I18n.t("Notification access needed to scan WhatsApp", language),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFB42318),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFB42318),
                        ) {
                            Text(
                                text = I18n.t("Enable", language),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // Compact 1-line warning banner if SMS permission is missing
            if (on && watchSms && !hasSmsPermission) {
                Surface(
                    onClick = { requestSms.launch(Manifest.permission.RECEIVE_SMS) },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Outlined.Sms,
                                contentDescription = null,
                                tint = Color(0xFF1E40AF),
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = I18n.t("SMS permission needed to scan text messages", language),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E40AF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2563EB),
                        ) {
                            Text(
                                text = I18n.t("Allow", language),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            icon = { Icon(Icons.Outlined.Shield, null, tint = Chaukas.Brand600) },
            title = { Text(I18n.t("Scan incoming SMS & WhatsApp messages?", language)) },
            text = {
                Text(
                    I18n.t(
                        "Chaukas runs entirely on this phone to catch scams before you tap. Your messages never leave this device.",
                        language,
                    ),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Chaukas.Ink2,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        explain = false
                        on = true
                        Guard.setOn(ctx, true)
                        Guard.start(ctx)
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                            requestContacts.launch(Manifest.permission.READ_CONTACTS)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (!access) {
                            Guard.openAccess(ctx)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Chaukas.Brand600),
                ) {
                    Text(I18n.t("Turn on", language))
                }
            },
            dismissButton = {
                TextButton(onClick = { explain = false }) {
                    Text(I18n.t("Not now", language), color = Chaukas.Ink3)
                }
            },
        )
    }
}

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

@Composable
private fun SelfTest() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
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
                                delay(400)
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
                            "2. App settings: Autostart on, Battery saver set to No restrictions.\n" +
                            "3. Lock Chaukas in Recent apps so the phone doesn't close it.",
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

@Composable
private fun RecentChecks(recent: List<Guard.Entry>, counts: Guard.Counts, showSafe: Boolean, onShowSafe: (Boolean) -> Unit) {
    val time = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT) }
    Spacer(Modifier.height(6.dp))
    Text(
        if (counts.today == 0) "No messages checked yet today."
        else "Today: ${counts.today} checked (SMS ${counts.todaySms} · WhatsApp ${counts.todayChat}) · ${counts.todayWarned} warning${if (counts.todayWarned == 1) "" else "s"}",
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
    )
    Text(
        "All time: ${counts.total} checked · ${counts.totalWarned} warnings. Counts never go down.",
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
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Also show a silent note when a message looks fine", fontSize = 13.sp, color = Chaukas.Ink2, modifier = Modifier.weight(1f))
        Switch(checked = showSafe, onCheckedChange = onShowSafe)
    }
}

/* ------------------------------ HISTORY SCREEN ------------------------------ */

private enum class HistoryFilter(val label: String) { ALL("All"), SAFE("Looks safe"), SUSPICIOUS("Be careful"), SCAM("Likely scam") }

@Composable
private fun HistoryScreen(vm: ChaukasViewModel, onCheck: () -> Unit) {
    val state = vm.history
    var openId by remember { mutableStateOf<String?>(null) }
    val open = openId?.let { id -> state.runs.find { it.id == id } }
    if (open != null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            TextButton(onClick = { openId = null }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(I18n.t("All checks", vm.language))
            }
            Spacer(Modifier.height(8.dp))
            ResultView(vm, open, onTellCircle = {})
        }
        return
    }

    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }
    val synced by remember { Repository.checks() }.collectAsState(initial = emptyList())
    val stats = historyStats(state)
    val list = state.runs.filter { r ->
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.SAFE -> r.analysis.verdict == Verdict.SAFE
            HistoryFilter.SUSPICIOUS -> r.analysis.verdict == Verdict.SUSPICIOUS
            HistoryFilter.SCAM -> r.analysis.verdict == Verdict.SCAM
        }
    }
    val otherDevices = synced.filter { s -> state.runs.none { it.id == s.id } }
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Eyebrow(I18n.t("Your checks", vm.language))
                Text(I18n.t("History", vm.language), style = MaterialTheme.typography.displaySmall)
            }
            if (state.runs.isNotEmpty()) {
                Row {
                    TextButton(onClick = {
                        val name = "chaukas-history-${java.time.LocalDate.now(java.time.ZoneOffset.UTC)}.csv"
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_SUBJECT, name)
                            putExtra(Intent.EXTRA_TEXT, historyCsv(state.runs))
                        }
                        ctx.startActivity(Intent.createChooser(send, I18n.t("Export", vm.language)))
                    }) {
                        Icon(Icons.Outlined.FileDownload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(I18n.t("Export", vm.language))
                    }
                    TextButton(onClick = { confirmClear = true }, colors = ButtonDefaults.textButtonColors(contentColor = Chaukas.Rose700)) {
                        Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(I18n.t("Clear all", vm.language))
                    }
                }
            }
        }

        // Stats grid
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HistoryStat(I18n.t("Checked", vm.language), stats.total.toString(), Chaukas.Brand100, Chaukas.Brand700, Modifier.weight(1f))
            HistoryStat(I18n.t("Scams caught", vm.language), stats.scams.toString(), Chaukas.Rose100, Chaukas.Rose700, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HistoryStat(I18n.t("Checked on device", vm.language), "${stats.localPct}%", Chaukas.Mint100, Chaukas.Mint700, Modifier.weight(1f))
            HistoryStat(I18n.t("Details kept private", vm.language), stats.hidden.toString(), Chaukas.Butter100, Chaukas.Butter700, Modifier.weight(1f))
        }

        if (state.runs.size >= 2) {
            HistoryInsightsCard(state.runs, vm.language)
        }

        // Filter chips
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.entries.forEach { f ->
                val on = filter == f
                Surface(
                    onClick = { filter = f },
                    shape = CircleShape,
                    color = if (on) Chaukas.Ink else Chaukas.Surface,
                    border = BorderStroke(1.dp, if (on) Chaukas.Ink else Chaukas.Line),
                ) {
                    Text(
                        I18n.t(f.label, vm.language),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (on) Color.White else Chaukas.Ink2,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        if (list.isEmpty()) {
            ChaukasCard {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(I18n.t("Nothing here yet", vm.language), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(I18n.t("Messages you check will appear here. Stored on this phone only.", vm.language), color = Chaukas.Ink2, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onCheck, colors = ButtonDefaults.buttonColors(containerColor = Chaukas.Brand600)) {
                        Text(I18n.t("Check a message", vm.language))
                    }
                }
            }
        } else {
            list.forEach { r ->
                val (bg, fg) = scoreColors(r.analysis.verdict.name.lowercase())
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Chaukas.Surface,
                    border = BorderStroke(1.dp, Chaukas.Line),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).clickable { openId = r.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
                                Text("${r.analysis.riskScore}", color = fg, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(I18n.t(r.analysis.verdict.label, vm.language), fontWeight = FontWeight.SemiBold)
                                    Text(" · ${categoryLabel(r.analysis.category, vm.language)}", color = Chaukas.Ink3, fontSize = 12.sp, maxLines = 1)
                                }
                                Text(excerpt(r.input, 110), color = Chaukas.Ink2, fontSize = 13.sp, maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                SourcePill(r.source.wire, vm.language)
                                Text(timeAgo(r.createdAt), color = Chaukas.Ink3, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        IconButton(onClick = { vm.deleteRun(r.id) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Chaukas.Ink3, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        if (otherDevices.isNotEmpty()) {
            Text(I18n.t("From your other devices", vm.language), color = Chaukas.Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            otherDevices.forEach { s -> SyncedRow(s, vm.language) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(I18n.t("Clear history", vm.language)) },
            text = { Text(I18n.t("Delete all checks stored on this device? Totals will be kept.", vm.language)) },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirmClear = false }, colors = ButtonDefaults.textButtonColors(contentColor = Chaukas.Rose700)) { Text(I18n.t("Delete", vm.language)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(I18n.t("Cancel", vm.language)) } },
        )
    }
}

private fun scoreColors(verdict: String) = when (verdict) {
    "scam" -> Chaukas.Rose100 to Chaukas.Rose700
    "suspicious" -> Chaukas.Butter100 to Chaukas.Butter700
    else -> Chaukas.Mint100 to Chaukas.Mint700
}

@Composable
private fun HistoryStat(label: String, value: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(value, color = fg, fontSize = 28.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Serif)
        Text(label, color = fg.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SourcePill(source: String, language: String = "English") {
    val (bg, fg) = when (source) {
        "on_device" -> Chaukas.Mint100 to Chaukas.Mint700
        "in_cloud" -> Chaukas.Sky100 to Chaukas.Sky600
        else -> Chaukas.Butter100 to Chaukas.Butter700
    }
    Text(I18n.t(sourceLabel(source), language), color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun SyncedRow(s: SyncedCheck, language: String = "English") {
    val (bg, fg) = scoreColors(s.verdict)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Chaukas.Surface, border = BorderStroke(1.dp, Chaukas.Line)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text("${s.riskScore}", color = fg, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.verdict.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                Text(s.excerpt, color = Chaukas.Ink2, fontSize = 13.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                SourcePill(s.source, language)
                Text(timeAgo(s.createdAt), color = Chaukas.Ink3, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun HistoryInsightsCard(runs: List<AnalysisRun>, language: String = "English") {
    val data = remember(runs) { historyInsights(runs) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChaukasCard {
            Text(I18n.t("Last 7 days", language), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                data.days.forEach { d ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                            val h = (d.total / data.maxDay.toFloat()).coerceIn(0f, 1f)
                            Column(
                                Modifier.fillMaxWidth(0.55f).fillMaxHeight(if (d.total == 0) 0.04f else h).clip(RoundedCornerShape(8.dp)).background(Chaukas.Brand50),
                            ) {
                                if (d.scam > 0) Box(Modifier.fillMaxWidth().weight(d.scam.toFloat()).background(Chaukas.Rose300))
                                if (d.suspicious > 0) Box(Modifier.fillMaxWidth().weight(d.suspicious.toFloat()).background(Chaukas.Butter300))
                                if (d.safe > 0) Box(Modifier.fillMaxWidth().weight(d.safe.toFloat()).background(Chaukas.Mint300))
                            }
                        }
                        Text(d.label, color = Chaukas.Ink3, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        ChaukasCard {
            Text(I18n.t("What is targeting you", language), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            if (data.top.isEmpty()) {
                Text(I18n.t("No scams yet. Keep it that way.", language), color = Chaukas.Ink2, fontSize = 13.sp)
            } else {
                data.top.forEach { (cat, n) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(categoryLabel(cat, language), fontSize = 13.sp, modifier = Modifier.width(130.dp), maxLines = 1)
                        Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(Chaukas.Rose50)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(n / data.catMax.toFloat()).clip(CircleShape).background(Chaukas.Rose300))
                        }
                        Text("$n", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/* ------------------------------ DOJO SCREEN ------------------------------ */

private data class LocalizedDojoContent(
    val message: String,
    val explanation: String,
    val tells: List<String>,
)

private data class DojoRoundItem(
    val sender: String,
    val channel: String,
    val message: String,
    val isScam: Boolean,
    val explanation: String,
    val tells: List<String>,
    val localized: Map<String, LocalizedDojoContent> = emptyMap(),
)

private data class DojoRankItem(val at: Int, val name: String)

private val DOJO_RANKS = listOf(
    DojoRankItem(0, "Curious beginner"),
    DojoRankItem(3, "Sharp eye"),
    DojoRankItem(6, "Scam spotter"),
    DojoRankItem(10, "Fraud fighter"),
    DojoRankItem(15, "Chaukas sensei"),
)

private val DOJO_ROUNDS = listOf(
    DojoRoundItem(
        sender = "+1 (530) 555-0142",
        channel = "text message",
        message = "India Post: Your parcel is on hold due to an incomplete address. Update within 12 hours to avoid return: https://indiapost-redelivery-help.top/track",
        isScam = true,
        explanation = "Delivery companies do not text you payment or address links from personal numbers. Check tracking in the official app.",
        tells = listOf("\".top\" domain that is not indiapost.gov.in", "12-hour deadline", "Unexpected text from a random number"),
        localized = mapOf(
            "Hindi" to LocalizedDojoContent(
                "इंडिया पोस्ट: आपका पार्सल पते की कमी के कारण रुका हुआ है। पार्सल वापस होने से बचाने के लिए 12 घंटे में अपडेट करें: https://indiapost-redelivery-help.top/track",
                "डाक विभाग कभी भी व्यक्तिगत मोबाइल नंबर से पते या भुगतान के लिए लिंक नहीं भेजता। हमेशा आधिकारिक वेबसाइट indiapost.gov.in पर देखें।",
                listOf("फर्जी .top लिंक जो indiapost.gov.in नहीं है", "12 घंटे की झूठी समय सीमा", "अज्ञात नंबर से मैसेज"),
            ),
            "Bengali" to LocalizedDojoContent(
                "ইন্ডিয়া পোস্ট: অসম্পূর্ণ ঠিকানার কারণে আপনার পার্সেল আটকে আছে। ফেরত এড়াতে ১২ ঘণ্টার মধ্যে আপডেট করুন: https://indiapost-redelivery-help.top/track",
                "ডাক বিভাগ কখনো ব্যক্তিগত নম্বর থেকে লিঙ্ক পাঠায় না। সর্বদা অফিসিয়াল indiapost.gov.in ওয়েবসাইটে ট্র্যাক করুন।",
                listOf("ভুয়া .top লিঙ্ক", "১২ ঘণ্টার চাপ সৃষ্টি করা সময়সীমা", "অপরিচিত নম্বর থেকে আসা মেসেজ"),
            ),
            "Tamil" to LocalizedDojoContent(
                "இந்தியா போஸ்ட்: முகவரி முழுமையடையாததால் உங்கள் பார்சல் நிறுத்தி வைக்கப்பட்டுள்ளது. திரும்புவதைத் தவிர்க்க 12 மணி நேரத்திற்குள் புதுப்பிக்கவும்: https://indiapost-redelivery-help.top/track",
                "அஞ்சல் துறை ஒருபோதும் தனிப்பட்ட எண்களிலிருந்து கட்டணம் அல்லது முகவரி இணைப்புகளை அனுப்பாது. அதிகாரப்பூர்வ indiapost.gov.in தளத்தில் சரிபார்க்கவும்.",
                listOf("அதிகாரப்பூர்வமற்ற .top இணைப்பு", "12 மணி நேர அவசர கெடு", "தெரியாத எண்ணிலிருந்து வந்த மெசேஜ்"),
            ),
            "Telugu" to LocalizedDojoContent(
                "ఇండియా పోస్ట్: చిరునామా సరిగ్గా లేకపోవడం వల్ల మీ పార్శిల్ ఆపివేయబడింది. వెనక్కి వెళ్లకుండా ఉండటానికి 12 గంటల్లో అప్‌డేట్ చేయండి: https://indiapost-redelivery-help.top/track",
                "తపాలా శాఖ ఎప్పుడూ వ్యక్తిగత నంబర్ల నుండి లింక్‌లను పంపదు. ఎల్లప్పుడూ అధికారిక indiapost.gov.in సైట్‌ను చూడండి.",
                listOf("అధికారికం కాని .top లింక్", "12 గంటల తప్పుడు గడువు", "తెలియని నంబర్ నుండి మెసేజ్"),
            ),
            "Marathi" to LocalizedDojoContent(
                "इंडिया पोस्ट: अपूर्ण पत्त्यामुळे तुमचे पार्सल थांबवले आहे. परत जाणे टाळण्यासाठी १२ तासांत अपडेट करा: https://indiapost-redelivery-help.top/track",
                "टपाल खाते कधीही वैयक्तिक क्रमांकावरून लिंक पाठवत नाही. अधिकृत indiapost.gov.in वर ट्रॅक करा.",
                listOf("अधिकृत नसलेली .top बनावट लिंक", "१२ तासांची खोटी मुदत", "अज्ञात नंबरवरून आलेला मेसेज"),
            ),
        ),
    ),
    DojoRoundItem(
        sender = "BP-DHBVN",
        channel = "text message",
        message = "Dear customer, your electricity power will disconnect tonight at 9:30 PM because previous month bill was not updated. Please immediately contact electricity officer 9876543210. Thanks.",
        isScam = true,
        explanation = "Electricity boards never disconnect power without advance written notices or ask you to call personal mobile numbers.",
        tells = listOf("Threat of imminent power cut tonight", "Personal 10-digit mobile number", "Urgent panic tactic"),
        localized = mapOf(
            "Hindi" to LocalizedDojoContent(
                "प्रिय उपभोक्ता, आपके पिछले महीने के बिजली बिल का भुगतान न होने के कारण आज रात 9.30 बजे बिजली काट दी जाएगी। तुरंत बिजली अधिकारी 9876543210 से संपर्क करें। धन्यवाद।",
                "बिजली बोर्ड कभी भी व्यक्तिगत फोन नंबर से बिजली काटने की धमकी वाले एसएमएस नहीं भेजते। हमेशा बिजली विभाग के आधिकारिक बिल पोर्टल से जाँच करें।",
                listOf("आज रात बिजली काटने की सीधी धमकी", "व्यक्तिगत 10 अंकों का मोबाइल नंबर", "घबराहट पैदा करने का हथकंडा"),
            ),
            "Bengali" to LocalizedDojoContent(
                "প্রিয় গ্রাহক, আগের মাসের বিল আপডেট না থাকায় আজ রাত ৯:৩০ এ আপনার বিদ্যুৎ সংযোগ বিচ্ছিন্ন করা হবে। অবিলম্বে বিদ্যুৎ কর্মকর্তা 9876543210 এর সাথে যোগাযোগ করুন। ধন্যবাদ।",
                "বিদ্যুৎ পর্ষদ কখনো ব্যক্তিগত মোবাইল নম্বরে কল করতে বলে না বা নোটিশ ছাড়া লাইন কাটার হুমকি দেয় না।",
                listOf("আজ রাতেই বিদ্যুৎ কাটার হুমকি", "ব্যক্তিগত মোবাইল নম্বর", "আতঙ্ক সৃষ্টির কৌশল"),
            ),
            "Tamil" to LocalizedDojoContent(
                "அன்புள்ள வாடிக்கையாளரே, கடந்த மாத பில் புதுப்பிக்கப்படாததால் இன்றிரவு 9:30 மணிக்கு உங்கள் மின் இணைப்பு துண்டிக்கப்படும். உடனடியாக மின் அதிகாரி 9876543210-ஐத் தொடர்பு கொள்ளவும்.",
                "மின்சார வாரியம் ஒருபோதும் தனிப்பட்ட மொபைல் எண்களுக்கு அழைக்கச் சொல்லாது அல்லது அவசர மிரட்டல் விடுக்காது.",
                listOf("இன்றிரவே மின்சாரம் துண்டிக்கப்படும் என்ற மிரட்டல்", "தனிப்பட்ட 10 இலக்க மொபைல் எண்", "பயமுறுத்தும் உத்தி"),
            ),
            "Telugu" to LocalizedDojoContent(
                "ప్రియమైన వినియోగదారుడా, మునుపటి నెల బిల్లు అప్‌డేట్ కానందున ఈ రాత్రి 9:30 గంటలకు మీ విద్యుత్ కనెక్షన్ కట్ చేయబడుతుంది. వెంటనే విద్యుత్ అధికారి 9876543210ని సంప్రదించండి.",
                "విద్యుత్ శాఖ ఎప్పుడూ వ్యక్తిగత మొబైల్ నంబర్లకు కాల్ చేయమని లేదా అకస్మాత్తుగా కరెంట్ కట్ చేస్తామని మెసేజ్ చేయదు.",
                listOf("ఈ రాత్రే కరెంట్ కట్ చేస్తామనే బెదిరింపు", "వ్యక్తిగత మొబైల్ నంబర్", "భయాందోళన కలిగించే వ్యూహం"),
            ),
            "Marathi" to LocalizedDojoContent(
                "प्रिय ग्राहक, मागील महिन्याचे वीज बिल अपडेट न झाल्यामुळे आज रात्री ९:३० वाजता वीज खंडित केली जाईल. कृपया लगेच वीज अधिकारी 9876543210 यांच्याशी संपर्क साधा.",
                "वीज महामंडळ कधीही वैयक्तिक मोबाईल क्रमांकावर कॉल करायला सांगत नाही किंवा अशी तडकाफडकी धमकी देत नाही.",
                listOf("आज रात्रीच वीज कापण्याची धमकी", "वैयक्तिक १० अंकी मोबाईल नंबर", "घबराट पसरवण्याची युक्ती"),
            ),
        ),
    ),
    DojoRoundItem(
        sender = "+91 91234 56789",
        channel = "WhatsApp message",
        message = "SBI Alert: Your YONO account has been blocked due to pending KYC. Tap http://sbi-kyc-update.apk to update PAN and unblock immediately.",
        isScam = true,
        explanation = "Banks never send APK download links or ask you to update KYC via third-party web addresses.",
        tells = listOf("Asks to download an APK file", "Suspicious link that is not sbi.co.in", "Artificial account block threat"),
        localized = mapOf(
            "Hindi" to LocalizedDojoContent(
                "SBI अलर्ट: आपका YONO खाता लंबित KYC के कारण ब्लॉक कर दिया गया है। पैन कार्ड अपडेट कर तुरंत खाता चालू करने के लिए यहाँ टैप करें: http://sbi-kyc-update.apk",
                "बैंक कभी भी व्हाट्सएप पर APK डाउनलोड करने का लिंक नहीं भेजते। यह फोन हैक करने का खतरनाक फ्रॉड है।",
                listOf("APK फ़ाइल डाउनलोड करने को कहना", "फर्जी लिंक जो sbi.co.in नहीं है", "खाता ब्लॉक करने की झूठी धमकी"),
            ),
            "Bengali" to LocalizedDojoContent(
                "SBI সতর্কতা: পেন্ডিং KYC-এর কারণে আপনার YONO অ্যাকাউন্ট ব্লক করা হয়েছে। প্যান আপডেট করতে এবং অবিলম্বে আনব্লক করতে ট্যাপ করুন: http://sbi-kyc-update.apk",
                "ব্যাংক কখনোই APK ফাইল ডাউনলোড করার লিঙ্ক পাঠায় না। এটি ফোন হ্যাক করার ফাঁদ।",
                listOf("APK ফাইল ডাউনলোডের অনুরোধ", "সন্দেহজনক ভুয়া লিঙ্ক", "অ্যাকাউন্ট ব্লকের ভীতি প্রদর্শন"),
            ),
            "Tamil" to LocalizedDojoContent(
                "SBI எச்சரிக்கை: நிலுவையில் உள்ள KYC காரணமாக உங்கள் YONO கணக்கு முடக்கப்பட்டுள்ளது. PAN-ஐப் புதுப்பித்து உடனே இயக்க இங்கே தட்டவும்: http://sbi-kyc-update.apk",
                "வங்கிகள் ஒருபோதும் APK பதிவிறக்க இணைப்புகளை அனுப்பாது. இது ஆபத்தான செயலி மோசடி.",
                listOf("APK பதிவிறக்க கோரிக்கை", "sbi.co.in அல்லாத போலி இணைப்பு", "கணக்கு முடக்க அச்சுறுத்தல்"),
            ),
            "Telugu" to LocalizedDojoContent(
                "SBI హెచ్చరిక: పెండింగ్ KYC కారణంగా మీ YONO ఖాతా బ్లాక్ చేయబడింది. పాన్ కార్డ్ అప్‌డేట్ చేసి వెంటనే అన్‌బ్లాక్ చేయడానికి ట్యాప్ చేయండి: http://sbi-kyc-update.apk",
                "బ్యాంకులు ఎప్పుడూ APK ఫైల్స్ డౌన్‌లోడ్ చేయమని లింకులు పంపవు. ఇది హ్యాకింగ్ మోసం.",
                listOf("APK ఫైల్ డౌన్‌లోడ్ కోరడం", "sbi.co.in కాని అనుమానాస్పద లింక్", "ఖాతా బ్లాక్ బెదిరింపు"),
            ),
            "Marathi" to LocalizedDojoContent(
                "SBI अलर्ट: प्रलंबित KYC मुळे तुमचे YONO खाते ब्लॉक करण्यात आले आहे. पॅन अपडेट करून त्वरित अनब्लॉक करण्यासाठी टॅप करा: http://sbi-kyc-update.apk",
                "बँका कधीही APK डाउनलोड लिंक पाठवत नाहीत. हे फोन हॅक करण्याचे षड्यंत्र असते.",
                listOf("APK फाइल डाउनलोड करण्यास सांगणे", "sbi.co.in नसलेली बनावट लिंक", "खाते ब्लॉक करण्याची धमकी"),
            ),
        ),
    ),
    DojoRoundItem(
        sender = "Swiggy",
        channel = "text message",
        message = "482910 is your Swiggy OTP for login. Never share your OTP with anyone, including Swiggy delivery partners.",
        isScam = false,
        explanation = "Direct verification code requesting no action other than entering it into the official app, explicitly warning against sharing.",
        tells = listOf("Official sender header", "Explicit warning not to share OTP", "No hyperlinks or phone numbers to call"),
        localized = mapOf(
            "Hindi" to LocalizedDojoContent(
                "482910 आपका Swiggy लॉगिन OTP है। अपना OTP किसी के साथ साझा न करें, डिलीवरी पार्टनर के साथ भी नहीं।",
                "यह एक सुरक्षित और असली लॉगिन कोड है। इसमें कोई लिंक नहीं है और स्पष्ट रूप से ओटीपी किसी को न देने की चेतावनी है।",
                listOf("आधिकारिक कंपनी हेडर", "OTP न बांटने की स्पष्ट चेतावनी", "कोई फर्जी लिंक या नंबर नहीं"),
            ),
            "Bengali" to LocalizedDojoContent(
                "482910 হলো আপনার Swiggy লগইন OTP। ডেলিভারি পার্টনার সহ কারও সাথে আপনার OTP শেয়ার করবেন না।",
                "এটি আসল লগইন ওটিপি। কোনো লিঙ্ক নেই এবং ওটিপি শেয়ার না করার পরিষ্কার নির্দেশ রয়েছে।",
                listOf("অফিসিয়াল প্রেরক হেডার", "OTP শেয়ার না করার সতর্কতা", "কোনো ক্ষতিকারক লিঙ্ক নেই"),
            ),
            "Tamil" to LocalizedDojoContent(
                "482910 என்பது உங்கள் Swiggy உள்நுழைவு OTP. டெலிவரி பார்ட்னர்கள் உட்பட யாருடனும் உங்கள் OTP-யைப் பகிர வேண்டாம்.",
                "இது உண்மையான உள்நுழைவு குறியீடு. இதில் எந்த இணைப்பும் இல்லை மற்றும் OTP-யைப் பகிர வேண்டாம் என்று தெளிவாகக் கூறுகிறது.",
                listOf("அதிகாரப்பூர்வ அனுப்புநர்", "OTP பகிரக்கூடாது என்ற தெளிவான எச்சரிக்கை", "சந்தேகத்திற்குரிய லிங்க் இல்லை"),
            ),
            "Telugu" to LocalizedDojoContent(
                "482910 అనేది మీ Swiggy లాగిన్ OTP. డెలివరీ భాగస్వాములతో సహా ఎవరితోనూ మీ OTPని పంచుకోవద్దు.",
                "ఇది నిజమైన లాగిన్ కోడ్. ఇందులో ఎటువంటి లింక్‌లు లేవు మరియు OTPని ఎవరికీ ఇవ్వవద్దని స్పష్టంగా హెచ్చరిస్తుంది.",
                listOf("అధికారిక సంస్థ పేరు", "OTP పంచుకోవద్దని స్పష్టమైన హెచ్చరిక", "ఎలాంటి తప్పుడు లింక్ లేదు"),
            ),
            "Marathi" to LocalizedDojoContent(
                "482910 हा तुमचा Swiggy लॉगिन OTP आहे. डिलिव्हरी पार्टनरसह कोणाशीही तुमचा OTP शेअर करू नका.",
                "हा खरा लॉगिन कोड आहे. यात कोणतीही लिंक नाही आणि OTP गुप्त ठेवण्याची स्पष्ट सूचना आहे.",
                listOf("अधिकृत पाठवणारा", "OTP शेअर न करण्याचा स्पष्ट इशारा", "कोणतीही संशयास्पद लिंक नाही"),
            ),
        ),
    ),
    DojoRoundItem(
        sender = "Daily Earn HR",
        channel = "WhatsApp message",
        message = "Earn Rs 3000-5000 daily working from home! Simply like YouTube videos and review products. Message our HR manager on Telegram: @earn_daily_india",
        isScam = true,
        explanation = "Classic task fraud scam. Scammers pay small sums initially to gain trust, then trick victims into depositing large amounts.",
        tells = listOf("Unrealistic daily pay for simple tasks", "Redirects to Telegram for chat", "Unsolicited job offer"),
        localized = mapOf(
            "Hindi" to LocalizedDojoContent(
                "घर बैठे रोज़ ₹3000-5000 कमाएं! बस YouTube वीडियो लाइक करें और रेटिंग दें। हमारे HR मैनेजर से Telegram पर बात करें: @earn_daily_india",
                "यह क्लासिक 'टास्क स्कैम' है। पहले छोटे पैसे देकर विश्वास जीतते हैं, फिर प्रीपेड टास्क के नाम पर लाखों रुपये ठग लेते हैं।",
                listOf("आसान काम के लिए अवास्तविक रोज़ाना कमाई", "टेलीग्राम पर चैट करने का दबाव", "अवांछित नौकरी का लालच"),
            ),
            "Bengali" to LocalizedDojoContent(
                "ঘরে বসে প্রতিদিন ৩০০০-৫০০০ টাকা আয় করুন! শুধু ইউটিউব ভিডিও লাইক করুন। টেলিগ্রামে আমাদের এইচআর ম্যানেজারের সাথে যোগাযোগ করুন: @earn_daily_india",
                "এটি কুখ্যাত টাস্ক প্রতারণা। প্রথমে সামান্য টাকা দিয়ে ফাঁদে ফেলে, পরে লাখ লাখ টাকা হাতিয়ে নেয়।",
                listOf("অবাস্তব দৈনিক আয়ের প্রলোভন", "টেলিগ্রামে নিয়ে যাওয়ার চেষ্টা", "অযাচিত চাকরির প্রস্তাব"),
            ),
            "Tamil" to LocalizedDojoContent(
                "வீட்டிலிருந்தே தினமும் ரூ. 3000-5000 சம்பாதிக்கலாம்! YouTube வீடியோக்களை லைக் செய்தால் போதும். Telegram-ல் எங்கள் HR மேலாளரைத் தொடர்பு கொள்ளவும்: @earn_daily_india",
                "இது டாஸ்க் மோசடி. ஆரம்பத்தில் சிறிய தொகையைக் கொடுத்து நம்ப வைத்து, பின்னர் பெரும் தொகையைப் பறிப்பார்கள்.",
                listOf("எளிய வேலைக்கு அதீத தினசரி வருமானம்", "Telegram-ல் பேச அழைப்பது", "கேட்காத வேலை வாய்ப்பு"),
            ),
            "Telugu" to LocalizedDojoContent(
                "ఇంట్లో కూర్చుని రోజూ రూ. 3000-5000 సంపాదించండి! కేవలం యూట్యూబ్ వీడియోలను లైక్ చేయండి. టెలిగ్రామ్‌లో మా HR మేనేజర్‌ను సంప్రదించండి: @earn_daily_india",
                "ఇది ప్రసిద్ధ టాస్క్ ఫ్రాడ్. మొదట కొద్దిగా డబ్బు ఇచ్చి నమ్మకం కుదిర్చి, తర్వాత పెట్టుబడి పేరుతో భారీగా మోసం చేస్తారు.",
                listOf("చిన్న పనికి అవాస్తవ రోజువారీ సంపాదన", "టెలిగ్రామ్‌కు మళ్ళించడం", "అడగని ఉద్యోగ ఆఫర్"),
            ),
            "Marathi" to LocalizedDojoContent(
                "घरी बसून दररोज ३०००-५००० रुपये कमवा! फक्त YouTube व्हिडिओ लाईक करा. Telegram वर आमच्या HR मॅनेजरशी संपर्क साधा: @earn_daily_india",
                "हा प्रसिद्ध टास्क फ्रॉड आहे. सुरुवातीला थोडे पैसे देऊन विश्वास जिंकतात आणि नंतर मोठी फसवणूक करतात.",
                listOf("सोप्या कामासाठी अवाजवी रोजची कमाई", "टेलिग्रामवर चॅट करायला लावणे", "अनाहूत नोकरीची ऑफर"),
            ),
        ),
    ),
    DojoRoundItem(
        sender = "Apollo Clinic",
        channel = "text message",
        message = "Reminder: You have an appointment with Dr. Mehta tomorrow at 10:30 AM at Apollo Clinic Bandra. Reply 1 to confirm or call 022-26400000 to reschedule.",
        isScam = false,
        explanation = "Routine appointment reminder confirming doctor and clinic details without requesting sensitive financial credentials.",
        tells = listOf("Specific doctor and location mentioned", "No requests for payment or bank details", "Provides standard landline clinic contact"),
        localized = mapOf(
            "Hindi" to LocalizedDojoContent(
                "याद दिलाना: कल सुबह 10:30 बजे अपोलो क्लिनिक बांद्रा में डॉ. मेहता के साथ आपकी अपॉइंटमेंट है। पुष्टि के लिए 1 लिखें या समय बदलने के लिए 022-26400000 पर कॉल करें।",
                "यह एक असली अपॉइंटमेंट रिमाइंडर है। इसमें डॉक्टर और क्लिनिक का नाम स्पष्ट है और कोई बैंक जानकारी नहीं माँगी गई है।",
                listOf("डॉक्टर और क्लिनिक का स्पष्ट नाम", "कोई भुगतान या OTP नहीं माँगा गया", "मानक लैंडलाइन संपर्क दिया गया"),
            ),
            "Bengali" to LocalizedDojoContent(
                "স্মারক: আগামীকাল সকাল ১০:৩০ টায় অ্যাপোलो ক্লিনিকে ডাঃ মেহতার সাথে আপনার অ্যাপয়েন্টমেন্ট রয়েছে। নিশ্চিত করতে ১ লিখুন বা ০২২-২৬৪০০০০০ নম্বরে কল করুন।",
                "এটি আসল অ্যাপয়েন্টমেন্ট রিমাইন্ডার। কোনো টাকা বা সংবেদনশীল তথ্য চাওয়া হয়নি।",
                listOf("নির্দিষ্ট ডাক্তার ও ক্লিনিকের বিবরণ", "কোনো পেমেন্ট বা তথ্য চাওয়া হয়নি", "অফিসিয়াল ল্যান্ডলাইন নম্বর"),
            ),
            "Tamil" to LocalizedDojoContent(
                "நினைவூட்டல்: அப்பல்லோ கிளினிக்கில் நாளை காலை 10:30 மணிக்கு டாக்டர் மேத்தாவுடன் உங்களுக்கு அப்பாயின்ட்மென்ட் உள்ளது. உறுதிப்படுத்த 1 என பதிலளிக்கவும் அல்லது 022-26400000 என்ற எண்ணை அழைக்கவும்.",
                "இது வழக்கமான மருத்துவ நினைவூட்டல். பணம் அல்லது வங்கி விவரங்கள் எதுவும் கேட்கப்படவில்லை.",
                listOf("குறிப்பிட்ட மருத்துவர் மற்றும் இடம்", "பணம் அல்லது குறியீடு கேட்கப்படவில்லை", "வழக்கமான கிளினிக் தொடர்பு எண்"),
            ),
            "Telugu" to LocalizedDojoContent(
                "రిమైండర్: రేపు ఉదయం 10:30 గంటలకు అపోలో క్లినిక్‌లో డాక్టర్ మెహతాతో మీకు అపాయింట్‌మెంట్ ఉంది. నిర్ధారించడానికి 1 అని రిప్లై ఇవ్వండి లేదా 022-26400000 కు కాల్ చేయండి.",
                "ఇది నిజమైన అపాయింట్‌మెంట్ సందేశం. ఇందులో ఎటువంటి బ్యాంకింగ్ వివరాలు లేదా డబ్బు కోరలేదు.",
                listOf("నిర్దిష్ట డాక్టర్ మరియు స్థలం వివరాలు", "డబ్బు లేదా OTP అడగలేదు", "ప్రామాణిక ల్యాండ్‌లైన్ నంబర్"),
            ),
            "Marathi" to LocalizedDojoContent(
                "स्मरणपत्र: उद्या सकाळी १०:३० वाजता अपोलो क्लिनिकमध्ये डॉ. मेहता यांच्यासोबत तुमची अपॉइंटमेंट आहे. निश्चित करण्यासाठी १ पाठवा किंवा ०२२-२६४००००० वर संपर्क साधा.",
                "हे खरे अपॉइंटमेंट रिमाइंडर आहे. यात कोणतीही बँक माहिती किंवा पैसे मागितलेले नाहीत.",
                listOf("डॉक्टर आणि क्लिनिकचा स्पष्ट उल्लेख", "कोणतीही पैशांची मागणी नाही", "अधिकृत संपर्क क्रमांक"),
            ),
        ),
    ),
    DojoRoundItem(
        sender = "+91 88888 12345",
        channel = "text message",
        message = "Dear user, Rs 2,500 refund approved for your order. Tap link to open UPI and enter your UPI PIN to claim money into your bank: bit.ly/upi-cashback-now",
        isScam = true,
        explanation = "Crucial rule of UPI: You NEVER enter your UPI PIN to receive money or get a refund. Entering your PIN always debits your account.",
        tells = listOf("Asks for UPI PIN to receive money", "Shortened bit.ly link", "Promise of free or approved cashback"),
        localized = mapOf(
            "Hindi" to LocalizedDojoContent(
                "प्रिय उपभोक्ता, आपके ऑर्डर का ₹2,500 रिफंड स्वीकृत हुआ। पैसे अपने बैंक में पाने के लिए लिंक खोलें और अपना UPI PIN दर्ज करें: bit.ly/upi-cashback-now",
                "UPI का सबसे बुनियादी नियम: पैसे पाने या रिफंड लेने के लिए कभी UPI PIN नहीं डालना पड़ता। पिन डालने से हमेशा आपके खाते से पैसे कटते हैं!",
                listOf("पैसे पाने के लिए UPI PIN मांगना", "असुरक्षित छोटा bit.ly लिंक", "फर्जी रिफंड का लालच"),
            ),
            "Bengali" to LocalizedDojoContent(
                "প্রিয় গ্রাহক, আপনার অর্ডারের জন্য ২৫০০ টাকা রিফান্ড অনুমোদিত হয়েছে। টাকা পেতে UPI PIN লিখুন: bit.ly/upi-cashback-now",
                "UPI-এর প্রধান নিয়ম: টাকা পেতে কখনো UPI PIN দিতে হয় না। পিন দিলে আপনার অ্যাকাউন্ট থেকেই টাকা কেটে নেওয়া হবে!",
                listOf("টাকা পাওয়ার জন্য UPI PIN চাওয়া", "সংক্ষিপ্ত bit.ly লিঙ্ক", "ভুয়া রিফান্ডের প্রলোভন"),
            ),
            "Tamil" to LocalizedDojoContent(
                "அன்புள்ள வாடிக்கையாளரே, உங்கள் ஆர்டருக்கான ரூ. 2,500 ரீஃபண்ட் அங்கீகரிக்கப்பட்டது. பணத்தைப் பெற இணைப்பைத் திறந்து உங்கள் UPI PIN-ஐ உள்ளிடவும்: bit.ly/upi-cashback-now",
                "UPI-இன் மிக முக்கியமான விதி: பணத்தைப் பெறுவதற்கு நீங்கள் ஒருபோதும் UPI PIN-ஐ உள்ளிடத் தேவையில்லை. பின்னை உள்ளிட்டால் உங்கள் பணம் பறிபோகும்!",
                listOf("பணம் பெற UPI PIN கேட்பது", "சுருக்கப்பட்ட bit.ly லிங்க்", "இலவச பண ஆசை"),
            ),
            "Telugu" to LocalizedDojoContent(
                "ప్రియమైన వినియోగదారుడా, మీ ఆర్డర్‌కు రూ. 2,500 రీఫండ్ ఆమోదించబడింది. డబ్బు పొందడానికి UPI PIN నమోదు చేయండి: bit.ly/upi-cashback-now",
                "UPI ముఖ్య నియమం: డబ్బు పొందడానికి ఎప్పుడూ UPI PIN నమోదు చేయకూడదు. పిన్ కొడితే మీ ఖాతా నుంచే డబ్బులు కట్ అవుతాయి!",
                listOf("డబ్బులు తీసుకోవడానికి UPI PIN అడగడం", "అనుమానాస్పద bit.ly లింక్", "తప్పుడు రీఫండ్ వాగ్దానం"),
            ),
            "Marathi" to LocalizedDojoContent(
                "प्रिय ग्राहक, तुमच्या ऑर्डरचा ₹२,५०० परतावा मंजूर झाला आहे. पैसे खात्यात मिळवण्यासाठी UPI PIN टाका: bit.ly/upi-cashback-now",
                "UPI चा सर्वात महत्त्वाचा नियम: पैसे मिळवण्यासाठी कधीही UPI PIN टाकावा लागत नाही. पिन टाकल्यास तुमच्याच खात्यातून पैसे कापले जातात!",
                listOf("पैसे मिळवण्यासाठी UPI PIN मागणे", "शॉर्ट केलेली bit.ly लिंक", "खोट्या रिफंडचे आमिष"),
            ),
        ),
    ),
)

@Composable
private fun DojoScreen(vm: ChaukasViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chaukas_dojo_v1", Context.MODE_PRIVATE) }
    var played by remember { mutableIntStateOf(prefs.getInt("played", 0)) }
    var correct by remember { mutableIntStateOf(prefs.getInt("correct", 0)) }
    var streak by remember { mutableIntStateOf(prefs.getInt("streak", 0)) }
    var best by remember { mutableIntStateOf(prefs.getInt("best", 0)) }

    var roundIndex by remember { mutableIntStateOf((0 until DOJO_ROUNDS.size).random()) }
    var guess by remember { mutableStateOf<Boolean?>(null) }

    val round = DOJO_ROUNDS[roundIndex % DOJO_ROUNDS.size]
    val roundContent = round.localized[vm.language] ?: LocalizedDojoContent(round.message, round.explanation, round.tells)
    val right = guess?.let { it == round.isScam }

    val rank = DOJO_RANKS.reversed().firstOrNull { correct >= it.at } ?: DOJO_RANKS.first()
    val nextRank = DOJO_RANKS.firstOrNull { it.at > correct }

    fun answer(saysScam: Boolean) {
        if (guess != null) return
        guess = saysScam
        val isRight = saysScam == round.isScam
        val newPlayed = played + 1
        val newCorrect = correct + if (isRight) 1 else 0
        val newStreak = if (isRight) streak + 1 else 0
        val newBest = maxOf(best, newStreak)

        played = newPlayed
        correct = newCorrect
        streak = newStreak
        best = newBest

        prefs.edit()
            .putInt("played", newPlayed)
            .putInt("correct", newCorrect)
            .putInt("streak", newStreak)
            .putInt("best", newBest)
            .apply()
    }

    fun nextRound() {
        guess = null
        var nextIdx = (roundIndex + 1) % DOJO_ROUNDS.size
        if (DOJO_ROUNDS.size > 1 && nextIdx == roundIndex) {
            nextIdx = (nextIdx + 1) % DOJO_ROUNDS.size
        }
        roundIndex = nextIdx
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow(I18n.t("Practice makes you scam-proof", vm.language))
            Text(
                text = I18n.t("Scam Dojo", vm.language),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // Stats card
        ChaukasCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Chaukas.Mint100,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.GpsFixed, null, tint = Chaukas.Mint700, modifier = Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = I18n.t(rank.name, vm.language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Chaukas.Ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    val progress = if (nextRank != null) {
                        ((correct - rank.at).toFloat() / (nextRank.at - rank.at).toFloat()).coerceIn(0f, 1f)
                    } else 1f
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Chaukas.Mint100)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(Chaukas.Mint700)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (nextRank != null) {
                            "${nextRank.at - correct} ${I18n.t("more to", vm.language)} ${I18n.t(nextRank.name, vm.language)}"
                        } else {
                            I18n.t("Top rank reached", vm.language)
                        },
                        fontSize = 11.sp,
                        color = Chaukas.Ink3,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Chaukas.Line, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$streak", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Chaukas.Ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(I18n.t("streak", vm.language), fontSize = 11.sp, color = Chaukas.Ink3)
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.Outlined.LocalFireDepartment, null, tint = Chaukas.Rose600, modifier = Modifier.size(13.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val acc = if (played > 0) (correct * 100 / played) else 0
                    Text("$acc%", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Chaukas.Ink)
                    Text(I18n.t("accuracy", vm.language), fontSize = 11.sp, color = Chaukas.Ink3)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$best", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Chaukas.Ink)
                    Text(I18n.t("best", vm.language), fontSize = 11.sp, color = Chaukas.Ink3)
                }
            }
        }

        // Phone message preview stage
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, Chaukas.Line),
            shadowElevation = 1.dp,
        ) {
            Column {
                // Phone top
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Chaukas.Surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Chaukas.Brand50,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = round.sender.firstOrNull()?.uppercase() ?: "·",
                                fontWeight = FontWeight.Bold,
                                color = Chaukas.Brand700,
                                fontSize = 15.sp,
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(round.sender, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Chaukas.Ink)
                        Text(I18n.t(round.channel, vm.language), fontSize = 11.sp, color = Chaukas.Ink3)
                    }
                }

                HorizontalDivider(color = Chaukas.Line, thickness = 1.dp)

                // Phone body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8F9FA))
                        .padding(16.dp),
                ) {
                    val bubbleBorder = when (right) {
                        true -> Chaukas.Mint700
                        false -> Chaukas.Rose700
                        null -> Color(0xFFE2E8F0)
                    }
                    val bubbleBg = when (right) {
                        true -> Chaukas.Mint50
                        false -> Chaukas.Rose50
                        null -> Color.White
                    }
                    Surface(
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        color = bubbleBg,
                        border = BorderStroke(1.dp, bubbleBorder),
                        shadowElevation = 0.5.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = roundContent.message,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Chaukas.Ink,
                            lineHeight = 21.sp,
                        )
                    }
                }
            }
        }

        // Decision / Reveal Area
        if (guess == null) {
            ChaukasCard {
                Text(
                    text = I18n.t("Scam, or the real thing?", vm.language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Chaukas.Ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = I18n.t("Read it like you would on your phone. Trust your gut, then see how you did.", vm.language),
                    style = MaterialTheme.typography.bodySmall,
                    color = Chaukas.Ink2,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { answer(true) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Chaukas.Rose600),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Shield, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(I18n.t("Scam", vm.language), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    Button(
                        onClick = { answer(false) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Chaukas.Mint700),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(I18n.t("Legit", vm.language), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        } else {
            val isRight = right == true
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = if (isRight) Chaukas.Mint50 else Chaukas.Rose50,
                border = BorderStroke(1.5.dp, if (isRight) Chaukas.Mint300 else Chaukas.Rose300),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isRight) Chaukas.Mint700 else Chaukas.Rose700)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = I18n.t(if (isRight) "Nice catch!" else "Not quite", vm.language),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }

                    Text(
                        text = I18n.t(if (round.isScam) "This was a scam" else "This one was legit", vm.language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Chaukas.Ink,
                    )

                    Text(
                        text = roundContent.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Chaukas.Ink,
                        lineHeight = 20.sp,
                    )

                    if (roundContent.tells.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            roundContent.tells.forEach { tell ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        Icons.Outlined.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp).padding(top = 2.dp),
                                        tint = if (isRight) Chaukas.Mint700 else Chaukas.Rose700,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = tell,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Chaukas.Ink2,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { nextRound() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Chaukas.Brand600),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = I18n.t("Next message", vm.language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Outlined.ArrowForward, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------ ENGINE SCREEN ------------------------------ */

@Composable
private fun EngineScreen(vm: ChaukasViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow(I18n.t("Under the hood", vm.language))
        Text(I18n.t("Engine", vm.language), style = MaterialTheme.typography.displaySmall)
        val (title, body, color) = when (val s = vm.modelState) {
            ModelState.Checking -> Triple(I18n.t("Checking…", vm.language), I18n.t("Asking AICore about Gemini Nano.", vm.language), Chaukas.Sky100)
            ModelState.Available -> Triple(I18n.t("Gemini Nano is ready", vm.language), I18n.t("Checks run on this phone, privately, even offline.", vm.language), Chaukas.Mint100)
            ModelState.Downloadable -> Triple(I18n.t("Ready to download", vm.language), I18n.t("Download Gemini Nano once. It is shared with other apps through AICore.", vm.language), Chaukas.Butter100)
            is ModelState.Downloading -> Triple(I18n.t("Downloading Gemini Nano…", vm.language) + (s.fraction?.let { " ${(it * 100).toInt()}%" } ?: ""), I18n.t("Smart mode uses the cloud with redaction until it finishes.", vm.language), Chaukas.Sky100)
            ModelState.Unavailable -> Triple(I18n.t("Not available on this phone", vm.language), I18n.t("Chaukas will use the cloud with personal details removed.", vm.language), Chaukas.Rose100)
        }
        ChaukasCard(color = color) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body, color = Chaukas.Ink2, modifier = Modifier.padding(top = 4.dp))
            (vm.modelState as? ModelState.Downloading)?.fraction?.let {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = Chaukas.Brand500)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.modelState == ModelState.Downloadable) Button(onClick = { vm.download() }) { Text(I18n.t("Download Gemini Nano", vm.language)) }
                OutlinedButton(onClick = { vm.refreshModel() }) { Text(I18n.t("Re-check", vm.language)) }
            }
        }
        ChaukasCard {
            Text(I18n.t("Privacy", vm.language), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(I18n.t("Remove personal details before using the cloud", vm.language))
                    Text(I18n.t("Numbers, emails, cards and codes are swapped for placeholders on your phone.", vm.language), color = Chaukas.Ink3, fontSize = 12.sp)
                }
                Switch(vm.redact, { vm.redact = it }, colors = SwitchDefaults.colors(checkedTrackColor = Chaukas.Brand500))
            }
        }
    }
}

@Composable
private fun Dial(score: Int, color: Color) {
    val p by animateFloatAsState(score / 100f, tween(900), label = "dial")
    Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            drawArc(color.copy(alpha = 0.15f), 135f, 270f, false, Offset(inset, inset), Size(size.width - stroke.width, size.height - stroke.width), style = stroke)
            drawArc(color, 135f, 270f * p, false, Offset(inset, inset), Size(size.width - stroke.width, size.height - stroke.width), style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(p * 100).toInt()}", style = MaterialTheme.typography.headlineSmall, color = color)
            Text("RISK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Chaukas.Ink3)
        }
    }
}
