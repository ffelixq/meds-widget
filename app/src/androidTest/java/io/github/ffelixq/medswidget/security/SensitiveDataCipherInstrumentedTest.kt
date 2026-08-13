package io.github.ffelixq.medswidget.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun tamperedEncryptedEnvelopeFailsClosed() {
        val cipher = SensitiveDataCipher("test_${UUID.randomUUID()}")
        val encrypted = cipher.encrypt("{\"medicineName\":\"Private medicine\"}")
        val finalCharacter = encrypted.last()
        val replacement = if (finalCharacter == 'A') 'B' else 'A'
        val tampered = encrypted.dropLast(1) + replacement

        assertNull(cipher.decryptOrPlaintext(tampered))
    }

    @Test
    fun encryptedEnvelopeCannotCrossCacheNamespaces() {
        val first = SensitiveDataCipher("first_${UUID.randomUUID()}")
        val second = SensitiveDataCipher("second_${UUID.randomUUID()}")
        val encrypted = first.encrypt("{\"medicineName\":\"Private medicine\"}")

        assertNull(second.decryptOrPlaintext(encrypted))
    }

    @Test
    fun legacyPlaintextIsAcceptedForMigration() {
        val cipher = SensitiveDataCipher("test_${UUID.randomUUID()}")
        val legacy = "{\"legacy\":true}"

        assertEquals(legacy, cipher.decryptOrPlaintext(legacy))
    }
}
