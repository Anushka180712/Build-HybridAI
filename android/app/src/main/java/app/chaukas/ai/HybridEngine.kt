/**
 * Firebase AI Logic hybrid inference on Android: Gemini Nano through ML Kit / AICore
 * (OnDeviceConfig) with Gemini in the cloud as the fallback, using the same privacy modes and
 * redaction rules as the web app.
 */
package app.chaukas.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.ondevice.DownloadStatus
import com.google.firebase.ai.ondevice.FirebaseAIOnDevice
import com.google.firebase.ai.ondevice.OnDeviceModelStatus
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

enum class PrivacyMode(val title: String, val line: String) {
    PRIVATE("Private", "Stays on this device, always. Works offline once the model is downloaded."),
    SMART("Smart", "On this device when possible. If not, personal details are removed before it goes to the cloud."),
    CLOUD("Most accurate", "Uses the larger cloud model first, with personal details removed. Falls back to on-device if offline."),
}

sealed interface ModelState {
    data object Checking : ModelState
    data object Available : ModelState
    data object Downloadable : ModelState
    data class Downloading(val fraction: Float?) : ModelState
    data object Unavailable : ModelState
}

/**
 * The same hybrid flow as the web app, on Android: Gemini Nano through
 * ML Kit / AICore when available, Gemini in the cloud otherwise, routed by
 * Firebase AI Logic's InferenceMode.
 */
@OptIn(PublicPreviewAPI::class)
class HybridEngine(private val cloudModelName: () -> String) {

    private val models = mutableMapOf<String, GenerativeModel>()

    private fun model(mode: InferenceMode): GenerativeModel {
        val key = "$mode|${cloudModelName()}"
        return models.getOrPut(key) {
            Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = cloudModelName(),
                generationConfig = generationConfig { maxOutputTokens = 700 },
                onDeviceConfig = OnDeviceConfig(mode = mode, maxOutputTokens = 256),
            )
        }
    }

    private fun PrivacyMode.inference() = when (this) {
        PrivacyMode.PRIVATE -> InferenceMode.ONLY_ON_DEVICE
        PrivacyMode.SMART -> InferenceMode.PREFER_ON_DEVICE
        PrivacyMode.CLOUD -> InferenceMode.PREFER_IN_CLOUD
    }

    // FirebaseAIOnDevice is deprecated in favour of GenerativeModel.OnDeviceExtension. It still
    // works in the pinned SDK; move over together with the next firebase-ai upgrade.
    @Suppress("DEPRECATION")
    suspend fun modelState(): ModelState = when (FirebaseAIOnDevice.checkStatus()) {
        OnDeviceModelStatus.AVAILABLE -> ModelState.Available
        OnDeviceModelStatus.DOWNLOADABLE -> ModelState.Downloadable
        OnDeviceModelStatus.DOWNLOADING -> ModelState.Downloading(null)
        else -> ModelState.Unavailable
    }

    /** AICore never downloads on demand, so Chaukas offers an explicit download. */
    @Suppress("DEPRECATION")
    fun download(): Flow<ModelState> {
        var total = 0L
        return FirebaseAIOnDevice.download().map { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> {
                    total = status.bytesToDownload
                    ModelState.Downloading(0f)
                }
                is DownloadStatus.DownloadInProgress -> ModelState.Downloading(if (total > 0) status.totalBytesDownloaded.toFloat() / total else null)
                is DownloadStatus.DownloadCompleted -> ModelState.Available
                is DownloadStatus.DownloadFailed -> ModelState.Unavailable
                else -> ModelState.Downloading(null)
            }
        }
    }

    suspend fun analyze(text: String, mode: PrivacyMode, redact: Boolean, privateTerms: List<String>, language: String = "English"): AnalysisRun {
        val started = System.currentTimeMillis()
        val deviceReady = modelState() == ModelState.Available
        if (mode == PrivacyMode.PRIVATE && !deviceReady) {
            throw IllegalStateException("Private mode needs Gemini Nano. Download it from the Engine tab, or switch to Smart.")
        }
        val mayLeave = mode == PrivacyMode.CLOUD || (mode == PrivacyMode.SMART && !deviceReady)
        val red = if (mayLeave && redact) Redactor.redact(text, privateTerms) else null
        val (signals, _) = Signals.compute(text)

        val response = model(mode.inference()).generateContent(Prompts.analysis(red?.text ?: text, signals, language))
        val source = if (response.inferenceSource.toString().contains("DEVICE", ignoreCase = true)) Source.ON_DEVICE else Source.IN_CLOUD
        var analysis = AnalysisParser.parse(response.text ?: "")
        if (red != null) {
            analysis = analysis.copy(
                summary = Redactor.restore(analysis.summary, red.map),
                redFlags = analysis.redFlags.map { it.copy(evidence = Redactor.restore(it.evidence, red.map)) },
            )
        }
        return AnalysisRun(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            input = text,
            analysis = analysis,
            source = source,
            latencyMs = System.currentTimeMillis() - started,
            redactions = if (source == Source.IN_CLOUD) red?.count ?: 0 else 0,
        )
    }
}

object Prompts {
    fun analysis(message: String, signals: List<Signal>, language: String = "English"): String = buildString {
        appendLine("You are Chaukas, a calm fraud-prevention expert helping ordinary people decide whether a message is a scam.")
        if (language.isNotBlank() && language != "English") {
            appendLine("Write summary, every \"why\", nextSteps, safeReply and reassurances in $language, in simple everyday words. Keep \"evidence\" quotes exactly as they appear in the message. Keep JSON keys and enum values (verdict, category, severity) in English.")
        }
        appendLine("Reply ONLY with one JSON object with these keys:")
        appendLine("""{"verdict":"safe|suspicious|scam","riskScore":0-100,"category":"phishing|impersonation|delivery_fee|bank_or_payment|tech_support|family_emergency|romance|investment_crypto|job_offer|prize_lottery|government_tax|marketplace|legitimate|other","summary":"one plain sentence","redFlags":[{"label":"","evidence":"exact short quote","why":""}],"nextSteps":["short action"],"safeReply":""}""")
        appendLine("riskScore must agree with verdict: safe 0-30, suspicious 31-69, scam 70-100. Max 4 red flags, max 3 next steps.")
        appendLine("Text like [PHONE_1] is a redacted placeholder; treat it as a normal value.")
        if (signals.isNotEmpty()) {
            appendLine("Local pattern checks found (may be false alarms): " + signals.joinToString { "${it.label}: ${it.matches.take(2).joinToString()}" })
        }
        appendLine("MESSAGE:")
        appendLine("\"\"\"")
        appendLine(message.trim().take(2500))
        append("\"\"\"")
    }
}
