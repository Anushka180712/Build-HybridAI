package app.chaukas.ai

/** Instant local pattern checks, mirroring web/src/ai/signals.ts. */
data class Signal(val id: String, val label: String, val weight: Int, val matches: List<String>)

object Signals {
    private data class Rule(val id: String, val label: String, val weight: Int, val re: Regex)

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val rules = listOf(
        Rule("urgency", "Pressure to act fast", 18, r("""\b(urgent(ly)?|immediately|right away|within \d+ ?(hours?|hrs?|minutes?)|final (notice|warning)|act now|last chance|expires? (today|soon))\b""")),
        Rule("threat", "Threat or penalty", 16, r("""\b(suspend(ed)?|locked|deactivat(ed|ion)|arrest|warrant|legal action|penalt(y|ies)|fine[sd]?|blocked)\b""")),
        Rule("odd_payment", "Unusual way to pay", 26, r("""\b(gift ?cards?|bitcoin|btc|usdt|crypto|wire transfer|western union|moneygram|zelle|cash ?app)\b""")),
        Rule("credentials", "Asks for codes or passwords", 24, r("""\b(password|one[- ]time (code|password)|otp|verification code|security code|pin|ssn|social security)\b""")),
        Rule("prize", "Too good to be true", 18, r("""\b(you('ve| have)? won|winner|congratulations|claim (your|now)|prize|lottery|refund (is )?(pending|available))\b""")),
        Rule("delivery", "Parcel or toll fee", 14, r("""\b(parcel|package|redeliver(y)?|customs fee|toll|unpaid (fee|balance|toll))\b""")),
        Rule("family", "\"New number\" family story", 20, r("""\b(hi (mum|mom|dad)|new number|lost my phone|broke my phone)\b""")),
        Rule("remote", "Remote access request", 22, r("""\b(anydesk|teamviewer|remote access|virus (detected|found))\b""")),
        Rule("job_scam", "Easy-money job offer", 24, r("""\b(part[- ]time (job|role|work)|work from home|rating products|like (videos|posts) and earn|no experience (needed|required)|daily (income|payout)|task[- ]based (job|work))""")),
        Rule("off_platform", "Moves you to another app", 16, r("""\b(add me on (telegram|whatsapp|signal)|telegram @?\w+|contact (me|us) on telegram|t\.me/\w+)""")),
        Rule("india_kyc", "KYC, PAN or Aadhaar \"update\"", 22, r("""\b(kyc|e-?kyc|pan( card)?|aadhaa?r)\b.{0,40}\b(update|expired?|blocked?|suspend|verify|link)""")),
        Rule("upi_pin", "UPI PIN or collect request", 26, r("""\b(upi pin|collect request|enter (your )?pin|scan (this|the) qr|sent .{0,20} by mistake)\b""")),
        Rule("upi_qr", "Payment QR code", 54, r("""upi://pay\S*""")),
        Rule("digital_arrest", "\"Digital arrest\" or police threat", 26, r("""\b(digital arrest|cbi|narcotics|customs (department|officer)|trai|your (number|sim) will be (blocked|disconnected)|money laundering)\b""")),
        Rule("power_cut", "Power cut threat", 20, r("""\b(electricity|power|light) (will be |connection will be )?(disconnected|cut)\b""")),
    )

    private val shorteners = setOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly", "rb.gy", "t.ly")
    private val riskyTlds = setOf("top", "xyz", "icu", "click", "buzz", "cfd", "sbs", "live", "shop", "loan", "zip", "vip", "online", "site", "support")
    private val link = r("""\b((?:https?://|www\.)[^\s<>"')\]]+)""")

    fun compute(text: String): Pair<List<Signal>, Int> {
        val out = rules.mapNotNull { rule ->
            val m = rule.re.findAll(text).map { it.value.trim() }.distinct().toList()
            if (m.isEmpty()) null else Signal(rule.id, rule.label, rule.weight, m)
        }.toMutableList()
        val risky = link.findAll(text).map { it.value }.filter { url ->
            val host = url.removePrefix("https://").removePrefix("http://").substringBefore('/').lowercase()
            host in shorteners || host.substringAfterLast('.') in riskyTlds || host.contains("xn--") || url.startsWith("http://")
        }.toList()
        if (risky.isNotEmpty()) out += Signal("links", "Suspicious link", 26, risky)
        val raw = out.sumOf { it.weight }
        val score = (100 * (1 - Math.exp(-raw / 45.0))).toInt()
        return out to score
    }
}
