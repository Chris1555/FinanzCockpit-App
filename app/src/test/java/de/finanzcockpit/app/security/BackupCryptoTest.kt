package de.finanzcockpit.app.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupCryptoTest {
    @Test
    fun `verschluesselte Sicherung enthaelt keinen Klartext und ist wiederherstellbar`() {
        val plaintext = "{\"plans\":[{\"name\":\"Vertraulich\"}]}".toByteArray()
        val password = "ein langes Testpasswort".toCharArray()

        val encrypted = BackupCrypto.encrypt(plaintext, password)
        val restored = BackupCrypto.decrypt(encrypted, password)

        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("Vertraulich"))
        assertArrayEquals(plaintext, restored)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `falsches Passwort wird abgelehnt`() {
        val encrypted = BackupCrypto.encrypt("geheim".toByteArray(), "richtiges Passwort".toCharArray())
        BackupCrypto.decrypt(encrypted, "falsches Passwort".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `manipulierte Sicherung wird abgelehnt`() {
        val password = "richtiges Passwort".toCharArray()
        val encrypted = BackupCrypto.encrypt("geheim".toByteArray(), password)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()
        BackupCrypto.decrypt(encrypted, password)
    }
}
