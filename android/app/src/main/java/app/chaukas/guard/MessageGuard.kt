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
        "com.oneplus.mms",
        "com.coloros.mms",
        "com.vivo.mms",
        "com.motorola.messaging",
        "com.sec.android.mms",
        "com.huawei.message",
        "com.transsion.mms",
        "com.nothing.chat",
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
    fun kind(pkg: String, extraSms: Set<String> = emptySet()): String? = when {
        pkg in SMS_APPS || pkg in extraSms -> "sms"
        pkg in CHAT_APPS || pkg.startsWith("com.whatsapp.") -> "chat"
        else -> null
    }

    /** Shorter than this, a message is counted as checked but cannot carry a scam. */
    private const val MIN_SCAM_LENGTH = 12

    /** Warn at "suspicious" or worse, the same line the app's verdict uses. */
    const val THRESHOLD = 45

    private val summary = Regex("""^\d+ (new )?messages?( from \d+ chats?)?$""", RegexOption.IGNORE_CASE)

    /** Returns true if text is just a count/summary like "3 new messages". */
    fun isSummary(body: String): Boolean = summary.matches(body.trim())

    /** True when this notification is a real message from a watched app, so it gets checked at all. */
    fun checkable(pkg: String, text: String, extraPackages: Set<String> = emptySet()): Boolean {
        if (kind(pkg, extraPackages) == null) return false
        val body = text.trim()
        // Even "hi" is counted, so the counts show every message really was looked at.
        return body.isNotEmpty() && !isSummary(body)
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

    /**
     * Determines whether a sender string represents an unknown / unsaved number or sender,
     * as opposed to a known contact name saved in the user's phone contacts.
     *
     * In Android SMS and WhatsApp notifications:
     * - Unsaved senders appear as raw phone numbers (e.g. "+91 98765 43210", "9876543210"),
     *   alphanumeric telecom headers (e.g. "BP-DHBVN", "VK-SBIINB", "AD-VIALRT"),
     *   numeric shortcodes (e.g. "567676"), or generic labels ("Unknown", empty).
     * - Saved contacts appear as person names (e.g. "Mom", "Rahul Gupta", "Priya").
     */
    fun isUnsavedSender(from: String, isContactSavedLookup: ((String) -> Boolean)? = null): Boolean {
        val trimmed = from.trim()
        if (trimmed.isEmpty()) return true

        // 1. If an external lookup (e.g. ContactsContract.PhoneLookup) confirms it is saved:
        if (isContactSavedLookup != null && runCatching { isContactSavedLookup(trimmed) }.getOrDefault(false)) {
            return false
        }

        // 2. Explicit unknown labels
        if (trimmed.equals("unknown", ignoreCase = true) ||
            trimmed.equals("unknown number", ignoreCase = true) ||
            trimmed.equals("unknown sender", ignoreCase = true)
        ) {
            return true
        }

        // 3. SMS telecom headers: 2 letters, hyphen, 3-8 alphanumeric characters (e.g. BP-DHBVN, VK-SBIINB)
        val smsHeader = Regex("""^[A-Za-z]{2}-[A-Za-z0-9]{3,8}$""", RegexOption.IGNORE_CASE)
        if (smsHeader.matches(trimmed)) return true

        // 4. SMS shortcodes: 3 to 8 digits
        val shortcode = Regex("""^\d{3,8}$""")
        if (shortcode.matches(trimmed)) return true

        val digits = trimmed.filter { it.isDigit() }
        val letters = trimmed.filter { it.isLetter() }

        // 5. Phone numbers: starts with + or has 7-16 digits with minimal/no alphabetic letters
        if (trimmed.startsWith("+") && digits.length in 7..16) return true
        if (digits.length in 7..16 && letters.isEmpty()) return true
        if (digits.length in 7..16 && letters.length <= 1) return true

        // Otherwise it is considered a saved contact name (e.g. "Mom", "Sunita Sharma", "Rohan")
        return false
    }
}
