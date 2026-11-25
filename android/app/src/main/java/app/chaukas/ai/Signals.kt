package app.chaukas.ai

/** Instant local pattern checks, mirroring web/src/ai/signals.ts. */
data class Signal(val id: String, val label: String, val weight: Int, val matches: List<String>)

object Signals {
    private data class Rule(val id: String, val label: String, val weight: Int, val re: Regex)

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val rules = listOf(
        Rule("urgency", "Pressure to act fast", 20, r("""\b(urgent(ly)?|immediately|right away|turant|jaldi|within \d+ ?(hours?|hrs?|minutes?|mins?)|final (notice|warning|reminder)|act now|last chance|expires? (today|soon|tonight)|tonight at \d|today only|avoid (suspension|disconnection|penalty|action))\b""")),
        Rule("threat", "Threat or penalty", 20, r("""\b(suspend(ed|ion)?|locked|deactivat(ed|ion)|arrest|warrant|lawsuit|legal action|penalt(y|ies)|fine[sd]?|terminated|blocked|band ho|kat (diya|jayega)|FIR|challan)\b""")),
        Rule("odd_payment", "Unusual way to pay", 26, r("""\b(gift ?cards?|bitcoin|btc|usdt|crypto|wire transfer|western union|moneygram|zelle|cash ?app)\b""")),
        Rule("credentials", "Asks for codes or passwords", 24, r("""\b(password|one[- ]time (code|password)|otp|verification code|security code|pin|ssn|social security)\b""")),
        Rule("prize", "Too good to be true", 22, r("""\b(you('ve| have)? won|winner|congratulations|badhai|inam|inaam|lottery|lucky draw|crore|lakhs?|claim (your|now)|reward (is )?(approved|available)|refund (is )?(approved|pending|available))\b""")),
        Rule("delivery", "Parcel or toll fee", 16, r("""\b(parcel|package|redeliver(y)?|customs fee|toll|unpaid (fee|balance|toll))\b""")),
        Rule("family", "\"New number\" family story", 20, r("""\b(hi (mum|mom|dad)|new number|lost my phone|broke my phone)\b""")),
        Rule("remote", "Remote access request", 26, r("""\b(anydesk|teamviewer|quicksupport|remote access|screen share|virus (detected|found)|\.apk\b|download (app|apk)|install (app|apk))\b""")),
        Rule("job_scam", "Easy-money job offer", 24, r("""\b(part[- ]time (job|role|work)|work from home|rating products|like (videos|posts) and earn|no experience (needed|required)|daily (income|payout)|task[- ]based (job|work))""")),
        Rule("off_platform", "Moves you to another app", 16, r("""\b(add me on (telegram|whatsapp|signal)|telegram @?\w+|contact (me|us) on telegram|t\.me/\w+)""")),
        Rule("india_kyc", "KYC, PAN or Aadhaar \"update\"", 26, r("""\b(kyc|e-?kyc|pan( card)?|aadhaa?r|sbi|yono|hdfc|icici|pnb|axis|bank account|khata)\b.{0,45}\b(update|updat(ed|ion)|expire[ds]?|block(ed)?|suspend(ed|ion)?|verify|link|deactivat(ed|ion)|hold)\b""")),
        Rule("upi_pin", "UPI PIN or collect request", 26, r("""\b(upi pin|collect request|enter (your )?pin|scan (this|the) qr|sent .{0,25} by mistake|galti se .{0,20}(bhej|paise)|wapas (kare|bhejo|karo)|approve request|cashback (approved|refund))\b""")),
        Rule("upi_qr", "Payment QR code", 54, r("""upi://pay\S*""")),
        Rule("digital_arrest", "\"Digital arrest\" or police threat", 26, r("""\b(digital arrest|cbi|narcotics|customs (department|officer)|trai|your (number|sim) will be (blocked|disconnected)|money laundering)\b""")),
        Rule("power_cut", "Power cut threat", 26, r("""\b(electricity|power|light|bijli|current|bill).{0,35}(disconnect(ed)?|cut|suspended|off|unpaid|pending|baki|officer|9\.\d{2} ?(pm|am))\b""")),
        Rule("call_officer", "Contact officer / mobile number", 22, r("""\b(contact|call|officer|dial|sampark (kare|kariye|karo)|helpline).{0,30}(\+?91|0)?[6-9]\d{9}\b""")),
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
