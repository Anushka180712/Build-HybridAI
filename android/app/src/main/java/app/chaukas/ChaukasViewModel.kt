/**
 * UI state for the Android app: runs checks through HybridEngine, tracks the on-device model
 * and syncs history and circles.
 */
package app.chaukas

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.chaukas.ai.AnalysisRun
import app.chaukas.ai.HybridEngine
import app.chaukas.ai.ModelState
import app.chaukas.ai.PrivacyMode
import app.chaukas.ai.Signals
import app.chaukas.ai.Verdict
import app.chaukas.data.HistoryCounts
import app.chaukas.data.HistoryState
import app.chaukas.data.InviteUtils
import app.chaukas.data.LocalHistory
import app.chaukas.data.QrFinding
import app.chaukas.data.RegionalVerdict
import app.chaukas.data.Repository
import app.chaukas.data.applyClear
import app.chaukas.data.applyDelete
import app.chaukas.data.applySave
import app.chaukas.guard.Guard
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class ChaukasViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("chaukas.settings", Context.MODE_PRIVATE)
    private var cloudModel = "gemini-3.5-flash-lite"
    private val engine = HybridEngine { cloudModel }

    var text by mutableStateOf("")
    var mode by mutableStateOf(PrivacyMode.SMART)
    var redact by mutableStateOf(true)
    var busy by mutableStateOf(false)
    var stage by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    var run by mutableStateOf<AnalysisRun?>(null)
    var modelState by mutableStateOf<ModelState>(ModelState.Checking)
    var trend by mutableStateOf(Repository.DEFAULT_TREND_BANNER)
    var history by mutableStateOf(HistoryState(emptyList(), HistoryCounts(0, 0, 0)))
        private set

    var attachedImageUri by mutableStateOf<Uri?>(null)
    var attachedBitmap by mutableStateOf<Bitmap?>(null)
    var qrFinding by mutableStateOf<QrFinding?>(null)
    var language by mutableStateOf(prefs.getString("language", "English") ?: "English")
    var autoShareCircle by mutableStateOf(prefs.getBoolean("autoShareCircle", true))
    var isKnownContact by mutableStateOf(false)
    var senderName by mutableStateOf("")
    var isSavedToHistory by mutableStateOf(false)

    data class InAppAlert(
        val title: String,
        val message: String,
        val score: Int? = null,
        val from: String? = null,
        val text: String? = null,
        val isScam: Boolean = true,
        val isKnownContact: Boolean = false,
    )

    var inAppAlert by mutableStateOf<InAppAlert?>(null)

    fun showInAppAlert(
        title: String,
        message: String,
        score: Int? = null,
        from: String? = null,
        text: String? = null,
        isKnownContact: Boolean = false,
    ) {
        inAppAlert = InAppAlert(title, message, score, from, text, (score ?: 0) >= 65, isKnownContact)
    }

    fun dismissInAppAlert() {
        inAppAlert = null
    }

    private var tts: TextToSpeech? = null
    private val historyPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "runs" || key == "counts") {
            history = runCatching { LocalHistory.load(getApplication()) }.getOrDefault(history)
        }
    }

    val liveSignals get() = Signals.compute(text).first

    init {
        history = runCatching { LocalHistory.load(app) }.getOrDefault(history)
        app.getSharedPreferences("chaukas.local", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(historyPrefsListener)
        viewModelScope.launch {
            cloudModel = Repository.cloudModel()
            trend = Repository.trendBanner()
        }
        refreshModel()
        initTts(app)

        Guard.onAlertCallback = { alert, appName, isKnown ->
            viewModelScope.launch(Dispatchers.Main) {
                showInAppAlert(
                    title = if (isKnown) "Suspicious message from ${alert.from}" else (alert.reasons.firstOrNull() ?: "Scam alert"),
                    message = alert.text,
                    score = alert.score,
                    from = if (alert.from.isNotBlank()) alert.from else appName,
                    text = alert.text,
                    isKnownContact = isKnown,
                )
            }
        }
    }

    private fun initTts(ctx: Context) {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "IN")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Guard.onAlertCallback = null
        getApplication<Application>().getSharedPreferences("chaukas.local", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(historyPrefsListener)
        tts?.stop()
        tts?.shutdown()
    }

    fun setLang(newLang: String) {
        language = newLang
        prefs.edit().putString("language", newLang).apply()
        val loc = when (newLang) {
            "Hindi" -> Locale("hi", "IN")
            "Bengali" -> Locale("bn", "IN")
            "Tamil" -> Locale("ta", "IN")
            "Telugu" -> Locale("te", "IN")
            "Marathi" -> Locale("mr", "IN")
            else -> Locale("en", "IN")
        }
        tts?.language = loc
    }

    fun setAutoShare(enabled: Boolean) {
        autoShareCircle = enabled
        prefs.edit().putBoolean("autoShareCircle", enabled).apply()
    }

    fun refreshModel() = viewModelScope.launch {
        modelState = runCatching { engine.modelState() }.getOrDefault(ModelState.Unavailable)
    }

    fun download() = viewModelScope.launch {
        runCatching { engine.download().collect { modelState = it } }.onFailure { modelState = ModelState.Unavailable }
    }

    fun removeAttachedImage() {
        attachedImageUri = null
        attachedBitmap = null
        qrFinding = null
    }

    fun clear() {
        text = ""
        attachedImageUri = null
        attachedBitmap = null
        qrFinding = null
        run = null
        error = null
    }

    /** Reads a screenshot on the phone (Latin and Devanagari scripts), extracts text and inspects QR. */
    fun checkImage(ctx: Context, uri: Uri) {
        if (busy) return
        busy = true
        error = null
        run = null
        attachedImageUri = uri
        attachedBitmap = null
        viewModelScope.launch {
            val found = runCatching {
                val image = InputImage.fromFilePath(ctx, uri)
                val latin = runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await().text }.getOrDefault("")
                val hindi = runCatching { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()).process(image).await().text }.getOrDefault("")
                mergeExtractedText(latin, hindi)
            }.getOrNull()

            busy = false
            if (found.isNullOrBlank()) {
                error = "Couldn't read any text in that picture. Try pasting the message instead."
            } else {
                val trimmed = found.trim()
                if (trimmed.contains("upi://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                    val finding = InviteUtils.inspectQr(trimmed)
                    qrFinding = finding
                }
                text = trimmed
                check()
            }
        }
    }

    /** Reads a captured camera photo bitmap directly, extracts text and inspects QR. */
    fun checkBitmap(ctx: Context, bitmap: Bitmap) {
        if (busy) return
        busy = true
        error = null
        run = null
        attachedBitmap = bitmap
        attachedImageUri = null
        viewModelScope.launch {
            val found = runCatching {
                val image = InputImage.fromBitmap(bitmap, 0)
                val latin = runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await().text }.getOrDefault("")
                val hindi = runCatching { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()).process(image).await().text }.getOrDefault("")
                mergeExtractedText(latin, hindi)
            }.getOrNull()

            busy = false
            if (found.isNullOrBlank()) {
                error = "Couldn't read any text in that picture. Try pasting the message instead."
            } else {
                val trimmed = found.trim()
                if (trimmed.contains("upi://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                    val finding = InviteUtils.inspectQr(trimmed)
                    qrFinding = finding
                }
                text = trimmed
                check()
            }
        }
    }

    private fun mergeExtractedText(latin: String, hindi: String): String {
        val l = latin.trim()
        val h = hindi.trim()
        if (l.isEmpty()) return h
        if (h.isEmpty()) return l
        if (l.contains(h)) return l
        if (h.contains(l)) return h
        val lLines = l.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val hLines = h.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val merged = LinkedHashSet<String>()
        merged.addAll(lLines)
        for (line in hLines) {
            if (!merged.any { it.contains(line) || line.contains(it) }) {
                merged.add(line)
            }
        }
        return merged.joinToString("\n")
    }

    suspend fun scanHandleFromImage(ctx: Context, uri: Uri): String? {
        return runCatching {
            val image = InputImage.fromFilePath(ctx, uri)
            val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await().text
            val hindi = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()).process(image).await().text
            val combined = "$latin\n$hindi"
            val fromLink = Regex("""#/circle/@?([a-z0-9-]+)""").find(combined)?.groupValues?.get(1)
            if (fromLink != null) {
                val h = InviteUtils.normalizeHandle(fromLink)
                if (h.isNotBlank()) return@runCatching h
            }
            val match = Regex("""\b([a-z0-9]{2,16}-[0-9]{4})\b""", RegexOption.IGNORE_CASE).find(combined)?.groupValues?.get(1)
            if (match != null) {
                val h = InviteUtils.normalizeHandle(match)
                if (h.isNotBlank()) return@runCatching h
            }
            null
        }.getOrNull()
    }

    fun check(isKnown: Boolean = false, fromSender: String = "") {
        if (text.isBlank() && attachedImageUri == null && attachedBitmap == null) return
        if (busy) return
        busy = true
        error = null
        run = null
        stage = 0
        isKnownContact = isKnown
        senderName = fromSender
        isSavedToHistory = false

        // Stage progress animation
        viewModelScope.launch {
            while (busy) {
                delay(700)
                if (stage < 2) stage++
            }
        }

        viewModelScope.launch {
            try {
                val existing = if (!isKnown) history.runs.firstOrNull { it.input.trim() == text.trim() } else null
                if (existing != null) {
                    run = existing
                    isSavedToHistory = true
                } else {
                    val r = engine.analyze(text, mode, redact, emptyList(), language)
                    run = r
                    if (!isKnown && r.analysis.verdict != Verdict.SAFE && r.analysis.riskScore >= 65) {
                        saveCurrentRunToHistory()
                    } else {
                        isSavedToHistory = false
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }

    fun dismissRun() {
        run = null
        text = ""
        attachedImageUri = null
        attachedBitmap = null
        error = null
        isKnownContact = false
        senderName = ""
        isSavedToHistory = false
    }

    fun saveCurrentRunToHistory() {
        val r = run ?: return
        if (isSavedToHistory) return
        saveRun(r)
        Repository.saveCheck(r)
        isSavedToHistory = true
        if (r.analysis.verdict != Verdict.SAFE && r.analysis.riskScore >= 65) {
            viewModelScope.launch {
                runCatching { Repository.autoShareToCircles(r) }
            }
        }
    }

    fun speakRun(r: AnalysisRun) {
        val a = r.analysis
        val regional = RegionalVerdict.forVerdict(language, a.verdict)
        val spoken = if (language == "English") {
            "${a.verdict.label}. ${a.summary}. What to do: ${a.nextSteps.joinToString(". ")}"
        } else {
            "$regional ${a.summary}"
        }
        tts?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "chaukas-verdict")
    }

    /** Keeps this phone's history and the never-down totals. A repeat of the same check is not counted twice. */
    fun saveRun(run: AnalysisRun) {
        val next = applySave(history, run, System.currentTimeMillis())
        history = next
        runCatching { LocalHistory.save(getApplication(), next) }
        runCatching { Repository.saveCheck(run) }
    }

    fun deleteRun(id: String) {
        val next = applyDelete(history, id)
        history = next
        runCatching { LocalHistory.save(getApplication(), next) }
        Repository.deleteCheck(id)
    }

    fun clearHistory() {
        val next = applyClear(history)
        history = next
        runCatching { LocalHistory.save(getApplication(), next) }
    }
}
