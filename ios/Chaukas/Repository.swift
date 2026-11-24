// Firestore and Remote Config access using the same redacted schema as the web and Android
// apps.

import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import FirebaseRemoteConfig
import Foundation

struct CircleAlert: Identifiable, Hashable {
    let id: String
    let byName: String
    let verdict: String
    let riskScore: Int
    let excerpt: String
    let note: String
}

struct CircleInfo: Identifiable, Hashable {
    let id: String
    let name: String
    let memberCount: Int
}

/// Same Firestore schema and Remote Config keys as the web and Android apps.
enum Repository {
    static var isConfigured: Bool { FirebaseApp.app() != nil }
    static var uid: String? { isConfigured ? Auth.auth().currentUser?.uid : nil }
    private static var db: Firestore { Firestore.firestore() }

    static func signIn(name: String) async throws {
        let user: User
        if let current = Auth.auth().currentUser { user = current } else { user = try await Auth.auth().signInAnonymously().user }
        if !name.isEmpty {
            let change = user.createProfileChangeRequest()
            change.displayName = String(name.prefix(40))
            try await change.commitChanges()
        }
    }

    private static func excerpt(_ text: String, _ n: Int) -> String {
        let t = Redactor.redact(text).text.split(whereSeparator: \.isWhitespace).joined(separator: " ")
        return t.count > n ? String(t.prefix(n - 1)) + "…" : t
    }

    static func save(_ run: AnalysisRun) {
        guard let uid else { return }
        db.collection("users").document(uid).collection("checks").document(run.id).setData([
            "id": run.id,
            "verdict": run.analysis.verdict.rawValue,
            "riskScore": run.analysis.riskScore,
            "category": String(run.analysis.category.prefix(40)),
            "summary": String(Redactor.redact(run.analysis.summary).text.prefix(400)),
            "excerpt": excerpt(run.input, 160),
            "source": run.source.rawValue,
            "createdAt": Int(run.createdAt.timeIntervalSince1970 * 1000),
            "syncedAt": FieldValue.serverTimestamp(),
        ])
    }

    static func listenCircles(_ cb: @escaping ([CircleInfo]) -> Void) -> ListenerRegistration? {
        guard let uid else { return nil }
        return db.collection("circles").whereField("members", arrayContains: uid).addSnapshotListener { snap, _ in
            cb(snap?.documents.map { d in
                CircleInfo(id: d.documentID, name: d.get("name") as? String ?? "Family", memberCount: (d.get("members") as? [String])?.count ?? 1)
            } ?? [])
        }
    }

    static func listenAlerts(_ circleId: String, _ cb: @escaping ([CircleAlert]) -> Void) -> ListenerRegistration {
        db.collection("circles").document(circleId).collection("alerts").order(by: "createdAt", descending: true).limit(to: 50)
            .addSnapshotListener { snap, _ in
                cb(snap?.documents.map { d in
                    CircleAlert(id: d.documentID, byName: d.get("byName") as? String ?? "Someone", verdict: d.get("verdict") as? String ?? "",
                                riskScore: d.get("riskScore") as? Int ?? 0, excerpt: d.get("excerpt") as? String ?? "", note: d.get("note") as? String ?? "")
                } ?? [])
            }
    }

    static func join(code: String) async throws {
        guard let uid else { return }
        let clean = code.uppercased().filter { $0.isLetter || $0.isNumber }
        try await db.collection("circles").document(clean).updateData([
            "members": FieldValue.arrayUnion([uid]),
            "memberNames.\(uid)": Auth.auth().currentUser?.displayName ?? "Member",
        ])
    }

    static func share(_ run: AnalysisRun, to circleId: String, note: String) async throws {
        guard let uid else { return }
        try await db.collection("circles").document(circleId).collection("alerts").addDocument(data: [
            "byUid": uid,
            "byName": Auth.auth().currentUser?.displayName ?? "Someone",
            "verdict": run.analysis.verdict.rawValue,
            "riskScore": run.analysis.riskScore,
            "category": String(run.analysis.category.prefix(40)),
            "summary": String(Redactor.redact(run.analysis.summary).text.prefix(400)),
            "excerpt": excerpt(run.input, 220),
            "note": String(note.prefix(280)),
            "createdAt": FieldValue.serverTimestamp(),
            // Group warnings expire after 3 days (Firestore TTL policy on expireAt).
            "expireAt": Timestamp(date: Date().addingTimeInterval(3 * 24 * 60 * 60)),
            "reactions": [String: String](),
        ])
    }

    static func loadRemoteConfig() async -> (cloudModel: String, banner: String) {
        guard isConfigured else { return ("gemini-3.5-flash-lite", "") }
        let rc = RemoteConfig.remoteConfig()
        rc.setDefaults(["cloud_model": "gemini-3.5-flash-lite" as NSString, "trend_banner": "" as NSString])
        _ = try? await rc.fetchAndActivate()
        let model = rc.configValue(forKey: "cloud_model").stringValue
        return (model.isEmpty ? "gemini-3.5-flash-lite" : model, rc.configValue(forKey: "trend_banner").stringValue)
    }
}
