package io.ktor.sessions

import io.ktor.util.hex
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class SessionTransportTransformerEncrypt(
    val encryptionKeySpec: SecretKeySpec,
    val signKeySpec: SecretKeySpec,
    val ivGenerator: (size: Int) -> ByteArray = { size -> SecureRandom().generateSeed(size) },
    val encryptAlgorithm: String = encryptionKeySpec.algorithm,
    val signAlgorithm: String = signKeySpec.algorithm
) : SessionTransportTransformer {
    private val charset = Charsets.UTF_8
    val encryptionKeySize get() = encryptionKeySpec.encoded.size

    // Check that input keys are right
    init {
        encrypt(ivGenerator(encryptionKeySize), byteArrayOf())
        mac(byteArrayOf())
    }

    constructor(
        encryptionKey: ByteArray,
        signKey: ByteArray,
        ivGenerator: (size: Int) -> ByteArray = { size -> SecureRandom().generateSeed(size) },
        encryptAlgorithm: String = "AES",
        signAlgorithm: String = "HmacSHA256"
    ) : this(
        SecretKeySpec(encryptionKey, encryptAlgorithm),
        SecretKeySpec(signKey, signAlgorithm),
        ivGenerator
    )

    override fun transformRead(transportValue: String): String? {
        val encrypedMac = transportValue.substringAfterLast('/', "")
        val iv = hex(transportValue.substringBeforeLast('/'))
        val encrypted = hex(encrypedMac.substringBeforeLast(':'))
        val macHex = encrypedMac.substringAfterLast(':', "")
        val decrypted = decrypt(iv, encrypted)

        if (hex(mac(decrypted)) != macHex) {
            return null
        }

        return decrypted.toString(charset)
    }

    override fun transformWrite(transportValue: String): String {
        val iv = ivGenerator(encryptionKeySize)
        val decrypted = transportValue.toByteArray(charset)
        val encrypted = encrypt(iv, decrypted)
        val mac = mac(decrypted)
        return "${hex(iv)}/${hex(encrypted)}:${hex(mac)}"
    }

    private fun encrypt(initVector: ByteArray, decrypted: ByteArray): ByteArray {
        return encryptDecrypt(Cipher.ENCRYPT_MODE, initVector, decrypted)
    }

    private fun decrypt(initVector: ByteArray, encrypted: ByteArray): ByteArray {
        return encryptDecrypt(Cipher.DECRYPT_MODE, initVector, encrypted)
    }

    private fun encryptDecrypt(mode: Int, initVector: ByteArray, input: ByteArray): ByteArray {
        val iv = IvParameterSpec(initVector)
        val cipher = Cipher.getInstance("$encryptAlgorithm/CBC/PKCS5PADDING")
        cipher.init(mode, encryptionKeySpec, iv)
        return cipher.doFinal(input)
    }

    private fun mac(value: ByteArray): ByteArray = Mac.getInstance(signAlgorithm).run {
        init(signKeySpec)
        doFinal(value)
    }
}
