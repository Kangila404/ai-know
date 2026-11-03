package org.aiknow.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only the server session cookie is persisted. OAuth tokens never enter this store. */
class SessionVault(context: Context, private val origin: String) : CookieStore {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("aiknow.session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("aiknow.session",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun save(cookie: String?) {
        if(cookie == null) { prefs.edit().remove(origin).commit(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val encrypted = cipher.doFinal(cookie.toByteArray())
        prefs.edit().putString(origin, Base64.encodeToString(cipher.iv + encrypted,Base64.NO_WRAP)).commit()
    }
    override fun load(): String? = runCatching {
        val raw = prefs.getString(origin,null) ?: return null
        val bytes = Base64.decode(raw,Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))) }
        String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)))
    }.getOrElse { prefs.edit().remove(origin).commit(); null }
}
