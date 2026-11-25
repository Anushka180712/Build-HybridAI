/**
 * Optional auto-check. A NotificationListenerService that reads the text of new SMS, WhatsApp
 * and other chat notifications on the phone, runs MessageGuard locally and posts a quiet
 * warning when a message looks like a scam. Tapping the warning opens the full hybrid check.
 * It is off until the user turns it on in the app and grants notification access in Settings.
 */
package app.chaukas.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.chaukas.MainActivity
import app.chaukas.R
import app.chaukas.ai.Analysis
import app.chaukas.ai.AnalysisRun
import app.chaukas.ai.RedFlag
import app.chaukas.ai.Source
import app.chaukas.ai.Verdict
import app.chaukas.data.LocalHistory
import app.chaukas.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GuardService : NotificationListenerService() {
    private val seen = LinkedHashSet<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Proof that this service really receives notifications: the app's own self-test.
        if (sbn.packageName == packageName && sbn.notification.extras.getBoolean(Guard.SELF_TEST)) {
            Guard.selfTestReceived(this)
            cancelNotification(sbn.key)
            return
        }
        // One bad notification must never stop the checker.
        try {
            check(sbn)
        } catch (e: Exception) {
            Log.e(TAG, "could not check a notification", e)
        }
    }

    private fun check(sbn: StatusBarNotification) {
        if (!Guard.isOn(this) || sbn.packageName == packageName) return
        val n = sbn.notification
        val text = extractMessageText(n) ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 && MessageGuard.isSummary(text)) return
        val from = extractSender(n)
        val watched = setOfNotNull(Telephony.Sms.getDefaultSmsPackage(this))
        val kind = MessageGuard.kind(sbn.packageName, watched) ?: return
        if (!Guard.watches(this, kind)) return
        if (!MessageGuard.checkable(sbn.packageName, text, watched)) return
        // Apps re-post the same message on every update: check each one once.
        val key = MessageGuard.key(sbn.packageName, text)
        if (!seen.add(key)) return
        if (seen.size > 200) seen.remove(seen.first())
        val alert = MessageGuard.assess(sbn.packageName, from, text, watched)
        Guard.record(this, appName(sbn.packageName), kind, alert)
        if (alert != null) {
            Log.i(TAG, "warned: score=${alert.score}")
            val isUnsaved = MessageGuard.isUnsavedSender(from) { Guard.isContactSaved(this, it) }
            Guard.warn(this, alert, key.hashCode(), isKnownContact = !isUnsaved)
            if (alert.score >= Guard.SCAM_AT && isUnsaved) {
                Guard.onScamDetected(this, appName(sbn.packageName), kind, alert)
            }
        } else if (Guard.showSafe(this)) {
            Guard.safeNote(this, appName(sbn.packageName))
        }
    }

    // Some phones (Xiaomi, Oppo, Vivo, Samsung with battery saving) stop the listener in the
    // background. Ask Android to connect it again whenever that happens.
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "listener disconnected, asking to rebind")
        Guard.setConnected(this, false)
        NotificationListenerService.requestRebind(ComponentName(this, GuardService::class.java))
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "listener connected")
        Guard.setConnected(this, true)
    }

    private fun appName(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))

    /** Robustly extracts message text from MessagingStyle, BigText, Inbox lines, or standard text. */
    private fun extractMessageText(n: Notification): String? {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val styleMsg = style?.messages?.lastOrNull { !it.text.isNullOrBlank() }?.text?.toString()?.trim()
        if (!styleMsg.isNullOrBlank() && !MessageGuard.isSummary(styleMsg)) return styleMsg

        val extras = n.extras ?: return null

        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        if (!big.isNullOrBlank() && !MessageGuard.isSummary(big)) return big

        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val lastLine = lines?.lastOrNull { !it.isNullOrBlank() }?.toString()?.trim()
        if (!lastLine.isNullOrBlank() && !MessageGuard.isSummary(lastLine)) return lastLine

        val txt = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        if (!txt.isNullOrBlank() && !MessageGuard.isSummary(txt)) return txt

        return if (!big.isNullOrBlank()) big else txt
    }

    private fun extractSender(n: Notification): String {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val person = style?.messages?.lastOrNull { !it.text.isNullOrBlank() }?.person?.name?.toString()
        if (!person.isNullOrBlank()) return person
        val extras = n.extras ?: return ""
        return extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    }

    private companion object { const val TAG = "ChaukasGuard" }
}

/** Settings, permission checks and the warning notification for the auto-check. */
object Guard {
    private const val PREFS = "chaukas.guard"
    private const val CHANNEL = "scam_warnings"
    const val ACTION_CHECK = "app.chaukas.CHECK"

    fun isOn(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true)

    fun setOn(ctx: Context, on: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()

    private const val SAFE_CHANNEL = "checked_safe"
    private const val SAFE_ID = 1930

    fun initChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val scamChannel = NotificationChannel(CHANNEL, "Scam warnings", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A heads-up when a new message looks like a scam"
            enableVibration(true)
            enableLights(true)
        }
        val safeChannel = NotificationChannel(SAFE_CHANNEL, "Checked, looks fine", NotificationManager.IMPORTANCE_MIN).apply {
            description = "A silent note when a new message was checked and showed no scam signs"
        }
        val testChannel = NotificationChannel(TEST_CHANNEL, "Self-test", NotificationManager.IMPORTANCE_LOW).apply {
            description = "A test message to check that automatic warnings work"
        }
        nm.createNotificationChannels(listOf(scamChannel, safeChannel, testChannel))
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Optional quiet note for messages that looked fine, so it is visible that checking works. */
    fun showSafe(ctx: Context) = prefs(ctx).getBoolean("safe", false)

    fun setShowSafe(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("safe", on).apply()


    /** Warnings are listed for 3 days, then dropped from the list (never from the counts). */
    const val KEEP_MS = 3L * 24 * 60 * 60 * 1000

    /** Risk at which a warning is called a scam rather than "be careful". */
    const val SCAM_AT = 65

    /** One warning: when, which app, its score and the top reasons. No message text is kept. */
    data class Entry(val at: Long, val app: String, val score: Int?, val reasons: String = "") {
        val isScam get() = (score ?: 0) >= SCAM_AT
    }

    /** Counts for today and for all time. They only ever go up: deleting a message changes nothing. */
    data class Counts(val today: Int, val todaySms: Int, val todayChat: Int, val todayWarned: Int, val total: Int, val totalWarned: Int, val lastChatAt: Long)

    private fun today() = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())

    /** Which kinds of app to check. Both are on by default once auto-check is on. */
    fun watches(ctx: Context, kind: String) = prefs(ctx).getBoolean("watch_$kind", true)
    fun setWatches(ctx: Context, kind: String, on: Boolean) = prefs(ctx).edit().putBoolean("watch_$kind", on).apply()

    /**
     * Counts every message checked (today, per kind, and all time) and keeps the warnings of the
     * last 3 days with their reasons. Messages that looked fine are only counted. All on this phone.
     */
    fun record(ctx: Context, app: String, kind: String, alert: GuardAlert?) {
        val p = prefs(ctx)
        val day = today()
        val fresh = p.getString("day", "") != day
        fun todayInt(k: String) = if (fresh) 0 else p.getInt(k, 0)
        val warn = if (alert != null) 1 else 0
        val e = p.edit()
            .putString("day", day)
            .putInt("checked_$kind", todayInt("checked_$kind") + 1)
            .putInt("checked_${if (kind == "sms") "chat" else "sms"}", todayInt("checked_${if (kind == "sms") "chat" else "sms"}"))
            .putInt("warned", todayInt("warned") + warn)
            .putInt("total", p.getInt("total", 0) + 1)
            .putInt("total_warned", p.getInt("total_warned", 0) + warn)
        if (kind == "chat") e.putLong("last_chat", System.currentTimeMillis())
        if (alert != null) {
            val clean = { x: String -> x.replace('\t', ' ').replace('\n', ' ') }
            val line = "${System.currentTimeMillis()}\t${clean(app)}\t${alert.score}\t${clean(alert.reasons.joinToString(", "))}"
            val lines = (listOf(line) + p.getString("log", "").orEmpty().split('\n').filter { it.isNotBlank() }).take(30)
            e.putString("log", lines.joinToString("\n"))
        }
        // "checked" changes last, so the screen refreshes once everything above is saved.
        e.putInt("checked", todayInt("checked") + 1)
        e.apply()
    }

    fun counts(ctx: Context): Counts {
        val p = prefs(ctx)
        val isToday = p.getString("day", "") == today()
        fun t(k: String) = if (isToday) p.getInt(k, 0) else 0
        return Counts(t("checked"), t("checked_sms"), t("checked_chat"), t("warned"), p.getInt("total", 0), p.getInt("total_warned", 0), p.getLong("last_chat", 0))
    }

    fun recent(ctx: Context): List<Entry> = prefs(ctx).getString("log", "").orEmpty().split('\n').mapNotNull { l ->
        val parts = l.split('\t')
        if (parts.size < 3) null else Entry(parts[0].toLongOrNull() ?: return@mapNotNull null, parts[1], parts[2].toIntOrNull(), parts.getOrElse(3) { "" })
    }.filter { it.score != null && System.currentTimeMillis() - it.at < KEEP_MS }

    /** Clears the list of warnings. The counts stay. */
    fun clearLog(ctx: Context) = prefs(ctx).edit().remove("log").apply()

    fun safeNote(ctx: Context, app: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(SAFE_CHANNEL, "Checked, looks fine", NotificationManager.IMPORTANCE_MIN).apply {
            description = "A silent note when a new message was checked and showed no scam signs"
        })
        val open = PendingIntent.getActivity(ctx, SAFE_ID, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, SAFE_CHANNEL)
            .setSmallIcon(R.drawable.ic_chaukas)
            .setContentTitle("Chaukas checked a $app message")
            .setContentText("No scam signs found. Still, never share an OTP or PIN.")
            .setColor(0xFF2F8F6B.toInt())
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(60 * 60 * 1000L)
            .build()
        // One note, replaced each time, so it never piles up.
        runCatching { NotificationManagerCompat.from(ctx).notify(SAFE_ID, n) }
    }

    fun hasAccess(ctx: Context) = ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)

    /** Opens the system page where the user grants notification access to Chaukas. */
    fun accessSettings(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(ctx, GuardService::class.java).flattenToString())
        } else {
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }

    const val SELF_TEST = "chaukas_self_test"
    private const val TEST_CHANNEL = "self_test"

    fun setConnected(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("connected", on).putLong("connected_at", System.currentTimeMillis()).apply()
    fun isConnected(ctx: Context) = prefs(ctx).getBoolean("connected", false)

    fun selfTestReceived(ctx: Context) = prefs(ctx).edit().putLong("self_test_ok", System.currentTimeMillis()).apply()
    fun lastSelfTest(ctx: Context) = prefs(ctx).getLong("self_test_ok", 0)

    /**
     * Posts a quiet test notification from Chaukas itself. If the checker receives it, it records
     * the time and removes it, so the app can say "working". If not, the phone is blocking it.
     */
    fun sendSelfTest(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return false
        nm.createNotificationChannel(NotificationChannel(TEST_CHANNEL, "Self-test", NotificationManager.IMPORTANCE_LOW).apply {
            description = "A test message to check that automatic warnings work"
        })
        val n = NotificationCompat.Builder(ctx, TEST_CHANNEL)
            .setSmallIcon(R.drawable.ic_chaukas)
            .setContentTitle("Chaukas self-test")
            .setContentText("Checking that Chaukas can see new messages…")
            .setSilent(true)
            .setTimeoutAfter(15_000)
            .setExtras(android.os.Bundle().apply { putBoolean(SELF_TEST, true) })
            .build()
        nm.notify(4242, n)
        return true
    }

    /** Asks the system to reconnect the checker, for phones that stopped it. */
    fun reconnect(ctx: Context) {
        NotificationListenerService.requestRebind(ComponentName(ctx, GuardService::class.java))
    }

    /** Opens the place to grant notification access, on any phone (some lack the detail page). */
    fun openAccess(ctx: Context) {
        val tries = listOf(
            accessSettings(ctx),
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")),
        )
        for (i in tries) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
                /* try the next one */
            }
        }
    }

    /** The app's own settings page, where Xiaomi and others keep Autostart and Battery saver. */
    fun openAppSettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { /* nothing to open */ }
    }

    const val TEST_SCAM_SMS = "URGENT: Your electricity power will be disconnected tonight at 9:30 PM due to unpaid bill. Immediately call officer at 9876543210 or update bill: http://bit.ly/dhbvn-pay"
    const val TEST_SCAM_CHAT = "SBI Alert: Your KYC has expired and your account is blocked. Update immediately by entering your OTP at http://sbi-kyc.top/verify or contact us on Telegram."

    /**
     * Simulates receiving a high-risk scam message to test the notification warning and scanner flow.
     */
    fun simulateTestAlert(ctx: Context, kind: String = "sms"): Boolean {
        setOn(ctx, true)
        val text = if (kind == "sms") TEST_SCAM_SMS else TEST_SCAM_CHAT
        val app = if (kind == "sms") "Messages" else "WhatsApp"
        val from = if (kind == "sms") "BP-DHBVN" else "+91 98765 43210"
        val alert = MessageGuard.assess(
            if (kind == "sms") "com.google.android.apps.messaging" else "com.whatsapp",
            from,
            text,
        ) ?: GuardAlert(
            score = 86,
            reasons = listOf("Threat of power cut", "Suspicious link"),
            text = text,
            from = from,
        )
        record(ctx, app, kind, alert)
        val isUnsaved = MessageGuard.isUnsavedSender(from) { isContactSaved(ctx, it) }
        warn(ctx, alert, (System.currentTimeMillis() % 10000).toInt() + 100, isKnownContact = !isUnsaved)
        if (alert.score >= SCAM_AT && isUnsaved) {
            onScamDetected(ctx, app, kind, alert)
        }
        return true
    }

    fun warn(ctx: Context, alert: GuardAlert, id: Int, isKnownContact: Boolean = false) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Scam warnings", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A heads-up when a new message looks like a scam"
            enableVibration(true)
            enableLights(true)
        })
        val open = Intent(ctx, MainActivity::class.java)
            .setAction(ACTION_CHECK)
            .putExtra(Intent.EXTRA_TEXT, alert.text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = if (isKnownContact) {
            "⚠️ Red flags in message from ${alert.from}"
        } else if (alert.score >= 70) {
            "This message looks like a scam"
        } else {
            "Be careful with this message"
        }
        val body = if (isKnownContact) {
            "Why it's flagged: ${alert.reasons.joinToString(" · ")}. Their phone or account may be compromised. Don't send money or tap links."
        } else {
            val who = if (alert.from.isNotBlank()) "From ${alert.from}. " else ""
            "$who${alert.reasons.joinToString(" · ")}. Don't tap links or share codes. Tap for a full check."
        }
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_chaukas)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(0xFFC94B36.toInt())
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, notification) }
    }

    /**
     * Checks if the sender string matches a saved contact in the phone's contacts database,
     * if READ_CONTACTS permission has been granted.
     */
    fun isContactSaved(ctx: Context, from: String): Boolean {
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val clean = from.trim()
        if (clean.isBlank()) return false
        return runCatching {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(clean),
            )
            ctx.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }.getOrDefault(false)
    }

    /**
     * Automatically records a high-risk scam (score >= 65) from an unsaved sender to on-device history
     * and shares it to Family Circle groups so family members are alerted.
     */
    fun onScamDetected(ctx: Context, app: String, kind: String, alert: GuardAlert) {
        val category = when {
            alert.reasons.any { it.contains("electricity", ignoreCase = true) || it.contains("bill", ignoreCase = true) || it.contains("power", ignoreCase = true) } -> "delivery_fee"
            alert.reasons.any { it.contains("kyc", ignoreCase = true) || it.contains("bank", ignoreCase = true) || it.contains("account", ignoreCase = true) || it.contains("otp", ignoreCase = true) } -> "bank_or_payment"
            alert.reasons.any { it.contains("lottery", ignoreCase = true) || it.contains("prize", ignoreCase = true) || it.contains("win", ignoreCase = true) } -> "prize_lottery"
            alert.reasons.any { it.contains("tax", ignoreCase = true) || it.contains("police", ignoreCase = true) || it.contains("court", ignoreCase = true) } -> "government_tax"
            alert.reasons.any { it.contains("job", ignoreCase = true) || it.contains("salary", ignoreCase = true) } -> "job_offer"
            else -> "phishing"
        }

        val senderDesc = if (alert.from.isNotBlank()) "from ${alert.from}" else "via $app"
        val summary = "Auto-detected scam $senderDesc: ${alert.reasons.joinToString(", ")}. Risk score: ${alert.score}%."

        val redFlags = alert.reasons.map { reason ->
            RedFlag(
                label = reason,
                evidence = if (alert.from.isNotBlank()) alert.from else app,
                why = "Auto-detected from $app notification from unknown number: $reason",
            )
        }

        val nextSteps = listOf(
            "Do not tap any links or call numbers sent in this message.",
            "Never share OTP, passwords, UPI PIN, or bank card details.",
            "Block and report the sender in $app.",
            "Report cyber fraud at cybercrime.gov.in or helpline 1930 if funds were sent.",
        )

        val run = AnalysisRun(
            id = java.util.UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            input = alert.text,
            analysis = Analysis(
                verdict = Verdict.SCAM,
                riskScore = alert.score,
                category = category,
                summary = summary,
                redFlags = redFlags,
                nextSteps = nextSteps,
                safeReply = "I will not share any sensitive information or click any links.",
            ),
            source = Source.ON_DEVICE,
            latencyMs = 12L,
            redactions = 0,
        )

        // 1. Record to on-device LocalHistory
        runCatching { LocalHistory.record(ctx, run) }

        // 2. Sync to Firebase user checks
        runCatching { Repository.saveCheck(run) }

        // 3. Auto-share to Family Circle group if circle sharing is enabled
        val autoShare = ctx.getSharedPreferences("chaukas.settings", Context.MODE_PRIVATE)
            .getBoolean("autoShareCircle", true)
        if (autoShare) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    Repository.autoShareToCircles(run, ctx)
                }
            }
        }
    }
}
