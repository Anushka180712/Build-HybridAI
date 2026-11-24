import Foundation

/// Instant local pattern checks, mirroring web/src/ai/signals.ts.
struct Signal: Identifiable, Hashable {
    let id: String
    let label: String
    let weight: Int
    let matches: [String]
}

enum Signals {
    private static let rules: [(String, String, Int, String)] = [
        ("urgency", "Pressure to act fast", 18, #"\b(urgent(ly)?|immediately|right away|within \d+ ?(hours?|hrs?|minutes?)|final (notice|warning)|act now|last chance)\b"#),
        ("threat", "Threat or penalty", 16, #"\b(suspend(ed)?|locked|deactivat(ed|ion)|arrest|warrant|legal action|penalt(y|ies)|fine[sd]?)\b"#),
        ("odd_payment", "Unusual way to pay", 26, #"\b(gift ?cards?|bitcoin|btc|usdt|crypto|wire transfer|western union|zelle|cash ?app)\b"#),
        ("credentials", "Asks for codes or passwords", 24, #"\b(password|one[- ]time (code|password)|otp|verification code|security code|pin|ssn)\b"#),
        ("prize", "Too good to be true", 18, #"\b(you('ve| have)? won|winner|congratulations|claim (your|now)|prize|lottery)\b"#),
        ("delivery", "Parcel or toll fee", 14, #"\b(parcel|package|redeliver(y)?|customs fee|toll|unpaid (fee|balance|toll))\b"#),
        ("family", "\"New number\" family story", 20, #"\b(hi (mum|mom|dad)|new number|lost my phone|broke my phone)\b"#),
        ("job_scam", "Easy-money job offer", 24, #"\b(part[- ]time (job|role|work)|work from home|rating products|like (videos|posts) and earn|no experience (needed|required)|daily (income|payout)|task[- ]based (job|work))"#),
        ("off_platform", "Moves you to another app", 16, #"\b(add me on (telegram|whatsapp|signal)|telegram @?\w+|contact (me|us) on telegram|t\.me/\w+)"#),
        ("india_kyc", "KYC, PAN or Aadhaar \"update\"", 22, #"\b(kyc|e-?kyc|pan( card)?|aadhaa?r)\b.{0,40}\b(update|expired?|blocked?|suspend|verify|link)"#),
        ("upi_pin", "UPI PIN or collect request", 26, #"\b(upi pin|collect request|enter (your )?pin|scan (this|the) qr|sent .{0,20} by mistake)\b"#),
        ("upi_qr", "Payment QR code", 54, #"upi://pay\S*"#),
        ("digital_arrest", "\"Digital arrest\" or police threat", 26, #"\b(digital arrest|cbi|narcotics|customs (department|officer)|trai|your (number|sim) will be (blocked|disconnected)|money laundering)\b"#),
        ("power_cut", "Power cut threat", 20, #"\b(electricity|power|light) (will be |connection will be )?(disconnected|cut)\b"#),
        ("remote", "Remote access request", 22, #"\b(anydesk|teamviewer|remote access|virus (detected|found))\b"#),
        ("links", "Suspicious link", 26, #"(https?://\S+\.(top|xyz|icu|click|live|shop|vip|online|site)\b\S*|bit\.ly/\S+|tinyurl\.com/\S+)"#),
    ]

    static func compute(_ text: String) -> (signals: [Signal], score: Int) {
        let ns = text as NSString
        let found: [Signal] = rules.compactMap { id, label, weight, pattern in
            guard let re = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else { return nil }
            let matches = re.matches(in: text, range: NSRange(location: 0, length: ns.length)).map { ns.substring(with: $0.range) }
            let unique = Array(NSOrderedSet(array: matches)) as? [String] ?? matches
            return unique.isEmpty ? nil : Signal(id: id, label: label, weight: weight, matches: unique)
        }
        let raw = Double(found.reduce(0) { $0 + $1.weight })
        return (found, Int(100 * (1 - exp(-raw / 45))))
    }
}
