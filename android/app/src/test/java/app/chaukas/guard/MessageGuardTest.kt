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
        assertEquals("chat", MessageGuard.kind("com.whatsapp.clone"))
        assertEquals("sms", MessageGuard.kind("com.google.android.apps.messaging"))
        assertEquals("sms", MessageGuard.kind("com.oneplus.mms"))
        assertEquals("sms", MessageGuard.kind("com.coloros.mms"))
        assertEquals("sms", MessageGuard.kind("com.vivo.mms"))
        assertEquals("sms", MessageGuard.kind("com.oem.sms", setOf("com.oem.sms")))
        assertNull(MessageGuard.kind("com.example.game"))
    }

    @Test fun testScamMessagesAreDetected() {
        val smsScam = Guard.TEST_SCAM_SMS
        val chatScam = Guard.TEST_SCAM_CHAT
        val smsAlert = MessageGuard.assess("com.google.android.apps.messaging", "BP-DHBVN", smsScam)
        assertNotNull(smsAlert)
        assertTrue(smsAlert!!.score >= 70)
        assertTrue(smsAlert.reasons.isNotEmpty())

        val chatAlert = MessageGuard.assess(whatsapp, "+91 98765 43210", chatScam)
        assertNotNull(chatAlert)
        assertTrue(chatAlert!!.score >= 70)
        assertTrue(chatAlert.reasons.isNotEmpty())
    }

    @Test fun isSummaryHelperIdentifiesSummaries() {
        assertTrue(MessageGuard.isSummary("2 new messages"))
        assertTrue(MessageGuard.isSummary("5 new messages from 2 chats"))
        assertTrue(MessageGuard.isSummary("1 new message"))
        assertTrue(!MessageGuard.isSummary("Your electricity will be disconnected tonight"))
    }

    @Test fun shortMessagesAreCountedButNeverWarned() {
        assertTrue(MessageGuard.checkable(whatsapp, "hi"))
        assertNull(MessageGuard.assess(whatsapp, "Maa", "OTP now!"))
    }

    @Test fun identifiesUnsavedSendersVersusSavedContacts() {
        // Unknown / unsaved phone numbers
        assertTrue(MessageGuard.isUnsavedSender("+91 98765 43210"))
        assertTrue(MessageGuard.isUnsavedSender("+919876543210"))
        assertTrue(MessageGuard.isUnsavedSender("9876543210"))
        assertTrue(MessageGuard.isUnsavedSender("+1 (555) 234-5678"))
        assertTrue(MessageGuard.isUnsavedSender("+44 7911 123456"))

        // SMS telecom sender headers & shortcodes
        assertTrue(MessageGuard.isUnsavedSender("BP-DHBVN"))
        assertTrue(MessageGuard.isUnsavedSender("VK-SBIINB"))
        assertTrue(MessageGuard.isUnsavedSender("AX-AIRTEL"))
        assertTrue(MessageGuard.isUnsavedSender("567676"))
        assertTrue(MessageGuard.isUnsavedSender("1901"))

        // Blank or explicit unknown
        assertTrue(MessageGuard.isUnsavedSender(""))
        assertTrue(MessageGuard.isUnsavedSender("Unknown"))
        assertTrue(MessageGuard.isUnsavedSender("Unknown number"))
        assertTrue(MessageGuard.isUnsavedSender("Unknown Sender"))

        // Saved contact names should return false (not unsaved)
        org.junit.Assert.assertFalse(MessageGuard.isUnsavedSender("Mom"))
        org.junit.Assert.assertFalse(MessageGuard.isUnsavedSender("Papa"))
        org.junit.Assert.assertFalse(MessageGuard.isUnsavedSender("Rohan Gupta"))
        org.junit.Assert.assertFalse(MessageGuard.isUnsavedSender("Priya Sharma"))
        org.junit.Assert.assertFalse(MessageGuard.isUnsavedSender("Dr. Mehta"))
        org.junit.Assert.assertFalse(MessageGuard.isUnsavedSender("Aunt Sunita"))

        // Lookup callback confirming saved contact returns false
        org.junit.Assert.assertFalse(
            MessageGuard.isUnsavedSender("+91 98765 43210", isContactSavedLookup = { true }),
        )
        assertTrue(
            MessageGuard.isUnsavedSender("+91 98765 43210", isContactSavedLookup = { false }),
        )
    }
}
