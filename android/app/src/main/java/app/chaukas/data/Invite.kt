package app.chaukas.data

import app.chaukas.ai.AnalysisRun
import app.chaukas.ai.Redactor
import app.chaukas.ai.Verdict
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class QrFinding(
    val raw: String,
    val kind: String, // "upi", "url", "text"
    val headline: String,
    val details: List<Pair<String, String>>,
    val warnings: List<String>,
)

object InviteUtils {
    const val HANDLE_RE = "^[a-z0-9]{2,16}-[0-9]{4}$"

    private val VOWELS = mapOf(
        0x05 to "a", 0x06 to "a", 0x07 to "i", 0x08 to "i", 0x09 to "u",
        0x0a to "u", 0x0b to "ri", 0x0e to "e", 0x0f to "e", 0x10 to "ai",
        0x12 to "o", 0x13 to "o", 0x14 to "au",
    )
    private val SIGNS = mapOf(
        0x3e to "a", 0x3f to "i", 0x40 to "i", 0x41 to "u", 0x42 to "u",
        0x43 to "ri", 0x46 to "e", 0x47 to "e", 0x48 to "ai", 0x4a to "o",
        0x4b to "o", 0x4c to "au", 0x4d to "",
    )
    private val CONS = "k kh g gh n ch chh j jh ny t th d dh n t th d dh n n p ph b bh m y r r l l l v sh sh s h".split(" ")
    private val BLOCKS = listOf(0x0900, 0x0980, 0x0b80, 0x0c00)

    fun toLatin(name: String): String {
        val out = StringBuilder()
        val chars = name.toCharArray()
        for (i in chars.indices) {
            val cp = chars[i].code
            val base = BLOCKS.firstOrNull { cp in it until (it + 0x80) }
            if (base == null) {
                out.append(chars[i])
                continue
            }
            val off = cp - base
            if (off in 0x15..0x39 && (off - 0x15) < CONS.size) {
                out.append(CONS[off - 0x15])
                val next = if (i + 1 < chars.size) chars[i + 1].code else 0
                val nextOff = if (next in base until (base + 0x80)) next - base else -1
                val prev = if (i > 0) chars[i - 1].code else 0
                val joined = (prev - base) == 0x4d
                val endOfWord = nextOff == -1 || nextOff == 0x01 || nextOff == 0x02
                val drops = (base == 0x0900 || base == 0x0980) && endOfWord && !joined && out.length > 2
                if (nextOff !in SIGNS && !drops) out.append('a')
            } else if (VOWELS.containsKey(off)) {
                out.append(VOWELS[off])
            } else if (SIGNS.containsKey(off)) {
                out.append(SIGNS[off])
            } else if (off == 0x01 || off == 0x02) {
                out.append('n')
            }
        }
        return out.toString()
    }

    fun nameStem(name: String): String {
        val latin = toLatin(name)
            .lowercase()
            .filter { it in 'a'..'z' || it in '0'..'9' }
        return latin.take(16)
    }

    fun makeHandle(name: String, randomDigits: String? = null): String {
        val stem = nameStem(name).ifEmpty { "friend" }.let { if (it.length < 2) "friend" else it }
        val digits = randomDigits ?: "%04d".format((Math.random() * 10000).toInt())
        return "$stem-$digits"
    }

    fun handleFitsName(handle: String, name: String): Boolean {
        val stem = nameStem(name)
        return if (stem.length < 2) handle.startsWith("friend-") else handle.startsWith("$stem-")
    }

    fun normalizeHandle(input: String): String {
        val raw = input.trim().lowercase()
        val fromLink = Regex("""#/circle/@?([a-z0-9-]+)""").find(raw)?.groupValues?.get(1)
        val h = (fromLink ?: raw).removePrefix("@").replace(Regex("""\s+"""), "")
        return if (Regex(HANDLE_RE).matches(h)) h else ""
    }

    fun personUrl(handle: String, origin: String = "https://chaukas.app"): String =
        "$origin/#/circle/@$handle"

    fun inviteUrl(code: String, origin: String = "https://chaukas.app"): String =
        "$origin/#/circle/${URLEncoder.encode(code, "UTF-8")}"

    fun inviteText(code: String, circleName: String, origin: String = "https://chaukas.app"): String =
        "Join our family circle \"$circleName\" on Chaukas, so we can warn each other about scam messages. Tap to join: ${inviteUrl(code, origin)} (code $code)"

    fun defang(text: String): String =
        Regex("""\b(?:https?://|www\.)(\S+)""", RegexOption.IGNORE_CASE).replace(text) {
            it.groupValues[1].replace(".", "[.]")
        }

    fun alertForwardText(
        verdict: String,
        riskScore: Int,
        category: String,
        summary: String,
        excerpt: String,
        byName: String,
        origin: String = "https://chaukas.app",
    ): String {
        val vLabel = when (verdict.lowercase()) {
            "scam" -> "Likely scam"
            "suspicious" -> "Be careful"
            "safe" -> "Looks safe"
            else -> verdict.replaceFirstChar { it.uppercase() }
        }
        val lines = listOf(
            "⚠️ $vLabel: $category (risk $riskScore/100), spotted by $byName",
            if (excerpt.isNotBlank()) "\"${defang(excerpt)}\"" else "",
            defang(summary),
            if (verdict.lowercase() == "safe") "" else "Don't tap links, share OTPs or send money. If you already did, call 1930 (India) or your bank now.",
            "Checked with Chaukas: $origin",
        ).filter { it.isNotBlank() }
        return lines.joinToString("\n")
    }

    fun resultWhatsAppText(run: AnalysisRun, origin: String = "https://chaukas.app"): String {
        val a = run.analysis
        val quote = defang(Redactor.redact(run.input).text.replace(Regex("\\s+"), " ").take(160))
        val vLabel = when (a.verdict) {
            Verdict.SCAM -> "Likely scam"
            Verdict.SUSPICIOUS -> "Be careful"
            Verdict.SAFE -> "Looks safe"
        }
        val lines = listOf(
            "⚠️ $vLabel: ${categoryLabel(a.category)} (risk ${a.riskScore}/100)",
            if (quote.isNotBlank()) "\"$quote\"" else "",
            defang(Redactor.redact(a.summary).text),
            if (a.verdict == Verdict.SAFE) "" else "Don't tap links, share OTPs or send money. If you already did, call 1930 (India) or your bank now.",
            "Checked with Chaukas: $origin",
        ).filter { it.isNotBlank() }
        return lines.joinToString("\n")
    }

    fun inspectQr(raw: String): QrFinding {
        val text = raw.trim()
        if (text.startsWith("upi://", ignoreCase = true)) {
            val uri = runCatching { URI(text) }.getOrNull()
            val query = uri?.rawQuery ?: text.substringAfter("?", "")
            val params = query.split("&").mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to runCatching { URLDecoder.decode(parts[1], "UTF-8") }.getOrDefault(parts[1]) else null
            }.toMap()

            val pa = params["pa"] ?: ""
            val pn = params["pn"] ?: ""
            val am = params["am"]
            val tn = params["tn"] ?: ""
            val amount = if (am != null) "₹$am" else "an amount you type"

            val warnings = mutableListOf(
                "Scanning a QR code and entering your UPI PIN always SENDS money. You never need to scan or enter a PIN to receive money.",
            )
            if (am != null && am.toDoubleOrNull() ?: 0.0 > 0) {
                warnings.add("The amount ($amount) is already filled in.")
            }
            if (Regex("""refund|cashback|prize|reward|lottery|receive|kyc|verify""", RegexOption.IGNORE_CASE).containsMatchIn("$tn $pn")) {
                warnings.add("The note says \"${tn.ifBlank { pn }}\", which is how \"scan to receive\" scams disguise a payment.")
            }

            val details = mutableListOf<Pair<String, String>>()
            if (pn.isNotBlank()) details.add("Payee" to pn)
            if (pa.isNotBlank()) details.add("UPI ID" to pa)
            if (am != null) details.add("Amount" to amount)
            if (tn.isNotBlank()) details.add("Note" to tn)

            return QrFinding(
                raw = text,
                kind = "upi",
                headline = "UPI Payment to ${pn.ifBlank { pa.ifBlank { "merchant" } }} for $amount",
                details = details,
                warnings = warnings,
            )
        }

        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            val host = runCatching { URI(text).host ?: "" }.getOrDefault("")
            return QrFinding(
                raw = text,
                kind = "url",
                headline = "Opens a website: ${host.ifBlank { text }}",
                details = listOf("Address" to text),
                warnings = emptyList(),
            )
        }

        return QrFinding(
            raw = text,
            kind = "text",
            headline = "Contains text",
            details = listOf("Text" to text),
            warnings = emptyList(),
        )
    }
}

object RegionalVerdict {
    data class LangCopy(
        val scam: String,
        val suspicious: String,
        val safe: String,
        val dont: List<String>,
        val safeTip: String,
    )

    val WORDS: Map<String, LangCopy> = mapOf(
        "English" to LangCopy(
            scam = "Stop. This looks like a scam.",
            suspicious = "Be careful with this message.",
            safe = "This looks safe.",
            dont = listOf("Do not tap any link", "Do not share an OTP or UPI PIN", "Do not send money"),
            safeTip = "If you are still unsure, call the person on a number you already know.",
        ),
        "Hindi" to LangCopy(
            scam = "रुकिए! यह धोखा लगता है।",
            suspicious = "सावधान! इस मैसेज पर भरोसा न करें।",
            safe = "यह सुरक्षित लगता है।",
            dont = listOf("किसी लिंक पर टैप न करें", "OTP या UPI PIN किसी को न बताएँ", "पैसे न भेजें"),
            safeTip = "फिर भी शक हो, तो उस व्यक्ति को उसके पुराने नंबर पर कॉल करें।",
        ),
        "Bengali" to LangCopy(
            scam = "থামুন! এটা প্রতারণা মনে হচ্ছে।",
            suspicious = "সাবধান! এই মেসেজে ভরসা করবেন না।",
            safe = "এটা নিরাপদ মনে হচ্ছে।",
            dont = listOf("কোনো লিংকে ট্যাপ করবেন না", "OTP বা UPI PIN কাউকে বলবেন না", "টাকা পাঠাবেন না"),
            safeTip = "তবুও সন্দেহ হলে, মানুষটাকে তাঁর চেনা নম্বरे ফোন করুন।",
        ),
        "Tamil" to LangCopy(
            scam = "நில்லுங்கள்! இது மோசடி போலத் தெரிகிறது.",
            suspicious = "கவனம்! இந்த மெசேஜை நம்பாதீர்கள்.",
            safe = "இது பாதுகாப்பானதாகத் தெரிகிறது.",
            dont = listOf("எந்த லிங்கையும் தட்டாதீர்கள்", "OTP அல்லது UPI PIN-ஐ யாரிடமும் சொல்லாதீர்கள்", "பணம் அனுப்பாதீர்கள்"),
            safeTip = "இன்னும் சந்தேகம் இருந்தால், அவருக்குத் தெரிந்த பழைய எண்ணில் அழையுங்கள்.",
        ),
        "Telugu" to LangCopy(
            scam = "ఆగండి! ఇది మోసంలా ఉంది.",
            suspicious = "జాగ్రత్త! ఈ మెసేజ్‌ను నమ్మకండి.",
            safe = "ఇది సురక్షితంగా ఉంది.",
            dont = listOf("ఏ లింక్‌నూ నొక్కకండి", "OTP లేదా UPI PIN ఎవరికీ చెప్పకండి", "డబ్బు పంపకండి"),
            safeTip = "ఇంకా అనుమానం ఉంటే, ఆ వ్యక్తికి మీకు తెలిసిన పాత నంబర్‌కు కాల్ చేయండి.",
        ),
        "Marathi" to LangCopy(
            scam = "थांबा! ही फसवणूक वाटते.",
            suspicious = "सावधान! या मेसेजवर विश्वास ठेवू नका.",
            safe = "हे सुरक्षित वाटते.",
            dont = listOf("कोणत्याही लिंकवर टॅप करू नका", "OTP किंवा UPI PIN कोणालाही सांगू नका", "पैसे पाठवू नका"),
            safeTip = "तरीही शंका असल्यास, त्या व्यक्तीला त्यांच्या जुन्या नंबरवर फोन करा.",
        ),
    )

    fun forVerdict(language: String, verdict: Verdict): String {
        val copy = WORDS[language] ?: WORDS["English"]!!
        return when (verdict) {
            Verdict.SCAM -> copy.scam
            Verdict.SUSPICIOUS -> copy.suspicious
            Verdict.SAFE -> copy.safe
        }
    }
}
