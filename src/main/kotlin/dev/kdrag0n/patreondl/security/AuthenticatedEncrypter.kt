package dev.kdrag0n.patreondl.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// AES-256-GCM
private const val KEY_BYTES = 32
private const val NONCE_BYTES = 12
private const val TAG_BYTES = 16

class AuthenticatedEncrypter(keyData: ByteArray) {
    init {
        if (keyData.size != KEY_BYTES) {
            error("Invalid key size; expected $KEY_BYTES bytes")
        }
    }

    private val rand = SecureRandom.getInstanceStrong()
    private val key = SecretKeySpec(keyData, "AES")

    fun encrypt(data: ByteArray): ByteArray {
        // Nonce
        val nonce = ByteArray(NONCE_BYTES)
        rand.nextBytes(nonce)

        // Encrypt
        val spec = GCMParameterSpec(TAG_BYTES * 8, nonce)
        val cipher = Cipher.getInstance("AES/GCM/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        return nonce + cipher.doFinal(data)
    }

    fun decrypt(data: ByteArray): ByteArray {
        // Nonce
        val nonce = data.sliceArray(0 until NONCE_BYTES)

        // Decrypt
        val spec = GCMParameterSpec(TAG_BYTES * 8, nonce)
        val cipher = Cipher.getInstance("AES/GCM/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(data.sliceArray(NONCE_BYTES until data.size))
    }
}