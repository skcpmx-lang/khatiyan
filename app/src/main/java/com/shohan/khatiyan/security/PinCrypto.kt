package com.shohan.khatiyan.security

import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN storage (Phase 29): PBKDF2-HMAC-SHA256, 120k iterations, per-install
 * random salt. The PIN itself is never stored anywhere. Base64 via android.util
 * keeps zero extra dependencies.
 */
object PinCrypto {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256

    data class PinSecret(val saltB64: String, val hashB64: String)

    private fun randomSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun derive(salt: ByteArray, pin: String): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun create(pin: String): PinSecret {
        val salt = randomSalt()
        val hash = derive(salt, pin)
        return PinSecret(
            saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP),
        )
    }

    fun verify(pin: String, saltB64: String, hashB64: String): Boolean {
        if (saltB64.isBlank() || hashB64.isBlank()) return false
        return try {
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val expected = Base64.decode(hashB64, Base64.NO_WRAP)
            MessageDigest.isEqual(derive(salt, pin), expected) // constant-time compare
        } catch (e: Exception) {
            false
        }
    }
}
