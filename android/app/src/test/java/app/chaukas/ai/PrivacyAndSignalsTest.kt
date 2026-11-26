/**
 * JVM unit tests for redaction, scam signals and model-output parsing.
 */
package app.chaukas.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {
    private fun hidden(input: String, vararg secrets: String) {
        val r = Redactor.redact(input)
        secrets.forEach { assertFalse("'$it' leaked in: ${r.text}", r.text.contains(it)) }
    }

    @Test fun hidesEmailsCardsAndCodes() =
        hidden("code is 482913, card 4111 1111 1111 1111, mail rose.k@example.com", "482913", "4111 1111 1111 1111", "rose.k@example.com")

    @Test fun hidesIndianIdentifiers() {
        hidden("Call +91 98765 43210", "98765 43210")
        hidden("call 9876543210", "9876543210")
        hidden("pay rahul.k@okaxis", "rahul.k@okaxis")
        hidden("PAN ABCDE1234F", "ABCDE1234F")
        hidden("Aadhaar 2345 6789 0123", "2345 6789 0123")
        hidden("482913 is your OTP", "482913")
    }

    @Test fun keepsOrdinaryNumbers() {
        assertEquals(0, Redactor.redact("Pay Rs 4,999 by 12/11/2026, order #4521").count)
    }

    @Test fun dropsQueryStringsAndRestores() {
        val r = Redactor.redact("Go to https://x.top/pay?id=88213 or email a.b@example.com", listOf("Priya"))
        assertFalse(r.text.contains("88213"))
        assertEquals("Reply to a.b@example.com", Redactor.restore("Reply to [EMAIL_1]", r.map))
    }

    @Test fun hidesPrivateTerms() {
        assertEquals("Hi [NAME_1]", Redactor.redact("Hi Priya", listOf("Priya")).text)
    }
}

class SignalsTest {
    @Test fun scoresScamHighAndChatLow() {
        assertTrue(Signals.compute("URGENT: account suspended. Buy gift cards, send the OTP: bit.ly/x").second > 70)
        assertTrue(Signals.compute("Lunch tomorrow at 1pm?").second < 10)
    }

    @Test fun catchesIndianScams() {
        val ids = Signals.compute("Your KYC is expired, account blocked. Enter your UPI PIN. upi://pay?pa=a@ybl").first.map { it.id }
        assertTrue(ids.containsAll(listOf("india_kyc", "upi_pin", "upi_qr")))
    }

    @Test fun catchesScamKeywordsAndRechargeAndChallan() {
        val (scamSignals, scamScore) = Signals.compute("This is a scam message from unknown")
        assertTrue(scamSignals.any { it.id == "scam_keyword" })
        assertTrue(scamScore >= 65)

        val (rechargeSignals, rechargeScore) = Signals.compute("Free 5G recharge offer click here")
        assertTrue(rechargeSignals.any { it.id == "recharge" })
        assertTrue(rechargeScore >= 65)

        val (challanSignals, challanScore) = Signals.compute("Pending e-challan traffic fine parivahan")
        assertTrue(challanSignals.any { it.id == "traffic_challan" })
        assertTrue(challanScore >= 65)
    }
}

class AnalysisParserTest {
    @Test fun repairsContradictoryScore() {
        val a = AnalysisParser.parse("```json\n{\"verdict\":\"scam\",\"riskScore\":10}\n```")
        assertEquals(Verdict.SCAM, a.verdict)
        assertEquals(70, a.riskScore)
    }

    @Test fun missingVerdictNeverDefaultsToSafe() {
        assertEquals(Verdict.SUSPICIOUS, AnalysisParser.parse("{\"summary\":\"x\"}").verdict)
    }
}
