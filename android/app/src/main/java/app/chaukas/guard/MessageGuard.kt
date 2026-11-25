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
        pkg == "sms" || pkg in SMS_APPS || pkg in extraSms -> "sms"
        pkg == "chat" || pkg in CHAT_APPS ||
            pkg.startsWith("com.whatsapp.") ||
            pkg.contains("whatsapp", ignoreCase = true) ||
            pkg.contains("telegram", ignoreCase = true) -> "chat"
        else -> null
    }

    /** Shorter than this, a message is counted as checked but cannot carry a scam. */
    private const val MIN_SCAM_LENGTH = 10

    /** Warn at "suspicious" or worse (score >= 25), so any real fraud signal raises an alert. */
    const val THRESHOLD = 25

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
     *   numeric shortcodes (e.g. "567676"), WhatsApp push names ("~Name"),
     *   parenthesized numbers ("Name (+91 98765 43210)"), or generic labels ("Unknown", empty).
     * - Saved contacts appear as person names (e.g. "Mom", "Rahul Gupta", "Priya").
     */
    fun isUnsavedSender(from: String, isContactSavedLookup: ((String) -> Boolean)? = null): Boolean {
        val trimmed = from.trim()
        if (trimmed.isEmpty()) return true

        // 1. WhatsApp prefix ~ denotes an unsaved push name (e.g. "~Anushka")
        // But if ~Name stripped matches a saved contact in phonebook, it is recognized as saved.
        if (trimmed.startsWith("~")) {
            val withoutTilde = trimmed.removePrefix("~").trim()
            if (isContactSavedLookup != null && withoutTilde.isNotBlank()) {
                val saved = runCatching { isContactSavedLookup(withoutTilde) }.getOrDefault(false)
                if (saved) return false
            }
            return true
        }

        // 2. Embedded phone numbers in parentheses or string (e.g. "Anushka (+91 98765 43210)")
        val phonePattern = Regex("""(\+?\d[\d -]{7,15}\d)""").find(trimmed)
        if (phonePattern != null) {
            val phoneDigits = phonePattern.value.filter { it.isDigit() }
            if (phoneDigits.length in 7..16) {
                if (isContactSavedLookup != null) {
                    val saved = runCatching { isContactSavedLookup(trimmed) }.getOrDefault(false) ||
                                runCatching { isContactSavedLookup(phoneDigits) }.getOrDefault(false) ||
                                (phoneDigits.length >= 10 && runCatching { isContactSavedLookup(phoneDigits.takeLast(10)) }.getOrDefault(false))
                    if (saved) return false
                }
                return true
            }
        }

        // 3. If contact lookup confirms it is saved in phone contacts:
        if (isContactSavedLookup != null) {
            val isSaved = runCatching { isContactSavedLookup(trimmed) }.getOrDefault(false)
            if (isSaved) return false
        }

        // 4. Explicit unknown labels
        if (trimmed.equals("unknown", ignoreCase = true) ||
            trimmed.equals("unknown number", ignoreCase = true) ||
            trimmed.equals("unknown sender", ignoreCase = true)
        ) {
            return true
        }

        // 5. SMS telecom headers: 2 letters, hyphen, 3-8 alphanumeric characters (e.g. BP-DHBVN, VK-SBIINB)
        val smsHeader = Regex("""^[A-Za-z]{2}-[A-Za-z0-9]{3,8}$""", RegexOption.IGNORE_CASE)
        if (smsHeader.matches(trimmed)) return true

        // 6. SMS shortcodes: 3 to 8 digits
        val shortcode = Regex("""^\d{3,8}$""")
        if (shortcode.matches(trimmed)) return true

        val digits = trimmed.filter { it.isDigit() }
        val letters = trimmed.filter { it.isLetter() }

        // 7. Phone numbers: starts with + or has 7-16 digits with minimal/no alphabetic letters
        val isNumericPattern = (trimmed.startsWith("+") && digits.length in 7..16) ||
                (digits.length in 7..16 && letters.isEmpty()) ||
                (digits.length in 7..16 && letters.length <= 1)
        if (isNumericPattern) {
            if (isContactSavedLookup != null) {
                val saved = runCatching { isContactSavedLookup(trimmed) }.getOrDefault(false) ||
                            runCatching { isContactSavedLookup(digits) }.getOrDefault(false) ||
                            (digits.length >= 10 && runCatching { isContactSavedLookup(digits.takeLast(10)) }.getOrDefault(false))
                if (saved) return false
            }
            return true
        }

        // Otherwise it is considered a saved contact name (e.g. "Mom", "Sunita Sharma", "Rohan")
        return false
    }
}
