import Foundation

/// Swift port of web/src/ai/redact.ts: the same privacy rules on every platform.
struct Redaction {
    var text: String
    var map: [String: String]
    var count: Int { map.count }
}

enum Redactor {
    private static func regex(_ p: String, _ opts: NSRegularExpression.Options = [.caseInsensitive]) -> NSRegularExpression {
        try! NSRegularExpression(pattern: p, options: opts)
    }

    private static let urlQuery = regex(#"\b(https?://[^\s?#]+)[?#]\S*"#)
    private static let email = regex(#"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}"#)
    private static let card = regex(#"\b(?:\d[ -]?){13,19}\b"#)
    private static let ssn = regex(#"\b\d{3}-\d{2}-\d{4}\b"#)
    private static let code = regex(#"\b(?:code|otp|pin|passcode|verification)\b[^\d\n]{0,20}(\d{4,8})\b"#)
    private static let upi = regex(#"\b[\w.-]{2,}@[a-z][a-z0-9]{1,15}\b(?![.@\w])"#)
    private static let pan = regex(#"\b[A-Z]{5}\d{4}[A-Z]\b"#, [])
    private static let aadhaar = regex(#"\b[2-9]\d{3}[\s-]?\d{4}[\s-]?\d{4}\b"#)
    private static let codeBefore = regex(#"\b(\d{4,8})\b(?=[\s.,:-]*(?:is|as)\s+(?:your|the)\s+(?:otp|code|one[- ]?time|verification|pin|passcode))"#)
    private static let indianMobile = regex(#"(?:\+91[\s-]?|\b0|\b)[6-9]\d{4}[\s-]?\d{5}\b"#)
    private static let account = regex(#"\b(?:acct|account|a/c)\.?\s*(?:no\.?|number|#)?\s*[:#]?\s*(\d{6,})\b"#)
    private static let phone = regex(#"(?:\+?\d{1,3}[\s.-]?)?(?:\(\d{2,4}\)|\d{2,4})[\s.-]?\d{3,4}[\s.-]?\d{3,4}\b"#)

    private static func luhn(_ s: String) -> Bool {
        let d = s.compactMap { $0.wholeNumberValue }
        guard (13 ... 19).contains(d.count) else { return false }
        var sum = 0
        for (i, n) in d.reversed().enumerated() {
            var v = n
            if i % 2 == 1 { v *= 2; if v > 9 { v -= 9 } }
            sum += v
        }
        return sum % 10 == 0
    }

    /// Replaces matches (or a capture group) from the end so ranges stay valid.
    private static func replace(_ re: NSRegularExpression, in text: String, group: Int = 0, _ transform: (String) -> String?) -> String {
        var out = text
        let ns = text as NSString
        for m in re.matches(in: text, range: NSRange(location: 0, length: ns.length)).reversed() {
            let r = m.range(at: group)
            guard r.location != NSNotFound, let swiftRange = Range(r, in: out) else { continue }
            if let rep = transform(String(out[swiftRange])) { out.replaceSubrange(swiftRange, with: rep) }
        }
        return out
    }

    static func redact(_ input: String, privateTerms: [String] = []) -> Redaction {
        var map: [String: String] = [:]
        var reverse: [String: String] = [:]
        var counters: [String: Int] = [:]
        func tag(_ kind: String, _ value: String) -> String {
            if let t = reverse[value] { return t }
            counters[kind, default: 0] += 1
            let t = "[\(kind)_\(counters[kind]!)]"
            map[t] = value
            reverse[value] = t
            return t
        }

        var text = urlQuery.stringByReplacingMatches(in: input, range: NSRange(input.startIndex..., in: input), withTemplate: "$1?[QUERY_REMOVED]")
        for term in privateTerms.map({ $0.trimmingCharacters(in: .whitespaces) }) where term.count >= 2 {
            text = replace(regex("\\b\(NSRegularExpression.escapedPattern(for: term))\\b"), in: text) { tag("NAME", $0) }
        }
        text = replace(email, in: text) { tag("EMAIL", $0) }
        text = replace(card, in: text) { luhn($0) ? tag("CARD", $0.trimmingCharacters(in: .whitespaces)) : nil }
        text = replace(ssn, in: text) { tag("SSN", $0) }
        text = replace(upi, in: text) { tag("UPI", $0) }
        text = replace(pan, in: text) { tag("PAN", $0) }
        text = replace(aadhaar, in: text) { tag("AADHAAR", $0) }
        text = replace(codeBefore, in: text, group: 1) { tag("CODE", $0) }
        text = replace(code, in: text, group: 1) { tag("CODE", $0) }
        text = replace(indianMobile, in: text) { tag("PHONE", $0) }
        text = replace(phone, in: text) { m in
            m.hasPrefix("[") || m.filter(\.isNumber).count < 9 ? nil : tag("PHONE", m.trimmingCharacters(in: .whitespaces))
        }
        text = replace(account, in: text, group: 1) { tag("ACCOUNT", $0) }
        return Redaction(text: text, map: map)
    }

    static func restore(_ text: String, map: [String: String]) -> String {
        map.reduce(text) { $0.replacingOccurrences(of: $1.key, with: $1.value) }
    }
}
