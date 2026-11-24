package app.chaukas.ai

import org.json.JSONArray
import org.json.JSONObject

enum class Verdict(val label: String) { SAFE("Looks safe"), SUSPICIOUS("Be careful"), SCAM("Likely scam") }

/** Mirrors the web app's InferenceSource values so Firestore records match. */
enum class Source(val wire: String, val label: String) {
    ON_DEVICE("on_device", "On this device"),
    IN_CLOUD("in_cloud", "Cloud"),
}

data class RedFlag(val label: String, val evidence: String, val why: String)

data class Analysis(
    val verdict: Verdict,
    val riskScore: Int,
    val category: String,
    val summary: String,
    val redFlags: List<RedFlag>,
    val nextSteps: List<String>,
    val safeReply: String,
)

data class AnalysisRun(
    val id: String,
    val createdAt: Long,
    val input: String,
    val analysis: Analysis,
    val source: Source,
    val latencyMs: Long,
    val redactions: Int,
)

object AnalysisParser {
    private fun JSONArray?.strings(n: Int): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.take(n)

    /** Lenient: tolerates code fences and repairs a score that contradicts the verdict. */
    fun parse(text: String): Analysis {
        val cleaned = text.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        require(start >= 0 && end > start) { "The model did not return JSON." }
        val o = JSONObject(cleaned.substring(start, end + 1))
        var score = o.optInt("riskScore", 50).coerceIn(0, 100)
        val verdict = when (o.optString("verdict").lowercase()) {
            "safe" -> Verdict.SAFE
            "suspicious" -> Verdict.SUSPICIOUS
            "scam" -> Verdict.SCAM
            else -> if (score >= 70) Verdict.SCAM else if (score > 30) Verdict.SUSPICIOUS else Verdict.SAFE
        }
        score = when (verdict) {
            Verdict.SAFE -> score.coerceAtMost(30)
            Verdict.SUSPICIOUS -> score.coerceIn(31, 69)
            Verdict.SCAM -> score.coerceAtLeast(70)
        }
        val flags = o.optJSONArray("redFlags")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { RedFlag(it.optString("label"), it.optString("evidence"), it.optString("why")) }
            }.filter { it.label.isNotBlank() }.take(5)
        } ?: emptyList()
        return Analysis(
            verdict = verdict,
            riskScore = score,
            category = o.optString("category", "other"),
            summary = o.optString("summary", "No summary was returned."),
            redFlags = flags,
            nextSteps = o.optJSONArray("nextSteps").strings(4),
            safeReply = o.optString("safeReply", ""),
        )
    }
}
