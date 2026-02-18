package dev.weuizx.jobzi.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Service for encrypting and decrypting sensitive data using AES-256-CBC.
 *
 * Encryption format: Base64(IV + ciphertext)
 * - IV: 16 random bytes
 * - Ciphertext: encrypted data
 *
 * The encryption key must be 32 bytes (256 bits) and should be stored in
 * environment variable TELEGRAM_POOL_ENCRYPTION_KEY.
 */
@Service
class EncryptionService(
    @Value("\${telegram.account-pool.encryption-key}")
    private val encryptionKeyString: String
) {
    private val algorithm = "AES/CBC/PKCS5Padding"
    private val secureRandom = SecureRandom()

    private val encryptionKey: SecretKeySpec by lazy {
        val keyBytes = encryptionKeyString.toByteArray(Charsets.UTF_8)
        require(keyBytes.size >= 32) {
            "Encryption key must be at least 32 bytes (256 bits). " +
            "Generate one with: openssl rand -base64 32"
        }
        // Use first 32 bytes if key is longer
        SecretKeySpec(keyBytes.copyOf(32), "AES")
    }

    /**
     * Encrypts a plaintext string.
     *
     * @param plaintext The string to encrypt
     * @return Base64-encoded string containing IV + ciphertext
     */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(algorithm)

        // Generate random IV
        val iv = ByteArray(16)
        secureRandom.nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)

        // Encrypt
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, ivSpec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Concatenate IV + ciphertext
        val combined = iv + ciphertext

        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Decrypts an encrypted string.
     *
     * @param encrypted Base64-encoded string containing IV + ciphertext
     * @return Decrypted plaintext
     */
    fun decrypt(encrypted: String): String {
        val combined = Base64.getDecoder().decode(encrypted)

        require(combined.size > 16) {
            "Invalid encrypted data: too short"
        }

        // Extract IV (first 16 bytes) and ciphertext (rest)
        val iv = combined.copyOfRange(0, 16)
        val ciphertext = combined.copyOfRange(16, combined.size)

        val cipher = Cipher.getInstance(algorithm)
        val ivSpec = IvParameterSpec(iv)

        // Decrypt
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, ivSpec)
        val plaintext = cipher.doFinal(ciphertext)

        return String(plaintext, Charsets.UTF_8)
    }

    /**
     * Verifies the encryption key is properly configured.
     * Called during application startup to fail fast if misconfigured.
     */
    fun verifyConfiguration() {
        try {
            // Trigger lazy initialization
            encryptionKey

            // Verify encryption/decryption works
            val testData = "test-encryption-${System.currentTimeMillis()}"
            val encrypted = encrypt(testData)
            val decrypted = decrypt(encrypted)

            require(decrypted == testData) {
                "Encryption verification failed: decrypted data doesn't match original"
            }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to initialize EncryptionService. " +
                "Ensure TELEGRAM_POOL_ENCRYPTION_KEY is set correctly.",
                e
            )
        }
    }
}
