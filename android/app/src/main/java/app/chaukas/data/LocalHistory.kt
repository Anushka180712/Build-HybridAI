/**
 * On-device history, matching the web app's history store: the newest 150 checks, and
 * running totals that never go down when a check is deleted or the list is cleared.
 */
package app.chaukas.data

import android.content.Context
import app.chaukas.ai.Analysis
import app.chaukas.ai.AnalysisRun
import app.chaukas.ai.RedFlag
import app.chaukas.ai.Source
import app.chaukas.ai.Verdict
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class HistoryCounts(val checked: Int, val scams: Int, val since: Long)

data class HistoryState(val runs: List<AnalysisRun>, val counts: HistoryCounts)

fun countsAsScam(run: AnalysisRun) = run.analysis.verdict != Verdict.SAFE && run.analysis.riskScore >= 65

/** Same save rules as web/src/data/history.ts saveRun. */
fun applySave(state: HistoryState, run: AnalysisRun, now: Long): HistoryState {
    var counts = state.counts
    if (counts.since == 0L) {
        counts = HistoryCounts(state.runs.size, state.runs.count(::countsAsScam), now)
    }
    if (state.runs.none { it.id == run.id }) {
        counts = counts.copy(checked = counts.checked + 1, scams = counts.scams + if (countsAsScam(run)) 1 else 0)
    }
    val runs = (listOf(run) + state.runs.filter { it.id != run.id }).take(150)
    return HistoryState(runs, counts)
}

fun applyDelete(state: HistoryState, id: String) = state.copy(runs = state.runs.filter { it.id != id })

/** Clears the list on this device. Totals stay, matching the web clear. */
fun applyClear(state: HistoryState) = state.copy(runs = emptyList())

fun historyStats(state: HistoryState): HistoryStats {
    val total = maxOf(state.counts.checked, state.runs.size)
    val scams = maxOf(state.counts.scams, state.runs.count(::countsAsScam))
    val local = state.runs.count { it.source == Source.ON_DEVICE }
    val localPct = if (state.runs.isEmpty()) 0 else kotlin.math.round(local * 100.0 / state.runs.size).toInt()
    val hidden = state.runs.sumOf { it.redactions }
    return HistoryStats(total, scams, localPct, hidden)
}

data class HistoryStats(val total: Int, val scams: Int, val localPct: Int, val hidden: Int)

fun timeAgo(ts: Long, now: Long = System.currentTimeMillis()): String {
    val s = maxOf(1, kotlin.math.round((now - ts) / 1000.0).toInt())
    if (s < 60) return "just now"
    val m = kotlin.math.round(s / 60.0).toInt()
    if (m < 60) return "$m min ago"
    val h = kotlin.math.round(m / 60.0).toInt()
    if (h < 24) return "$h h ago"
    val d = kotlin.math.round(h / 24.0).toInt()
    if (d < 7) return "$d d ago"
    return Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("MMM d"))
}

fun categoryLabel(cat: String, language: String = "English"): String {
    val en = CATEGORY_LABEL[cat] ?: cat
    return I18n.t(en, language)
}

fun sourceLabel(source: String) = when (source) {
    "on_device" -> "On this device"
    "in_cloud" -> "Cloud"
    else -> "Preview"
}

fun excerpt(text: String, n: Int = 140): String {
    val t = text.replace(Regex("\\s+"), " ").trim()
    return if (t.length > n) t.take(n - 1) + "…" else t
}

private val CATEGORY_LABEL = mapOf(
    "phishing" to "Phishing",
    "impersonation" to "Impersonation",
    "delivery_fee" to "Parcel or toll fee",
    "bank_or_payment" to "Bank or payment",
    "tech_support" to "Tech support",
    "family_emergency" to "Family emergency",
    "romance" to "Romance",
    "investment_crypto" to "Investment or crypto",
    "job_offer" to "Job offer",
    "prize_lottery" to "Prize or lottery",
    "government_tax" to "Government or tax",
    "marketplace" to "Marketplace",
    "legitimate" to "Looks legitimate",
    "other" to "Other",
)

data class DayBar(val start: Long, val label: String, val safe: Int, val suspicious: Int, val scam: Int) {
    val total get() = safe + suspicious + scam
}

data class HistoryInsights(
    val days: List<DayBar>,
    val maxDay: Int,
    val top: List<Pair<String, Int>>,
    val catMax: Int,
    val topFlags: List<Pair<String, Int>>,
    val avgMs: Int?,
)

/** Last 7 local days, scam categories and repeated tricks. Same buckets as the web insights. */
fun historyInsights(runs: List<AnalysisRun>, now: Long = System.currentTimeMillis()): HistoryInsights {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val counts = Array(7) { IntArray(3) }
    val starts = LongArray(7)
    val labels = Array(7) { "" }
    for (i in 0 until 7) {
        val date = today.minusDays((6 - i).toLong())
        starts[i] = date.atStartOfDay(zone).toInstant().toEpochMilli()
        labels[i] = date.format(DateTimeFormatter.ofPattern("EEEEE"))
    }
    val cats = linkedMapOf<String, Int>()
    val flags = linkedMapOf<String, Int>()
    for (r in runs) {
        val a = r.analysis
        if (a.verdict != Verdict.SAFE) cats[a.category] = (cats[a.category] ?: 0) + 1
        for (f in a.redFlags) flags[f.label] = (flags[f.label] ?: 0) + 1
        val idx = (6 downTo 0).firstOrNull { r.createdAt >= starts[it] }
        if (idx != null && r.createdAt < starts[idx] + 86_400_000L) {
            val slot = when (a.verdict) {
                Verdict.SAFE -> 0
                Verdict.SUSPICIOUS -> 1
                Verdict.SCAM -> 2
            }
            counts[idx][slot]++
        }
    }
    val days = (0 until 7).map { i -> DayBar(starts[i], labels[i], counts[i][0], counts[i][1], counts[i][2]) }
    val top = cats.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
    val topFlags = flags.entries.sortedByDescending { it.value }.take(4).map { it.key to it.value }
    val onDevice = runs.filter { it.source == Source.ON_DEVICE }
    val avgMs = if (onDevice.isEmpty()) null else kotlin.math.round(onDevice.sumOf { it.latencyMs } / onDevice.size.toDouble()).toInt()
    return HistoryInsights(
        days = days,
        maxDay = maxOf(1, days.maxOf { it.total }),
        top = top,
        catMax = maxOf(1, top.maxOfOrNull { it.second } ?: 1),
        topFlags = topFlags,
        avgMs = avgMs,
    )
}

fun historyCsv(runs: List<AnalysisRun>): String {
    fun esc(v: Any) = "\"${v.toString().replace("\"", "\"\"")}\""
    val rows = listOf(listOf("date", "verdict", "risk", "category", "source", "summary", "message")) +
        runs.map { r ->
            listOf(
                Instant.ofEpochMilli(r.createdAt).toString(),
                r.analysis.verdict.name.lowercase(),
                r.analysis.riskScore.toString(),
                r.analysis.category,
                r.source.wire,
                r.analysis.summary,
                r.input,
            )
        }
    return rows.joinToString("\n") { row -> row.joinToString(",") { esc(it) } }
}

fun encodeRuns(runs: List<AnalysisRun>): String =
    JSONArray().apply { runs.forEach { put(runToJson(it)) } }.toString()

fun decodeRuns(raw: String?): List<AnalysisRun> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::runFromJson) }
    }.getOrDefault(emptyList())
}

fun encodeCounts(c: HistoryCounts) = JSONObject().apply {
    put("checked", c.checked)
    put("scams", c.scams)
    put("since", c.since)
}.toString()

fun decodeCounts(raw: String?): HistoryCounts {
    if (raw.isNullOrBlank()) return HistoryCounts(0, 0, 0)
    return runCatching {
        val o = JSONObject(raw)
        HistoryCounts(o.optInt("checked"), o.optInt("scams"), o.optLong("since"))
    }.getOrDefault(HistoryCounts(0, 0, 0))
}

private fun runToJson(run: AnalysisRun) = JSONObject().apply {
    put("id", run.id)
    put("createdAt", run.createdAt)
    put("input", run.input)
    put("source", run.source.wire)
    put("latencyMs", run.latencyMs)
    put("redactions", run.redactions)
    put("analysis", JSONObject().apply {
        put("verdict", run.analysis.verdict.name.lowercase())
        put("riskScore", run.analysis.riskScore)
        put("category", run.analysis.category)
        put("summary", run.analysis.summary)
        put("safeReply", run.analysis.safeReply)
        put("nextSteps", JSONArray(run.analysis.nextSteps))
        put("redFlags", JSONArray().apply {
            run.analysis.redFlags.forEach { f ->
                put(JSONObject().apply {
                    put("label", f.label)
                    put("evidence", f.evidence)
                    put("why", f.why)
                })
            }
        })
    })
}

private fun runFromJson(o: JSONObject): AnalysisRun? = runCatching {
    val a = o.getJSONObject("analysis")
    val verdict = when (a.optString("verdict").lowercase()) {
        "safe" -> Verdict.SAFE
        "scam" -> Verdict.SCAM
        "suspicious" -> Verdict.SUSPICIOUS
        else -> return null
    }
    val flags = a.optJSONArray("redFlags")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { RedFlag(it.optString("label"), it.optString("evidence"), it.optString("why")) }
        }
    } ?: emptyList()
    val steps = a.optJSONArray("nextSteps")?.let { arr ->
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    } ?: emptyList()
    val source = when (o.optString("source")) {
        "on_device" -> Source.ON_DEVICE
        "in_cloud" -> Source.IN_CLOUD
        else -> Source.IN_CLOUD
    }
    AnalysisRun(
        id = o.getString("id"),
        createdAt = o.optLong("createdAt"),
        input = o.optString("input"),
        analysis = Analysis(verdict, a.optInt("riskScore"), a.optString("category", "other"), a.optString("summary"), flags, steps, a.optString("safeReply")),
        source = source,
        latencyMs = o.optLong("latencyMs"),
        redactions = o.optInt("redactions"),
    )
}.getOrNull()

object LocalHistory {
    private const val PREFS = "chaukas.local"
    private const val RUNS = "runs"
    private const val COUNTS = "counts"

    fun load(ctx: Context): HistoryState {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return HistoryState(decodeRuns(p.getString(RUNS, null)), decodeCounts(p.getString(COUNTS, null)))
    }

    fun save(ctx: Context, state: HistoryState) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(RUNS, encodeRuns(state.runs))
            .putString(COUNTS, encodeCounts(state.counts))
            .apply()
    }

    /** Appends a new run to on-device history and updates running totals. */
    fun record(ctx: Context, run: AnalysisRun): HistoryState {
        val current = load(ctx)
        val next = applySave(current, run, System.currentTimeMillis())
        save(ctx, next)
        return next
    }
}
