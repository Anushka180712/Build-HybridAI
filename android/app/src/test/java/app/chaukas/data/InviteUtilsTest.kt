package app.chaukas.data

import app.chaukas.ai.Analysis
import app.chaukas.ai.AnalysisRun
import app.chaukas.ai.RedFlag
import app.chaukas.ai.Source
import app.chaukas.ai.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteUtilsTest {

    @Test
    fun toLatinTransliteratesIndianNames() {
        assertEquals("nani", InviteUtils.toLatin("नानी"))
        assertEquals("papa", InviteUtils.toLatin("पापा"))
        assertEquals("rajesh", InviteUtils.toLatin("राजेश"))
    }

    @Test
    fun makeHandleGeneratesValidFormat() {
        val handle = InviteUtils.makeHandle("Papa", "1234")
        assertEquals("papa-1234", handle)
        assertTrue(Regex(InviteUtils.HANDLE_RE).matches(handle))
        assertTrue(InviteUtils.handleFitsName(handle, "Papa"))
    }

    @Test
    fun normalizeHandleCleansInput() {
        assertEquals("riya-4821", InviteUtils.normalizeHandle("@riya-4821"))
        assertEquals("riya-4821", InviteUtils.normalizeHandle("https://chaukas.app/#/circle/@riya-4821"))
        assertEquals("riya-4821", InviteUtils.normalizeHandle("  riya-4821  "))
        assertEquals("", InviteUtils.normalizeHandle("invalid handle"))
    }

    @Test
    fun inspectQrIdentifiesUpiAndExtractsDetails() {
        val upi = "upi://pay?pa=scam@bank&pn=Flipkart%20Refund&am=4999.00&tn=Refund"
        val finding = InviteUtils.inspectQr(upi)
        assertEquals("upi", finding.kind)
        assertTrue(finding.headline.contains("Flipkart Refund"))
        assertTrue(finding.warnings.any { it.contains("always SENDS money") })
        assertTrue(finding.warnings.any { it.contains("₹4999.00") })
    }

    @Test
    fun inspectQrIdentifiesUrl() {
        val url = "https://sbi-fake-login.xyz/verify"
        val finding = InviteUtils.inspectQr(url)
        assertEquals("url", finding.kind)
        assertTrue(finding.headline.contains("sbi-fake-login.xyz"))
    }

    @Test
    fun defangMakesLinksSafe() {
        val raw = "Visit https://bad.site.com/login and http://evil.org"
        val safe = InviteUtils.defang(raw)
        assertFalse(safe.contains("bad.site.com"))
        assertTrue(safe.contains("bad[.]site[.]com"))
        assertTrue(safe.contains("evil[.]org"))
    }

    @Test
    fun alertForwardTextIncludesDetails() {
        val text = InviteUtils.alertForwardText(
            verdict = "scam",
            riskScore = 85,
            category = "phishing",
            summary = "A fake electricity bill SMS",
            excerpt = "Pay bill at http://bad.com",
            byName = "Dad",
        )
        assertTrue(text.contains("Likely scam"))
        assertTrue(text.contains("85/100"))
        assertTrue(text.contains("Dad"))
        assertTrue(text.contains("1930"))
        assertTrue(text.contains("bad[.]com"))
    }

    @Test
    fun qrEncoderProducesMatrix() {
        val matrix = QrEncoder.encode("https://chaukas.app/#/circle/@riya-4821")
        assertTrue(matrix.isNotEmpty())
        assertEquals(matrix.size, matrix[0].size)
        // Finder pattern top-left should have black corners
        assertTrue(matrix[0][0])
        assertTrue(matrix[0][6])
        assertTrue(matrix[6][0])
        assertTrue(matrix[6][6])
        // Finder pattern center
        assertTrue(matrix[3][3])
    }
}
