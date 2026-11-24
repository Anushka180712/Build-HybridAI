// SwiftUI screens: Check, verdict, History, Family Circle and Engine status.

import FirebaseFirestore
import SwiftUI
import UIKit

struct RootView: View {
    var body: some View {
        TabView {
            CheckView().tabItem { Label("Check", systemImage: "checkmark.shield") }
            HistoryView().tabItem { Label("History", systemImage: "clock.arrow.circlepath") }
            FamilyView().tabItem { Label("Family", systemImage: "person.2") }
            EngineView().tabItem { Label("Engine", systemImage: "cpu") }
        }
        .tint(Palette.lilac500)
    }
}

// MARK: - Check

struct CheckView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                let ready = model.deviceState == .available
                Pill(text: ready ? "Apple Intelligence on this iPhone" : "Cloud fallback ready",
                     systemImage: ready ? "iphone" : "cloud", bg: ready ? Palette.mint100 : Palette.sky100, fg: ready ? Palette.mint700 : Palette.sky600)
                Text("Got a message that feels off?").font(.display(34)).foregroundStyle(Palette.ink)
                Text("Paste it below. Chaukas tells you if it is a scam and what to do next.").foregroundStyle(Palette.ink2)

                if !model.trendBanner.isEmpty {
                    Label(model.trendBanner, systemImage: "flame").font(.footnote).foregroundStyle(Palette.rose700).card(Palette.rose50)
                }

                ZStack(alignment: .topLeading) {
                    if model.text.isEmpty {
                        Text("Paste a text, email or WhatsApp message…").foregroundStyle(Palette.ink3).padding(.top, 8).padding(.leading, 5)
                    }
                    TextEditor(text: $model.text).scrollContentBackground(.hidden).frame(minHeight: 150)
                }
                .card()

                HStack {
                    Button {
                        if let s = UIPasteboard.general.string { model.text = s }
                    } label: { Label("Paste", systemImage: "doc.on.clipboard") }
                        .buttonStyle(.bordered).tint(Palette.ink2)
                    if !model.text.isEmpty {
                        Button("Clear") { model.text = ""; model.run = nil }.buttonStyle(.borderless).tint(Palette.ink3)
                    }
                }

                if !model.liveSignals.isEmpty && model.run == nil {
                    FlowLayout(spacing: 6) {
                        ForEach(model.liveSignals) { Pill(text: $0.label, bg: Palette.rose50, fg: Palette.rose700) }
                    }
                }

                Picker("Privacy", selection: $model.mode) {
                    ForEach(PrivacyMode.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.segmented)
                Text(model.mode.line).font(.footnote).foregroundStyle(Palette.ink2)

                Button(action: model.check) {
                    HStack {
                        if model.busy { ProgressView().tint(.white) } else { Image(systemName: "checkmark.shield.fill") }
                        Text(model.busy ? "Checking…" : "Check it").fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity, minHeight: 50)
                }
                .buttonStyle(.borderedProminent).tint(Palette.ink)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .disabled(model.busy || model.text.isEmpty)
                .sensoryFeedback(.success, trigger: model.run?.id)

                if let error = model.error {
                    Text(error).foregroundStyle(Palette.rose700).card(Palette.rose50)
                }
                if let run = model.run {
                    ResultView(run: run).transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .padding(20)
            .animation(.spring(duration: 0.45), value: model.run?.id)
        }
        .background(Palette.paper)
        .scrollDismissesKeyboard(.interactively)
    }
}

struct ResultView: View {
    let run: AnalysisRun
    @State private var shown = 0.0

    private var colors: (Color, Color) {
        switch run.analysis.verdict {
        case .safe: (Palette.mint50, Palette.mint700)
        case .suspicious: (Palette.butter50, Palette.butter700)
        case .scam: (Palette.rose50, Palette.rose700)
        }
    }

    var body: some View {
        let a = run.analysis
        let (bg, fg) = colors
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 16) {
                    ZStack {
                        Circle().trim(from: 0, to: 0.75).stroke(fg.opacity(0.15), style: StrokeStyle(lineWidth: 9, lineCap: .round)).rotationEffect(.degrees(135))
                        Circle().trim(from: 0, to: 0.75 * shown / 100).stroke(fg, style: StrokeStyle(lineWidth: 9, lineCap: .round)).rotationEffect(.degrees(135))
                        Text("\(a.riskScore)").font(.display(28)).contentTransition(.numericText())
                    }
                    .frame(width: 88, height: 88)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(a.verdict.label).font(.display(28)).foregroundStyle(fg)
                        Text(a.summary).foregroundStyle(Palette.ink)
                    }
                }
                HStack(spacing: 6) {
                    Pill(text: "\(run.source.label) · \(run.latencyMs) ms", systemImage: run.source == .onDevice ? "iphone" : "cloud",
                         bg: run.source == .onDevice ? Palette.mint100 : Palette.sky100, fg: run.source == .onDevice ? Palette.mint700 : Palette.sky600)
                    if run.redactions > 0 { Pill(text: "\(run.redactions) details hidden", systemImage: "eye.slash", bg: Palette.lilac100, fg: Palette.lilac700) }
                }
            }
            .card(bg)
            .onAppear { withAnimation(.easeOut(duration: 0.9)) { shown = Double(a.riskScore) } }

            if !a.redFlags.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Red flags").font(.headline)
                    ForEach(Array(a.redFlags.enumerated()), id: \.offset) { i, f in
                        VStack(alignment: .leading, spacing: 3) {
                            Text("\(i + 1). \(f.label)").fontWeight(.semibold)
                            if !f.evidence.isEmpty { Text("“\(f.evidence)”").font(.system(.body, design: .serif).italic()).foregroundStyle(Palette.rose700) }
                            Text(f.why).font(.footnote).foregroundStyle(Palette.ink2)
                        }
                        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Palette.rose50, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                }
                .card()
            }
            if !a.nextSteps.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("What to do now").font(.headline)
                    ForEach(a.nextSteps, id: \.self) { s in Label(s, systemImage: "checkmark.circle").foregroundStyle(Palette.ink) }
                }
                .card(Palette.mint50)
            }
            if !a.safeReply.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("IF YOU WANT TO REPLY").font(.caption2.weight(.semibold)).foregroundStyle(Palette.ink3)
                    Text(a.safeReply).font(.system(.body, design: .serif))
                    Button("Copy reply") { UIPasteboard.general.string = a.safeReply }.font(.footnote)
                }
                .card()
            }
        }
    }
}

// MARK: - History

struct HistoryView: View {
    @Environment(AppModel.self) private var model
    var body: some View {
        NavigationStack {
            List {
                if model.history.isEmpty {
                    Text("Messages you check appear here.").foregroundStyle(Palette.ink2)
                }
                ForEach(model.history) { run in
                    HStack(spacing: 12) {
                        Text("\(run.analysis.riskScore)").font(.headline).frame(width: 44, height: 44)
                            .background(tint(run.analysis.verdict).0, in: RoundedRectangle(cornerRadius: 14))
                            .foregroundStyle(tint(run.analysis.verdict).1)
                        VStack(alignment: .leading) {
                            Text(run.analysis.verdict.label).fontWeight(.semibold)
                            Text(run.input).font(.footnote).foregroundStyle(Palette.ink2).lineLimit(2)
                        }
                    }
                    .listRowBackground(Color.white)
                }
            }
            .scrollContentBackground(.hidden).background(Palette.paper)
            .navigationTitle("History")
        }
    }

    private func tint(_ v: Verdict) -> (Color, Color) {
        switch v {
        case .safe: (Palette.mint100, Palette.mint700)
        case .suspicious: (Palette.butter100, Palette.butter700)
        case .scam: (Palette.rose100, Palette.rose700)
        }
    }
}

// MARK: - Family

struct FamilyView: View {
    @Environment(AppModel.self) private var model
    @State private var name = ""
    @State private var code = ""
    @State private var signedIn = Repository.uid != nil
    @State private var circles: [CircleInfo] = []
    @State private var alerts: [String: [CircleAlert]] = [:]
    @State private var listeners: [ListenerRegistration] = []
    @State private var message: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("LOOK OUT FOR EACH OTHER").font(.caption2.weight(.semibold)).foregroundStyle(Palette.ink3)
                Text("Family Circle").font(.display(34))
                if !Repository.isConfigured {
                    Text("Add GoogleService-Info.plist to turn on Family Circle.").card(Palette.sky100)
                } else if !signedIn {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("What should your family call you?").font(.display(22))
                        TextField("e.g. Grandma Rose", text: $name).textFieldStyle(.roundedBorder)
                        Button("Continue") {
                            Task { do { try await Repository.signIn(name: name); signedIn = true; listen() } catch { message = error.localizedDescription } }
                        }
                        .buttonStyle(.borderedProminent).tint(Palette.ink).disabled(name.isEmpty)
                    }
                    .card(Color(hex: 0xFFF6EE))
                } else {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Join a circle").font(.headline)
                        Text("Create circles in the web app, then enter the 6-letter code.").font(.footnote).foregroundStyle(Palette.ink2)
                        HStack {
                            TextField("ABC123", text: $code).textFieldStyle(.roundedBorder).textInputAutocapitalization(.characters).font(.system(.body, design: .monospaced))
                            Button("Join") {
                                Task { do { try await Repository.join(code: code); message = "Joined!"; code = "" } catch { message = "No circle with that code" } }
                            }
                            .buttonStyle(.borderedProminent).tint(Palette.lilac500).disabled(code.count != 6)
                        }
                    }
                    .card(Palette.lilac50)
                    ForEach(circles) { c in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(c.name).font(.display(22))
                                Spacer()
                                Text("\(c.memberCount) members").font(.caption).foregroundStyle(Palette.ink3)
                            }
                            if let run = model.run {
                                Button { Task { try? await Repository.share(run, to: c.id, note: "Watch out for this one"); message = "Shared with \(c.name)" } } label: {
                                    Label("Share my last check here", systemImage: "square.and.arrow.up")
                                }
                                .buttonStyle(.bordered)
                            }
                            ForEach(alerts[c.id] ?? []) { a in
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text(a.byName).fontWeight(.semibold)
                                        Spacer()
                                        Pill(text: "\(a.verdict) · \(a.riskScore)", bg: a.verdict == "scam" ? Palette.rose50 : Palette.butter50, fg: a.verdict == "scam" ? Palette.rose700 : Palette.butter700)
                                    }
                                    if !a.note.isEmpty { Text(a.note).font(.system(.body, design: .serif)) }
                                    Text(a.excerpt).font(.footnote.monospaced()).foregroundStyle(Palette.ink2)
                                }
                                .card()
                            }
                        }
                    }
                }
                if let message { Text(message).foregroundStyle(Palette.ink2) }
            }
            .padding(20)
        }
        .background(Palette.paper)
        .onAppear { if signedIn { listen() } }
        .onDisappear { listeners.forEach { $0.remove() }; listeners = [] }
    }

    private func listen() {
        guard listeners.isEmpty, let reg = Repository.listenCircles({ list in
            circles = list
            for c in list where alerts[c.id] == nil {
                alerts[c.id] = []
                listeners.append(Repository.listenAlerts(c.id) { alerts[c.id] = $0 })
            }
        }) else { return }
        listeners.append(reg)
    }
}

// MARK: - Engine

struct EngineView: View {
    @Environment(AppModel.self) private var model
    var body: some View {
        @Bindable var model = model
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("UNDER THE HOOD").font(.caption2.weight(.semibold)).foregroundStyle(Palette.ink3)
                Text("Engine").font(.display(34))
                VStack(alignment: .leading, spacing: 6) {
                    Text(model.deviceState.title).font(.display(24))
                    Text(detail).foregroundStyle(Palette.ink2)
                    if model.deviceState == .notEnabled, let url = URL(string: UIApplication.openSettingsURLString) {
                        Link("Open Settings", destination: url).padding(.top, 6)
                    }
                }
                .card(model.deviceState == .available ? Palette.mint100 : Palette.butter100)

                VStack(alignment: .leading, spacing: 8) {
                    Text("How a message is routed").font(.headline)
                    Label("Instant pattern checks run locally", systemImage: "bolt")
                    Label("Apple Foundation Model on device (Private, Smart)", systemImage: "iphone")
                    Label("Personal details removed locally", systemImage: "eye.slash")
                    Label("Gemini via Firebase AI Logic (fallback, Most accurate)", systemImage: "cloud")
                }
                .foregroundStyle(Palette.ink).card()

                Toggle(isOn: $model.redact) {
                    VStack(alignment: .leading) {
                        Text("Remove personal details before using the cloud")
                        Text("Numbers, emails, cards and codes become placeholders on your iPhone.").font(.caption).foregroundStyle(Palette.ink3)
                    }
                }
                .tint(Palette.lilac500).card()
            }
            .padding(20)
        }
        .background(Palette.paper)
    }

    private var detail: String {
        switch model.deviceState {
        case .available: "Checks run on this iPhone, privately, even offline."
        case .notEnabled: "Turn on Apple Intelligence in Settings to check messages on-device."
        case .downloading: "The on-device model is downloading. Smart mode uses the cloud with redaction until then."
        case .unsupported(let why): "\(why). Chaukas uses the cloud with personal details removed."
        }
    }
}

/// Simple wrapping layout for chips.
struct FlowLayout: Layout {
    var spacing: CGFloat = 6
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) -> CGSize {
        let width = proposal.width ?? 320
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x + size.width > width { x = 0; y += rowH + spacing; rowH = 0 }
            x += size.width + spacing
            rowH = max(rowH, size.height)
        }
        return CGSize(width: width, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal _: ProposedViewSize, subviews: Subviews, cache _: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX { x = bounds.minX; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowH = max(rowH, size.height)
        }
    }
}
