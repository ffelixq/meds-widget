package io.github.ffelixq.medswidget.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SensitiveDataCipherInstrumentedTest {
    @Test
    fun encryptedEnvelopeRoundTripsWithoutPlaintext() {
        val cipher = SensitiveDataCipher("test_${UUID.randomUUID()}")
        val plaintext = "{\"medicineName\":\"Private medicine\",\"taken\":true}"

        val encrypted = cipher.encrypt(plaintext)

        assertTrue(encrypted.startsWith(SensitiveDataCipher.ENCRYPTED_PREFIX))
        assertFalse(encrypted.contains("Private medicine"))
        assertEquals(plaintext, cipher.decryptOrPlaintext(encrypted))
    }

    @Test
    fun legacyPlaintextIsAcceptedForMigration() {
        val cipher = SensitiveDataCipher("test_${UUID.randomUUID()}")
        val legacy = "{\"legacy\":true}"

        assertEquals(legacy, cipher.decryptOrPlaintext(legacy))
    }
}
