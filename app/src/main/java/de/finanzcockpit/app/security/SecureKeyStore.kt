package de.finanzcockpit.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    data class Enrollment(
        val cipher: Cipher,
        val databasePassphrase: ByteArray,
    )

    fun isEnrolled(): Boolean = preferences.contains(KEY_WRAPPED_PASSPHRASE) &&
        preferences.contains(KEY_IV) && keyStore.containsAlias(KEY_ALIAS)

    fun prepareEnrollment(): Enrollment {
        val passphrase = ByteArray(32).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        return Enrollment(cipher, passphrase)
    }

    fun finishEnrollment(cipher: Cipher, databasePassphrase: ByteArray): ByteArray {
        val wrapped = cipher.doFinal(databasePassphrase)
        preferences.edit(commit = true) {
            putString(KEY_WRAPPED_PASSPHRASE, Base64.encodeToString(wrapped, Base64.NO_WRAP))
            putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        }
        wrapped.fill(0)
        return databasePassphrase
    }

    fun prepareUnlock(): Cipher {
        check(isEnrolled()) { "Die App ist noch nicht eingerichtet." }
        val iv = Base64.decode(preferences.getString(KEY_IV, null), Base64.NO_WRAP)
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        }.also { iv.fill(0) }
    }

    fun finishUnlock(cipher: Cipher): ByteArray {
        val wrapped = Base64.decode(
            preferences.getString(KEY_WRAPPED_PASSPHRASE, null),
            Base64.NO_WRAP,
        )
        return try {
            cipher.doFinal(wrapped)
        } finally {
            wrapped.fill(0)
        }
    }

    fun clearEnrollment() {
        preferences.edit(commit = true) { clear() }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getKey(): SecretKey = (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)
        ?: error("Sicherheitsschlüssel ist nicht verfügbar.")

    private fun getOrCreateKey(): SecretKey {
        if (keyStore.containsAlias(KEY_ALIAS)) return getKey()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .setUnlockedDeviceRequired(true)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or
                            KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "finanzcockpit.database.key.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFERENCES = "secure_key_material"
        private const val KEY_WRAPPED_PASSPHRASE = "wrapped_passphrase"
        private const val KEY_IV = "wrapped_passphrase_iv"
    }
}
