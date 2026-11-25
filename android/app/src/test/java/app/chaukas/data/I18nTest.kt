package app.chaukas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class I18nTest {

    @Test
    fun englishReturnsOriginal() {
        assertEquals("Is this message a scam?", I18n.t("Is this message a scam?", "English"))
        assertEquals("Check it", I18n.t("Check it", "English"))
    }

    @Test
    fun hindiTranslations() {
        assertEquals("क्या यह मैसेज धोखा है?", I18n.t("Is this message a scam?", "Hindi"))
        assertEquals("जाँचिए", I18n.t("Check it", "Hindi"))
        assertEquals("परिवार", I18n.t("Family", "Hindi"))
    }

    @Test
    fun bengaliTranslations() {
        assertEquals("এই মেসেজটা কি প্রতারণা?", I18n.t("Is this message a scam?", "Bengali"))
        assertEquals("যাচাই করুন", I18n.t("Check it", "Bengali"))
        assertEquals("পরিবার", I18n.t("Family", "Bengali"))
    }

    @Test
    fun tamilTranslations() {
        assertEquals("இந்த மெசேஜ் மோசடியா?", I18n.t("Is this message a scam?", "Tamil"))
        assertEquals("சரிபார்", I18n.t("Check it", "Tamil"))
        assertEquals("குடும்பம்", I18n.t("Family", "Tamil"))
    }

    @Test
    fun teluguTranslations() {
        assertEquals("ఈ మెసేజ్ మోసమా?", I18n.t("Is this message a scam?", "Telugu"))
        assertEquals("తనిఖీ చేయండి", I18n.t("Check it", "Telugu"))
        assertEquals("కుటుంబం", I18n.t("Family", "Telugu"))
    }

    @Test
    fun marathiTranslations() {
        assertEquals("हा मेसेज फसवणूक आहे का?", I18n.t("Is this message a scam?", "Marathi"))
        assertEquals("तपासा", I18n.t("Check it", "Marathi"))
        assertEquals("कुटुंब", I18n.t("Family", "Marathi"))
    }

    @Test
    fun unknownKeyFallsBackToOriginal() {
        assertEquals("Some Unknown Sentence", I18n.t("Some Unknown Sentence", "Hindi"))
    }

    @Test
    fun scamWaveTranslationMatchesRobustly() {
        val banner = "Scam wave: fake electricity-bill and KYC \"update\" texts are spiking. Never pay or share an OTP through a texted link."
        assertNotEquals(banner, I18n.t(banner, "Hindi"))
        assertNotEquals(banner, I18n.t(banner, "Bengali"))
        assertNotEquals(banner, I18n.t(banner, "Tamil"))
        assertNotEquals(banner, I18n.t(banner, "Telugu"))
        assertNotEquals(banner, I18n.t(banner, "Marathi"))

        // Also works with curly quotes or variation
        val curlyBanner = "Scam wave: fake electricity-bill and KYC “update” texts are spiking. Never pay or share an OTP through a texted link."
        assertEquals(I18n.t(banner, "Hindi"), I18n.t(curlyBanner, "Hindi"))
    }

    @Test
    fun dojoAndEmptyCircleTranslations() {
        assertEquals("धोखा पहचान अभ्यास", I18n.t("Scam Dojo", "Hindi"))
        assertEquals("अभ्यास", I18n.t("Dojo", "Hindi"))
        assertEquals("सब ठीक है", I18n.t("All quiet", "Hindi"))
        assertEquals("সব শান্ত", I18n.t("All quiet", "Bengali"))
        assertEquals("எல்லாம் அமைதி", I18n.t("All quiet", "Tamil"))
        assertEquals("అంతా ప్రశాంతం", I18n.t("All quiet", "Telugu"))
        assertEquals("सगळं शांत", I18n.t("All quiet", "Marathi"))
    }
}
