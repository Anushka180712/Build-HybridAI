package app.chaukas.data

import app.chaukas.ai.Analysis
import app.chaukas.ai.AnalysisRun
import app.chaukas.ai.RedFlag
import app.chaukas.ai.Source
import app.chaukas.ai.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHistoryTest {
    private fun run(
        id: String,
        verdict: Verdict = Verdict.SCAM,
        score: Int = 80,
        source: Source = Source.ON_DEVICE,
        at: Long = 1_000L,
        flags: List<RedFlag> = emptyList(),
    ) = AnalysisRun(
        id, at, "please send the code",
        Analysis(verdict, score, "phishing", "A fake bank text", flags, listOf("Don't reply"), "No thanks"),
        source, 1500, 2,
    )

    @Test fun countsGoUpOnceAndSurviveDeleteAndClear() {
        val empty = HistoryState(emptyList(), HistoryCounts(0, 0, 0))
        val first = applySave(empty, run("a"), 10)
        assertEquals(1, first.counts.checked)
        assertEquals(1, first.counts.scams)
        val again = applySave(first, run("a", score = 90), 20)
        assertEquals(1, again.counts.checked)
        assertEquals(90, again.runs.first().analysis.riskScore)
        val second = applySave(again, run("b", verdict = Verdict.SAFE, score = 10), 30)
        assertEquals(2, second.counts.checked)
        assertEquals(1, second.counts.scams)
        val deleted = applyDelete(second, "a")
        assertEquals(1, deleted.runs.size)
        assertEquals(2, historyStats(deleted).total)
        assertEquals(1, historyStats(deleted).scams)
        val cleared = applyClear(deleted)
        assertTrue(cleared.runs.isEmpty())
        assertEquals(2, historyStats(cleared).total)
    }

    @Test fun suspiciousAt65CountsAsScamAndSafeDoesNot() {
        assertTrue(countsAsScam(run("s", Verdict.SUSPICIOUS, 65)))
        assertFalse(countsAsScam(run("s", Verdict.SUSPICIOUS, 64)))
        assertFalse(countsAsScam(run("s", Verdict.SAFE, 90)))
    }

    @Test fun keepsNewest150() {
        var state = HistoryState(emptyList(), HistoryCounts(0, 0, 5))
        for (i in 0 until 160) state = applySave(state, run("id$i"), 100)
        assertEquals(150, state.runs.size)
        assertEquals("id159", state.runs.first().id)
        assertEquals("id10", state.runs.last().id)
        assertEquals(160, state.counts.checked)
    }

    @Test fun roundTripAndCorruptPrefs() {
        val run = run("z", flags = listOf(RedFlag("Urgency", "now", "pressure")))
        val back = decodeRuns(encodeRuns(listOf(run)))
        assertEquals(run, back.single())
        assertTrue(decodeRuns("not json").isEmpty())
        assertEquals(HistoryCounts(0, 0, 0), decodeCounts("{"))
        val counts = HistoryCounts(4, 2, 9)
        assertEquals(counts, decodeCounts(encodeCounts(counts)))
    }

    @Test fun csvEscapesQuotes() {
        val csv = historyCsv(listOf(run("a").copy(input = "say \"hi\"")))
        assertTrue(csv.startsWith("\"date\""))
        assertTrue(csv.contains("\"say \"\"hi\"\"\""))
        assertTrue(csv.contains("\"scam\""))
        assertTrue(csv.contains("\"on_device\""))
    }

    @Test fun timeAgoBuckets() {
        val now = 10_000_000L
        assertEquals("just now", timeAgo(now - 5_000, now))
        assertEquals("2 min ago", timeAgo(now - 90_000, now))
        assertEquals("2 h ago", timeAgo(now - 2 * 3_600_000, now))
    }

    @Test fun insightsSkipSafeCategoriesAndAverageOnDevice() {
        val now = 1_700_000_000_000L
        val scam = run("a", at = now, flags = listOf(RedFlag("Urgency", "", "")))
        val safe = run("b", Verdict.SAFE, 5, Source.IN_CLOUD, now, emptyList())
        val data = historyInsights(listOf(scam, safe), now)
        assertEquals(listOf("phishing" to 1), data.top)
        assertEquals(listOf("Urgency" to 1), data.topFlags)
        assertEquals(1500, data.avgMs)
        assertEquals(2, data.days.last().total)
        assertNull(historyInsights(emptyList(), now).avgMs)
    }

    @Test fun onDevicePercentUsesTotalsThatDoNotShrink() {
        val state = HistoryState(
            listOf(run("a", source = Source.ON_DEVICE), run("b", source = Source.IN_CLOUD)),
            HistoryCounts(4, 1, 1),
        )
        val stats = historyStats(state)
        assertEquals(4, stats.total)
        assertEquals(50, stats.localPct)
        assertEquals(4, stats.hidden)
    }
}
