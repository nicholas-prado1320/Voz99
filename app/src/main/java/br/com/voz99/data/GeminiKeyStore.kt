package br.com.voz99.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Armazena a chave Gemini criptografada com uma chave AES do Android Keystore.
 * Somente para demonstração: uma credencial guardada no cliente NÃO substitui
 * um backend que execute as chamadas à API com autenticação por usuário.
 */
class GeminiKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("voz99_private_config", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = !readKey().isNullOrBlank()

    fun saveKey(value: String): Boolean {
        val clean = value.trim()
        if (clean.isEmpty() || clean.length > 1000) return false
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(clean.toByteArray(StandardCharsets.UTF_8))
            val encodedIv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val encodedCipher = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            preferences.edit().putString(STORAGE_KEY, "$encodedIv:$encodedCipher").commit()
        }.getOrDefault(false)
    }

    fun removeKey() {
        preferences.edit().remove(STORAGE_KEY).apply()
    }

    fun readKey(): String? = runCatching {
        val record = preferences.getString(STORAGE_KEY, null) ?: return null
        val parts = record.split(':', limit = 2)
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey(KEY_ALIAS, null) as? SecretKey ?: return null
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "voz99_gemini_api_key"
        const val STORAGE_KEY = "encrypted_gemini_api_key"
    }
}
