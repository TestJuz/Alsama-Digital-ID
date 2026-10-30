package cr.alsamatours.digitalid.net

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores only the refresh credential, encrypted using a non-exportable Android
 * Keystore AES-GCM key. Access tokens are kept in memory and never logged.
 */
internal class SecureSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("alsama_secure_session", Context.MODE_PRIVATE)
    private val alias = "alsama_digital_id_refresh_v1"

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    fun save(refreshToken: String) {
        require(Regex("[0-9a-fA-F]{64}").matches(refreshToken))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(refreshToken.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + ciphertext
        prefs.edit().putString("encrypted_refresh", Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun read(): String? = try {
        val packed = Base64.decode(prefs.getString("encrypted_refresh", null) ?: return null, Base64.NO_WRAP)
        if (packed.size < 29) throw IllegalStateException("Invalid encrypted credentials")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        val value = String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8)
        value.takeIf { Regex("[0-9a-fA-F]{64}").matches(it) }
    } catch (_: Exception) {
        clear()
        null
    }

    fun clear() { prefs.edit().remove("encrypted_refresh").apply() }
}
