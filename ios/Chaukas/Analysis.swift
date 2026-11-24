// Analysis model shared by both inference paths, plus a lenient parser that keeps the score
// and verdict consistent.

import Foundation

enum Verdict: String, Codable {
    case safe, suspicious, scam
    var label: String {
        switch self {
        case .safe: "Looks safe"
        case .suspicious: "Be careful"
        case .scam: "Likely scam"
        }
    }
}

/// Wire values match the web app's InferenceSource so Firestore records agree.
enum InferenceSource: String, Codable {
    case onDevice = "on_device"
    case inCloud = "in_cloud"
    var label: String { self == .onDevice ? "On this device" : "Cloud" }
}

struct RedFlag: Codable, Hashable {
    var label: String
    var evidence: String
    var why: String
}

struct Analysis: Codable, Hashable {
    var verdict: Verdict
    var riskScore: Int
    var category: String
    var summary: String
    var redFlags: [RedFlag]
    var nextSteps: [String]
    var safeReply: String
}

struct AnalysisRun: Identifiable, Hashable {
    let id = UUID().uuidString
    let createdAt = Date()
    let input: String
    var analysis: Analysis
    let source: InferenceSource
    let latencyMs: Int
    let redactions: Int
}

enum AnalysisParser {
    /// Lenient parsing: strips code fences and keeps score and verdict consistent.
    static func parse(_ text: String) throws -> Analysis {
        let cleaned = text.replacingOccurrences(of: "```json", with: "").replacingOccurrences(of: "```", with: "")
        guard let start = cleaned.firstIndex(of: "{"), let end = cleaned.lastIndex(of: "}"), start < end,
              let data = String(cleaned[start ... end]).data(using: .utf8),
              let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { throw ChaukasError.unreadable }

        var score = min(100, max(0, (obj["riskScore"] as? NSNumber)?.intValue ?? 50))
        let verdict = Verdict(rawValue: (obj["verdict"] as? String ?? "").lowercased())
            ?? (score >= 70 ? .scam : score > 30 ? .suspicious : .safe)
        switch verdict {
        case .safe: score = min(score, 30)
        case .suspicious: score = min(max(score, 31), 69)
        case .scam: score = max(score, 70)
        }
        let flags = (obj["redFlags"] as? [[String: Any]] ?? []).compactMap { f -> RedFlag? in
            guard let label = f["label"] as? String, !label.isEmpty else { return nil }
            return RedFlag(label: label, evidence: f["evidence"] as? String ?? "", why: f["why"] as? String ?? "")
        }
        return Analysis(
            verdict: verdict,
            riskScore: score,
            category: obj["category"] as? String ?? "other",
            summary: obj["summary"] as? String ?? "No summary was returned.",
            redFlags: Array(flags.prefix(5)),
            nextSteps: Array((obj["nextSteps"] as? [String] ?? []).prefix(4)),
            safeReply: obj["safeReply"] as? String ?? ""
        )
    }
}

enum ChaukasError: LocalizedError {
    case unreadable, privateNeedsDevice, firebaseMissing
    var errorDescription: String? {
        switch self {
        case .unreadable: "The model returned an unreadable answer. Try again."
        case .privateNeedsDevice: "Private mode needs Apple Intelligence on this device. Switch to Smart to use the cloud with personal details removed."
        case .firebaseMissing: "Add GoogleService-Info.plist to use the cloud model."
        }
    }
}
