package app.chaukas.ai

/**
 * Local PII redaction, a Kotlin port of web/src/ai/redact.ts so both apps
 * apply exactly the same privacy rules before anything leaves the device.
 */
data class Redaction(val text: String, val map: Map<String, String>) {
    val count: Int get() = map.size
}

object Redactor {
    private val urlQuery = Regex("""\b(https?://[^\s?#]+)[?#]\S*""", RegexOption.IGNORE_CASE)
    private val email = Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)
    private val card = Regex("""\b(?:\d[ -]?){13,19}\b""")
    private val ssn = Regex("""\b\d{3}-\d{2}-\d{4}\b""")
    private val code = Regex("""\b(?:code|otp|pin|passcode|verification)\b[^\d\n]{0,20}(\d{4,8})\b""", RegexOption.IGNORE_CASE)
    private val upi = Regex("""\b[\w.-]{2,}@[a-z][a-z0-9]{1,15}\b(?![.@\w])""", RegexOption.IGNORE_CASE)
    private val pan = Regex("""\b[A-Z]{5}\d{4}[A-Z]\b""")
    private val aadhaar = Regex("""\b[2-9]\d{3}[\s-]?\d{4}[\s-]?\d{4}\b""")
    private val codeBefore = Regex("""\b(\d{4,8})\b(?=[\s.,:-]*(?:is|as)\s+(?:your|the)\s+(?:otp|code|one[- ]?time|verification|pin|passcode))""", RegexOption.IGNORE_CASE)
    private val indianMobile = Regex("""(?:\+91[\s-]?|\b0|\b)[6-9]\d{4}[\s-]?\d{5}\b""")
    private val phone = Regex("""(?:\+?\d{1,3}[\s.-]?)?(?:\(\d{2,4}\)|\d{2,4})[\s.-]?\d{3,4}[\s.-]?\d{3,4}\b""")
    private val account = Regex("""\b(?:acct|account|a/c)\.?\s*(?:no\.?|number|#)?\s*[:#]?\s*(\d{6,})\b""", RegexOption.IGNORE_CASE)

    private fun luhn(raw: String): Boolean {
        val d = raw.filter { it.isDigit() }
        if (d.length !in 13..19) return false
        var sum = 0
        var dbl = false
        for (i in d.indices.reversed()) {
            var n = d[i] - '0'
            if (dbl) { n *= 2; if (n > 9) n -= 9 }
            sum += n
            dbl = !dbl
        }
        return sum % 10 == 0
    }

    fun redact(input: String, privateTerms: List<String> = emptyList()): Redaction {
        val map = linkedMapOf<String, String>()
        val reverse = hashMapOf<String, String>()
        val counters = hashMapOf<String, Int>()
        fun tag(kind: String, value: String): String = reverse.getOrPut(value) {
            val n = (counters[kind] ?: 0) + 1
            counters[kind] = n
            "[${kind}_$n]".also { map[it] = value }
        }

        var text = urlQuery.replace(input) { "${it.groupValues[1]}?[QUERY_REMOVED]" }
        privateTerms.map { it.trim() }.filter { it.length >= 2 }.forEach { term ->
            text = Regex("""\b${Regex.escape(term)}\b""", RegexOption.IGNORE_CASE).replace(text) { tag("NAME", it.value) }
        }
        text = email.replace(text) { tag("EMAIL", it.value) }
        text = card.replace(text) { if (luhn(it.value)) tag("CARD", it.value.trim()) else it.value }
        text = ssn.replace(text) { tag("SSN", it.value) }
        text = upi.replace(text) { tag("UPI", it.value) }
        text = pan.replace(text) { tag("PAN", it.value) }
        text = aadhaar.replace(text) { tag("AADHAAR", it.value) }
        text = codeBefore.replace(text) { tag("CODE", it.value) }
        text = code.replace(text) { m -> m.value.replace(m.groupValues[1], tag("CODE", m.groupValues[1])) }
        text = indianMobile.replace(text) { tag("PHONE", it.value) }
        text = phone.replace(text) { m ->
            if (m.value.startsWith("[") || m.value.count { it.isDigit() } < 9) m.value
            else m.value.takeWhile { it.isWhitespace() } + tag("PHONE", m.value.trim())
        }
        text = account.replace(text) { m -> m.value.replace(m.groupValues[1], tag("ACCOUNT", m.groupValues[1])) }
        return Redaction(text, map)
    }

    fun restore(text: String, map: Map<String, String>): String =
        Regex("""\[[A-Z]+_\d+]""").replace(text) { map[it.value] ?: it.value }
}
