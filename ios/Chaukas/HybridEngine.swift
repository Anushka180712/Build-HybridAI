// Hybrid inference on iOS with Firebase AI Logic: Apple Foundation Models on-device and Gemini
// in the cloud through one HybridModel session.

import FirebaseAILogic
import FirebaseCore
import Foundation

enum PrivacyMode: String, CaseIterable, Identifiable {
    case `private`, smart, cloud
    var id: String { rawValue }
    var title: String {
        switch self {
        case .private: "Private"
        case .smart: "Smart"
        case .cloud: "Most accurate"
        }
    }
    var line: String {
        switch self {
        case .private: "Stays on this iPhone, always."
        case .smart: "On this iPhone when possible. Otherwise personal details are removed before using the cloud."
        case .cloud: "Cloud model first, with personal details removed."
        }
    }
}

enum DeviceModelState: Equatable {
    case available
    case notEnabled // Apple Intelligence is off
    case downloading // model not ready yet
    case unsupported(String)

    var title: String {
        switch self {
        case .available: "Apple Intelligence is ready"
        case .notEnabled: "Turn on Apple Intelligence"
        case .downloading: "Model is getting ready"
        case .unsupported: "Not available on this device"
        }
    }
}

/// Hybrid inference on iOS through Firebase AI Logic's `HybridModel`: Apple's
/// on-device Foundation Model (`FirebaseAI.SystemLanguageModel`) and Gemini in
/// the cloud behind one `GenerativeModelSession`. The SDK picks the route; the
/// response's `modelVersion` tells us which one answered.
///
/// Because the route is decided inside the call, any prompt that *may* reach the
/// cloud is redacted first. Private mode uses the on-device model only and is
/// never redacted.
final class HybridEngine {
    var cloudModelName = "gemini-3.5-flash-lite"

    private static let instructions = "You are Chaukas, a calm fraud-prevention expert. Reply only with JSON."
    private static let onDeviceName = "apple-foundation-models-system-language-model"

    var deviceState: DeviceModelState {
        switch FirebaseAI.SystemLanguageModel.default.availability {
        case .available: return .available
        case .unavailable(.appleIntelligenceNotEnabled): return .notEnabled
        case .unavailable(.modelNotReady): return .downloading
        case .unavailable(.deviceNotEligible): return .unsupported("This device does not support Apple Intelligence")
        case .unavailable: return .unsupported("Needs iOS 26 with Apple Intelligence")
        }
    }

    /// One session per check: sessions keep history, and each check is independent.
    private func session(for mode: PrivacyMode) throws -> GenerativeModelSession {
        guard FirebaseApp.app() != nil else { throw ChaukasError.firebaseMissing }
        let ai = FirebaseAI.firebaseAI(backend: .googleAI())
        let device = FirebaseAI.SystemLanguageModel.default
        let cloud: GeminiModelProvider = .geminiModel(name: cloudModelName)
        switch mode {
        case .private:
            return ai.generativeModelSession(model: device, instructions: Self.instructions)
        case .smart:
            return ai.generativeModelSession(model: .hybridModel(primary: device, secondary: cloud), instructions: Self.instructions)
        case .cloud:
            return ai.generativeModelSession(model: .hybridModel(primary: cloud, secondary: device), instructions: Self.instructions)
        }
    }

    func analyze(_ text: String, mode: PrivacyMode, redact: Bool, privateTerms: [String]) async throws -> AnalysisRun {
        let started = Date()
        if mode == .private && deviceState != .available { throw ChaukasError.privateNeedsDevice }

        let signals = Signals.compute(text).signals
        let red = (mode != .private && redact) ? Redactor.redact(text, privateTerms: privateTerms) : nil
        let options: ResponseGenerationOptions = .hybrid(
            gemini: GenerationConfig(maxOutputTokens: 700, responseMIMEType: "application/json"),
            foundationModels: FirebaseAI.GenerationOptions(temperature: 0.2, maximumResponseTokens: 700)
        )

        let response = try await session(for: mode).respond(to: Prompts.analysis(red?.text ?? text, signals: signals), options: options)
        let source: InferenceSource = response.rawResponse.modelVersion == Self.onDeviceName ? .onDevice : .inCloud

        var analysis = try AnalysisParser.parse(response.content)
        if let red {
            analysis.summary = Redactor.restore(analysis.summary, map: red.map)
            analysis.redFlags = analysis.redFlags.map { RedFlag(label: $0.label, evidence: Redactor.restore($0.evidence, map: red.map), why: $0.why) }
            analysis.nextSteps = analysis.nextSteps.map { Redactor.restore($0, map: red.map) }
        }
        return AnalysisRun(input: text, analysis: analysis, source: source,
                           latencyMs: Int(Date().timeIntervalSince(started) * 1000),
                           redactions: source == .inCloud ? (red?.count ?? 0) : 0)
    }
}

enum Prompts {
    static func analysis(_ message: String, signals: [Signal]) -> String {
        var lines = [
            "Judge whether the MESSAGE is a scam. Reply ONLY with one JSON object:",
            #"{"verdict":"safe|suspicious|scam","riskScore":0-100,"category":"phishing|impersonation|delivery_fee|bank_or_payment|tech_support|family_emergency|romance|investment_crypto|job_offer|prize_lottery|government_tax|marketplace|legitimate|other","summary":"one plain sentence","redFlags":[{"label":"","evidence":"exact short quote","why":""}],"nextSteps":["short action"],"safeReply":""}"#,
            "riskScore must agree with verdict: safe 0-30, suspicious 31-69, scam 70-100. Max 4 red flags, max 3 next steps.",
            "Text like [PHONE_1] is a redacted placeholder; treat it as a normal value.",
        ]
        if !signals.isEmpty {
            lines.append("Local pattern checks found (may be false alarms): " + signals.map { "\($0.label): \($0.matches.prefix(2).joined(separator: ", "))" }.joined(separator: "; "))
        }
        lines.append("MESSAGE:\n\"\"\"\n\(message.prefix(2500))\n\"\"\"")
        return lines.joined(separator: "\n")
    }
}
