package com.shikhi.app.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES/GCM encryption of the refresh token at rest, keyed in the Android Keystore
 * (ADR-0012 — the deprecated androidx security-crypto library is deliberately avoided).
 * Output/input format: base64(iv || ciphertext).
 */
@Singleton
class RefreshTokenCipher @Inject constructor() {

	private companion object {
		const val KEY_ALIAS = "shikhi.refresh-token"
		const val TRANSFORMATION = "AES/GCM/NoPadding"
		const val IV_BYTES = 12
		const val TAG_BITS = 128
	}

	private fun key(): SecretKey {
		val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
		(ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
		val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
		generator.init(
			KeyGenParameterSpec.Builder(
				KEY_ALIAS,
				KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
			)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
				.setKeySize(256)
				.build(),
		)
		return generator.generateKey()
	}

	fun encrypt(plaintext: String): String {
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.ENCRYPT_MODE, key())
		val sealed = cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
		return Base64.encodeToString(sealed, Base64.NO_WRAP)
	}

	fun decrypt(sealed: String): String {
		val bytes = Base64.decode(sealed, Base64.NO_WRAP)
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
		return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
	}
}
