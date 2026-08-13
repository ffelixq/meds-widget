package io.github.ffelixq.medswidget.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface SensitiveDataProtector {
    fun encrypt(plaintext: String): String

    fun decryptOrPlaintext(value: String): String?
}

internal class SensitiveDataCipher(
    namespace: String,
) : SensitiveDataProtector {
    private val keyAlias = "meds_widget_${namespace}_v1"
    private val associatedData = namespace.toByteArray(StandardCharsets.UTF_8)
    private var robolectricKey: SecretKey? = null

    override fun encrypt(plaintext: String): String =
        try {
            encryptWithKey(plaintext, getOrCreateKey())
        } catch (_: GeneralSecurityException) {
            recoverKeyAndEncrypt(plaintext)
        } catch (_: IOException) {
            recoverKeyAndEncrypt(plaintext)
        }

    override fun decryptOrPlaintext(value: String): String? {
        if (!value.startsWith(ENCRYPTED_PREFIX)) return value
        return runCatching {
            val envelope = Base64.decode(value.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
            require(envelope.size > IV_LENGTH_BYTES)
            val iv = envelope.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertext = envelope.copyOfRange(IV_LENGTH_BYTES, envelope.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            cipher.updateAAD(associatedData)
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun recoverKeyAndEncrypt(plaintext: String): String {
        deleteKeySafely()
        return encryptWithKey(plaintext, getOrCreateKey())
    }

    private fun encryptWithKey(
        plaintext: String,
        key: SecretKey,
    ): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val envelope =
            ByteBuffer
                .allocate(cipher.iv.size + ciphertext.size)
                .put(cipher.iv)
                .put(ciphertext)
                .array()
        return ENCRYPTED_PREFIX + Base64.encodeToString(envelope, Base64.NO_WRAP)
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey =
        try {
            getOrCreateAndroidKeyStoreKey()
        } catch (error: GeneralSecurityException) {
            if (!isRobolectric()) throw error
            getOrCreateRobolectricKey()
        } catch (error: IOException) {
            if (!isRobolectric()) throw error
            getOrCreateRobolectricKey()
        }

    private fun getOrCreateAndroidKeyStoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val parameters =
            KeyGenParameterSpec
                .Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        generator.init(parameters)
        return generator.generateKey()
    }

    private fun getOrCreateRobolectricKey(): SecretKey {
        robolectricKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        generator.init(KEY_SIZE_BITS)
        return generator.generateKey().also { robolectricKey = it }
    }

    @Synchronized
    private fun deleteKeySafely() {
        if (isRobolectric()) {
            robolectricKey = null
            return
        }
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                load(null)
                if (containsAlias(keyAlias)) deleteEntry(keyAlias)
            }
        }
    }

    private fun isRobolectric(): Boolean = Build.FINGERPRINT == ROBOLECTRIC_FINGERPRINT

    companion object {
        internal const val ENCRYPTED_PREFIX = "enc:v1:"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ROBOLECTRIC_FINGERPRINT = "robolectric"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
    }
}
