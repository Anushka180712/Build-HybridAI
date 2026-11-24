// Share extension: "Chaukas" in the iOS share sheet. Gives an instant on-device verdict from
// the local scam signals, then hands the message to the app for the full hybrid check.

import SwiftUI
import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        Task { @MainActor in
            let text = await sharedText()
            let host = UIHostingController(rootView: ShareCard(text: text, close: { [weak self] in self?.finish() }, open: { [weak self] in self?.openInApp(text) }))
            addChild(host)
            host.view.frame = view.bounds
            host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            view.addSubview(host.view)
            host.didMove(toParent: self)
        }
    }

    /// Plain text first (a selected message), then a shared link.
    private func sharedText() async -> String {
        let providers = (extensionContext?.inputItems as? [NSExtensionItem])?.flatMap { $0.attachments ?? [] } ?? []
        for type in [UTType.plainText, UTType.url] {
            for provider in providers where provider.hasItemConformingToTypeIdentifier(type.identifier) {
                if let item = try? await provider.loadItem(forTypeIdentifier: type.identifier) {
                    if let s = item as? String { return s }
                    if let u = item as? URL { return u.absoluteString }
                }
            }
        }
        return ""
    }

    /// Opens chaukas://check?text=… in the app. Extensions have no UIApplication.shared, so the
    /// call goes up the responder chain. If that is not possible the text is copied instead.
    private func openInApp(_ text: String) {
        var c = URLComponents()
        c.scheme = "chaukas"
        c.host = "check"
        c.queryItems = [URLQueryItem(name: "text", value: String(text.prefix(4000)))]
        guard let url = c.url else { return finish() }
        var responder: UIResponder? = self
        while let r = responder {
            if let app = r as? UIApplication {
                app.open(url, options: [:], completionHandler: nil)
                return finish()
            }
            responder = r.next
        }
        UIPasteboard.general.string = text
        finish()
    }

    private func finish() {
        extensionContext?.completeRequest(returningItems: nil)
    }
}

struct ShareCard: View {
    let text: String
    let close: () -> Void
    let open: () -> Void

    var body: some View {
        let result = Signals.compute(text)
        let (title, tint, ink): (String, Color, Color) =
            result.score >= 70 ? ("Looks like a scam", Palette.rose100, Palette.rose700)
            : result.score >= 45 ? ("Be careful", Palette.butter100, Palette.butter700)
            : ("No scam signs found", Palette.mint100, Palette.mint700)
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Chaukas").font(.display(24))
                Spacer()
                Button("Done", action: close)
            }
            if text.isEmpty {
                Text("Share a message's text to check it.").foregroundStyle(Palette.ink2)
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    Text(title).font(.title3.weight(.semibold)).foregroundStyle(ink)
                    ForEach(result.signals.sorted { $0.weight > $1.weight }.prefix(3)) { s in
                        Label(s.label, systemImage: "exclamationmark.triangle").font(.subheadline).foregroundStyle(ink)
                    }
                    if result.signals.isEmpty {
                        Text("Quick check only. The full check reads the whole message.").font(.subheadline).foregroundStyle(ink)
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(tint, in: RoundedRectangle(cornerRadius: 20))
                Text(text).font(.footnote).foregroundStyle(Palette.ink3).lineLimit(4)
                Button(action: open) {
                    Text("Full check in Chaukas").frame(maxWidth: .infinity).padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .tint(Palette.lilac500)
                Text("This quick check ran on your iPhone. Nothing was sent.").font(.caption).foregroundStyle(Palette.ink3)
            }
            Spacer()
        }
        .padding(20)
        .background(Palette.paper)
        .preferredColorScheme(.light)
    }
}
