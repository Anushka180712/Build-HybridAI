// Plain-Swift checks for the platform-independent core (redaction, signals, parsing).
// Run in CI with: swiftc Chaukas/Analysis.swift Chaukas/Signals.swift Chaukas/Redactor.swift Tests/main.swift
import Foundation

var failures = 0
func check(_ ok: Bool, _ name: String) {
    print(ok ? "PASS  \(name)" : "FAIL  \(name)")
    if !ok { failures += 1 }
}
func hides(_ input: String, _ secret: String) -> Bool { !Redactor.redact(input).text.contains(secret) }

check(hides("code is 482913", "482913"), "hides one-time codes")
check(hides("482913 is your OTP", "482913"), "hides codes written before the keyword")
check(hides("card 4111 1111 1111 1111", "4111 1111 1111 1111"), "hides card numbers")
check(hides("mail rose.k@example.com", "rose.k@example.com"), "hides emails")
check(hides("Call +91 98765 43210", "98765 43210"), "hides Indian mobiles")
check(hides("pay rahul.k@okaxis", "rahul.k@okaxis"), "hides UPI IDs")
check(hides("PAN ABCDE1234F", "ABCDE1234F"), "hides PAN")
check(hides("Aadhaar 2345 6789 0123", "2345 6789 0123"), "hides Aadhaar")
check(hides("open https://x.top/pay?id=88213", "88213"), "drops query strings")
check(Redactor.redact("Pay Rs 4,999 by 12/11/2026, order #4521").count == 0, "keeps ordinary numbers")
check(Redactor.redact("Hi Priya", privateTerms: ["Priya"]).text == "Hi [NAME_1]", "hides private terms")
let r = Redactor.redact("email a.b@example.com")
check(Redactor.restore("Reply to [EMAIL_1]", map: r.map) == "Reply to a.b@example.com", "restores placeholders")

check(Signals.compute("URGENT: account suspended. Buy gift cards, send the OTP: bit.ly/x").score > 70, "scores scams high")
check(Signals.compute("Lunch tomorrow at 1pm?").score < 10, "scores chat low")
let ids = Set(Signals.compute("Your KYC is expired, account blocked. Enter your UPI PIN. upi://pay?pa=a@ybl").signals.map(\.id))
check(ids.isSuperset(of: ["india_kyc", "upi_pin", "upi_qr"]), "catches Indian scam patterns")

if let a = try? AnalysisParser.parse("```json\n{\"verdict\":\"scam\",\"riskScore\":10}\n```") {
    check(a.verdict == .scam && a.riskScore == 70, "repairs contradictory score")
} else { check(false, "parses fenced JSON") }
check((try? AnalysisParser.parse("{\"summary\":\"x\"}"))?.verdict == .suspicious, "missing verdict never defaults to safe")

print(failures == 0 ? "All checks passed" : "\(failures) check(s) failed")
exit(failures == 0 ? 0 : 1)
