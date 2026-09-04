package de.finanzcockpit.app.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    private val magic = byteArrayOf(0x46, 0x43, 0x42, 0x4B) // FCBK
    private val aad = "FinanzCockpitBackup:v1".toByteArray(Charsets.UTF_8)
    private const val VERSION = 1
    private const val ITERATIONS = 600_000
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val MAX_PLAINTEXT_BYTES = 5 * 1024 * 1024

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Sicherung ist zu groß." }
        require(password.size >= 12) { "Das Sicherungspasswort muss mindestens 12 Zeichen haben." }
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val key = deriveKey(password, salt, ITERATIONS)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                updateAAD(aad)
            }
            val encrypted = cipher.doFinal(plaintext)
            ByteArrayOutputStream().use { bytes ->
                DataOutputStream(bytes).use { out ->
                    out.write(magic)
                    out.writeByte(VERSION)
                    out.writeInt(ITERATIONS)
                    out.writeByte(salt.size)
                    out.writeByte(iv.size)
                    out.writeInt(encrypted.size)
                    out.write(salt)
                    out.write(iv)
                    out.write(encrypted)
                }
                bytes.toByteArray()
            }.also { encrypted.fill(0) }
        } finally {
            key.encoded?.fill(0)
            salt.fill(0)
            iv.fill(0)
        }
    }

    fun decrypt(container: ByteArray, password: CharArray): ByteArray {
        require(container.size <= MAX_PLAINTEXT_BYTES + 1_024) { "Sicherungsdatei ist zu groß." }
        return DataInputStream(ByteArrayInputStream(container)).use { input ->
            val actualMagic = ByteArray(magic.size).also(input::readFully)
            require(actualMagic.contentEquals(magic)) { "Keine FinanzCockpit-Sicherung." }
            require(input.readUnsignedByte() == VERSION) { "Nicht unterstützte Sicherungsversion." }
            val iterations = input.readInt()
            require(iterations in 100_000..2_000_000) { "Ungültige Schlüsselparameter." }
            val saltLength = input.readUnsignedByte()
            val ivLength = input.readUnsignedByte()
            val encryptedLength = input.readInt()
            require(saltLength == SALT_SIZE && ivLength == IV_SIZE) { "Ungültige Sicherungsparameter." }
            require(encryptedLength in 16..MAX_PLAINTEXT_BYTES + 16) { "Ungültige Sicherungslänge." }
            require(input.available() == saltLength + ivLength + encryptedLength) {
                "Beschädigte Sicherungsdatei."
            }
            val salt = ByteArray(saltLength).also(input::readFully)
            val iv = ByteArray(ivLength).also(input::readFully)
            val encrypted = ByteArray(encryptedLength).also(input::readFully)
            val key = deriveKey(password, salt, iterations)
            try {
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                    updateAAD(aad)
                    doFinal(encrypted)
                }
            } catch (_: AEADBadTagException) {
                throw IllegalArgumentException("Falsches Passwort oder beschädigte Sicherung.")
            } finally {
                key.encoded?.fill(0)
                salt.fill(0)
                iv.fill(0)
                encrypted.fill(0)
                actualMagic.fill(0)
            }
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try {
                SecretKeySpec(raw, "AES")
            } finally {
                raw.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }
}
