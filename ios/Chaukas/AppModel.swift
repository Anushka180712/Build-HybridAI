// Observable app state: runs checks, loads Remote Config and keeps history and circles in
// sync.

import Foundation
import Observation

@MainActor
@Observable
final class AppModel {
    var text = ""
    var mode: PrivacyMode = .smart
    var redact = true
    var busy = false
    var error: String?
    var run: AnalysisRun?
    var history: [AnalysisRun] = []
    var trendBanner = ""

    private let engine = HybridEngine()
    var deviceState: DeviceModelState { engine.deviceState }
    var liveSignals: [Signal] { Signals.compute(text).signals }

    init() {
        Task {
            let rc = await Repository.loadRemoteConfig()
            engine.cloudModelName = rc.cloudModel
            trendBanner = rc.banner
        }
    }

    /// chaukas://check?text=... lets Shortcuts and other apps hand a message to Chaukas.
    func handle(url: URL) {
        guard let text = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "text" })?.value else { return }
        self.text = text
        check()
    }

    func check() {
        guard !busy, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        busy = true
        error = nil
        run = nil
        let (text, mode, redact) = (text, mode, redact)
        Task {
            defer { busy = false }
            do {
                let r = try await engine.analyze(text, mode: mode, redact: redact, privateTerms: [])
                run = r
                history.insert(r, at: 0)
                Repository.save(r)
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}
