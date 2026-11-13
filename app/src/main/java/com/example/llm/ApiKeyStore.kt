package com.example.llm

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** User-supplied API keys, encrypted with a non-exportable Android Keystore AES key. Never bundled in the APK. */
class ApiKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("llm_keys", Context.MODE_PRIVATE)

    fun hasKey(provider: LlmProvider) = prefs.contains(provider.name)

    fun get(provider: LlmProvider): String? {
        val stored = prefs.getString(provider.name, null) ?: return null
        return runCatching {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            String(cipher.doFinal(raw, 12, raw.size - 12))
        }.getOrNull()
    }

    fun put(provider: LlmProvider, key: String) {
        if (key.isBlank()) { prefs.edit().remove(provider.name).apply(); return }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val out = cipher.iv + cipher.doFinal(key.trim().toByteArray())
        prefs.edit().putString(provider.name, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "depthlive_llm_keys"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
