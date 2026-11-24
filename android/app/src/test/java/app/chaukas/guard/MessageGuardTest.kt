/**
 * JVM unit tests for the auto-check: which notifications warn, which stay quiet.
 */
package app.chaukas.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageGuardTest {
    private val whatsapp = "com.whatsapp"

    @Test fun warnsOnKycScam() {
        val a = MessageGuard.assess(whatsapp, "+91 70123 44821", "Your KYC is expired, account will be blocked today. Update now: http://sbi-kyc.top/verify")
        assertNotNull(a)
        assertTrue(a!!.score >= MessageGuard.THRESHOLD)
        assertEquals(2, a.reasons.size)
    }

    @Test fun quietOnNormalChat() {
        assertNull(MessageGuard.assess(whatsapp, "Maa", "Dinner at 8? Bring curd on the way home."))
    }

    @Test fun ignoresUnwatchedApps() {
        assertNull(MessageGuard.assess("com.example.game", "", "URGENT: your account is suspended, share OTP now at http://x.top"))
    }

    @Test fun watchesTheDefaultSmsApp() {
        val text = "URGENT: electricity will be disconnected tonight. Call 9876543210 immediately"
        assertNull(MessageGuard.assess("com.oem.sms", "", text))
        assertNotNull(MessageGuard.assess("com.oem.sms", "", text, setOf("com.oem.sms")))
    }

    @Test fun ignoresSummaries() {
        assertNull(MessageGuard.assess(whatsapp, "WhatsApp", "5 new messages from 2 chats"))
    }

    @Test fun keyIsStable() {
        assertEquals(MessageGuard.key(whatsapp, "hello there "), MessageGuard.key(whatsapp, "hello there"))
    }

    @Test fun normalChatIsCheckedButNotWarned() {
        // Counted as checked (so it shows as "Looks fine"), but no warning.
        assertTrue(MessageGuard.checkable(whatsapp, "Dinner at 8? Bring curd on the way home."))
        assertNull(MessageGuard.assess(whatsapp, "Maa", "Dinner at 8? Bring curd on the way home."))
    }

    @Test fun summariesAreNotChecked() {
        assertTrue(!MessageGuard.checkable(whatsapp, "3 new messages from 2 chats"))
        assertTrue(!MessageGuard.checkable("com.example.game", "Dinner at 8? Bring curd on the way home."))
    }

    @Test fun sortsAppsIntoSmsAndWhatsApp() {
        assertEquals("chat", MessageGuard.kind("com.whatsapp"))
        assertEquals("chat", MessageGuard.kind("com.whatsapp.w4b"))
        assertEquals("sms", MessageGuard.kind("com.google.android.apps.messaging"))
        assertEquals("sms", MessageGuard.kind("com.oem.sms", setOf("com.oem.sms")))
        assertNull(MessageGuard.kind("com.example.game"))
    }

    @Test fun shortMessagesAreCountedButNeverWarned() {
        assertTrue(MessageGuard.checkable(whatsapp, "hi"))
        assertNull(MessageGuard.assess(whatsapp, "Maa", "OTP now!"))
    }
}
