package org.caojun.walletlogin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature

/**
 * NFC 登录凭证存储：
 * - 绑定信息（userId / 模式 / ID 类型 / opaqueId / HMAC 密钥）存 EncryptedSharedPreferences
 * - ECDSA 模式的私钥生成于 Android Keystore（不可导出），仅存别名
 */
object NfcKeyStore {

    private const val FILE = "nfc_secure_prefs"
    private const val KEY_USER_ID = "userId"
    private const val KEY_MODE = "nfcMode"
    private const val KEY_ID_TYPE = "idType"
    private const val KEY_OPAQUE = "opaqueId"
    private const val KEY_HMAC = "nfcKey"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val EC_ALIAS = "nfc_login_ec"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        FILE,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** 保存绑定信息；hmacKey 仅 HMAC 模式需要（ECDSA 私钥在 Keystore）。 */
    fun save(
        context: Context,
        userId: String,
        mode: String,
        idType: String,
        opaqueId: String?,
        hmacKey: String?
    ) {
        prefs(context).edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_MODE, mode)
            .putString(KEY_ID_TYPE, idType)
            .putString(KEY_OPAQUE, opaqueId)
            .putString(KEY_HMAC, hmacKey)
            .apply()
    }

    fun getUserId(context: Context): String? = prefs(context).getString(KEY_USER_ID, null)
    fun getMode(context: Context): String? = prefs(context).getString(KEY_MODE, null)
    fun getIdType(context: Context): String? = prefs(context).getString(KEY_ID_TYPE, null)
    fun getOpaqueId(context: Context): String? = prefs(context).getString(KEY_OPAQUE, null)
    fun getHmacKey(context: Context): String? = prefs(context).getString(KEY_HMAC, null)

    fun isBound(context: Context): Boolean = !getUserId(context).isNullOrBlank()

    /** 出示时使用的 ID：opaqueId 模式用 opaqueId，否则用 userId。 */
    fun getDisplayId(context: Context): String? =
        if (getIdType(context) == "opaqueId") getOpaqueId(context) else getUserId(context)

    /** 解绑：清本地数据并删除 Keystore 密钥。 */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(EC_ALIAS)) ks.deleteEntry(EC_ALIAS)
        }
    }

    // ---------- ECDSA（Keystore 私钥，不可导出） ----------

    private fun getOrCreateEcKeyPair(): KeyPair {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(EC_ALIAS, null) as? KeyStore.PrivateKeyEntry)?.let {
            return KeyPair(it.certificate.publicKey, it.privateKey)
        }
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        kpg.initialize(
            KeyGenParameterSpec.Builder(EC_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return kpg.generateKeyPair()
    }

    /** 导出公钥（Base64 X.509/SPKI），用于绑定时注册到服务端。 */
    fun getEcPublicKeyBase64(): String {
        val kp = getOrCreateEcKeyPair()
        return Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
    }

    /** 用 Keystore 私钥对 challenge 做 ECDSA-SHA256 签名（DER 编码）。 */
    fun signChallenge(challenge: ByteArray): ByteArray {
        val kp = getOrCreateEcKeyPair()
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(kp.private)
        sig.update(challenge)
        return sig.sign()
    }
}
