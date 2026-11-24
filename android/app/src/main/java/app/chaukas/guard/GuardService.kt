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
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val text = latestMessage(n) ?: (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
        val from = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
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
            Guard.warn(this, alert, key.hashCode())
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

    /** WhatsApp and Messages use MessagingStyle; the newest line is the one that just arrived. */
    private fun latestMessage(n: Notification): String? {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) ?: return null
        return style.messages.lastOrNull()?.text?.toString()
    }

    private companion object { const val TAG = "ChaukasGuard" }
}

/** Settings, permission checks and the warning notification for the auto-check. */
object Guard {
    private const val PREFS = "chaukas.guard"
    private const val CHANNEL = "scam_warnings"
    const val ACTION_CHECK = "app.chaukas.CHECK"

    fun isOn(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)

    fun setOn(ctx: Context, on: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()

    private const val SAFE_CHANNEL = "checked_safe"
    private const val SAFE_ID = 1930

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

    fun warn(ctx: Context, alert: GuardAlert, id: Int) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Scam warnings", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A heads-up when a new message looks like a scam"
        })
        val open = Intent(ctx, MainActivity::class.java)
            .setAction(ACTION_CHECK)
            .putExtra(Intent.EXTRA_TEXT, alert.text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = if (alert.score >= 70) "This message looks like a scam" else "Be careful with this message"
        val who = if (alert.from.isNotBlank()) "From ${alert.from}. " else ""
        val body = "$who${alert.reasons.joinToString(" · ")}. Don't tap links or share codes. Tap for a full check."
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_chaukas)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(0xFFC94B36.toInt())
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, notification) }
    }
}
