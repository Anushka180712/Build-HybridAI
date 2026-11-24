/**
 * UI state for the Android app: runs checks through HybridEngine, tracks the on-device model
 * and syncs history and circles.
 */
package app.chaukas

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chaukas.ai.AnalysisRun
import app.chaukas.ai.HybridEngine
import app.chaukas.ai.ModelState
import app.chaukas.ai.PrivacyMode
import app.chaukas.ai.Signals
import app.chaukas.data.Repository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChaukasViewModel : ViewModel() {
    private var cloudModel = "gemini-3.5-flash-lite"
    private val engine = HybridEngine { cloudModel }

    var text by mutableStateOf("")
    var mode by mutableStateOf(PrivacyMode.SMART)
    var redact by mutableStateOf(true)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var run by mutableStateOf<AnalysisRun?>(null)
    var modelState by mutableStateOf<ModelState>(ModelState.Checking)
    var trend by mutableStateOf("")
    val history = mutableListOf<AnalysisRun>()

    val liveSignals get() = Signals.compute(text).first

    init {
        viewModelScope.launch {
            cloudModel = Repository.cloudModel()
            trend = Repository.trendBanner()
        }
        refreshModel()
    }

    fun refreshModel() = viewModelScope.launch {
        modelState = runCatching { engine.modelState() }.getOrDefault(ModelState.Unavailable)
    }

    fun download() = viewModelScope.launch {
        runCatching { engine.download().collect { modelState = it } }.onFailure { modelState = ModelState.Unavailable }
    }

    /** Reads a shared screenshot on the phone (Latin and Devanagari scripts), then checks the text. */
    fun checkImage(ctx: Context, uri: Uri) {
        if (busy) return
        busy = true
        error = null
        run = null
        viewModelScope.launch {
            val found = runCatching {
                val image = InputImage.fromFilePath(ctx, uri)
                val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await().text
                val hindi = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()).process(image).await().text
                if (hindi.length > latin.length) hindi else latin
            }.getOrNull()
            busy = false
            if (found.isNullOrBlank()) {
                error = "Couldn't read any text in that picture. Try pasting the message instead."
            } else {
                text = found.trim()
                check()
            }
        }
    }

    fun check() {
        if (text.isBlank() || busy) return
        busy = true
        error = null
        run = null
        viewModelScope.launch {
            try {
                val r = engine.analyze(text, mode, redact, emptyList())
                run = r
                history.add(0, r)
                Repository.saveCheck(r)
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }
}
