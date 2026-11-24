/**
 * Decides whether an incoming message notification deserves a warning. Pure Kotlin with no
 * Android types, so it runs in JVM tests. Uses only the local scam signals: nothing is sent
 * anywhere and no model is called, so it is instant and works offline.
 */
package app.chaukas.guard

import app.chaukas.ai.Signals

data class GuardAlert(val score: Int, val reasons: List<String>, val text: String, val from: String)

object MessageGuard {
    /** SMS apps. The phone's own default SMS app is added at runtime. */
    val SMS_APPS = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.miui.mms",
        "com.truecaller",
    )

    /** WhatsApp and other chat apps. */
    val CHAT_APPS = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.google.android.gm",
    )

    val WATCHED = SMS_APPS + CHAT_APPS

    /** "sms", "chat", or null for apps that are not checked. */
    fun kind(pkg: String, extraSms: Set<String> = emptySet()): String? = when (pkg) {
        in SMS_APPS, in extraSms -> "sms"
        in CHAT_APPS -> "chat"
        else -> null
    }

    /** Shorter than this, a message is counted as checked but cannot carry a scam. */
    private const val MIN_SCAM_LENGTH = 12

    /** Warn at "suspicious" or worse, the same line the app's verdict uses. */
    const val THRESHOLD = 45

    private val summary = Regex("""^\d+ (new )?messages?( from \d+ chats?)?$""", RegexOption.IGNORE_CASE)

    /** True when this notification is a real message from a watched app, so it gets checked at all. */
    fun checkable(pkg: String, text: String, extraPackages: Set<String> = emptySet()): Boolean {
        if (pkg !in WATCHED && pkg !in extraPackages) return false
        val body = text.trim()
        // Even "hi" is counted, so the counts show every message really was looked at.
        return body.isNotEmpty() && !summary.matches(body)
    }

    fun assess(pkg: String, from: String, text: String, extraPackages: Set<String> = emptySet()): GuardAlert? {
        if (!checkable(pkg, text, extraPackages)) return null
        val body = text.trim()
        if (body.length < MIN_SCAM_LENGTH) return null
        val (signals, score) = Signals.compute(body)
        if (score < THRESHOLD) return null
        val reasons = signals.sortedByDescending { it.weight }.map { it.label }.take(2)
        return GuardAlert(score, reasons, body, from.trim())
    }

    /** Stable key so the same message (re-posted on every notification update) warns once. */
    fun key(pkg: String, text: String) = "$pkg:${text.trim().hashCode()}"
}
