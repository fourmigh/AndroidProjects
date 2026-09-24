package org.caojun.walletlogin

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HCE 卡模拟：响应读卡器的 SELECT AID 与挑战命令。
 *
 * 协议（AID = F04C4F47494E）：
 *   选择应用: 00 A4 04 00 06 F04C4F47494E 00            -> 90 00
 *   挑战:     80 10 00 00 10 <16字节 challenge>          -> id ‖ 0x00 ‖ proof ‖ 90 00
 *     id    = userId（userId 模式）或 opaqueId（opaqueId 模式）
 *     proof = HMAC-SHA256(nfcKey, challenge) 或 ECDSA 签名
 */
class NfcHostCardService : HostApduService() {

    companion object {
        const val AID = "F04C4F47494E"
        private const val INS_SELECT = 0xA4
        private const val INS_CHALLENGE = 0x10
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_UNKNOWN = byteArrayOf(0x6F, 0x00)
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return SW_UNKNOWN
        if (apdu.size < 4) return SW_UNKNOWN

        when (apdu[1].toInt() and 0xFF) {
            INS_SELECT -> return SW_OK
            INS_CHALLENGE -> return handleChallenge(apdu)
        }
        return SW_UNKNOWN
    }

    private fun handleChallenge(apdu: ByteArray): ByteArray {
        if (apdu.size < 5) return SW_UNKNOWN
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return SW_UNKNOWN
        val challenge = apdu.copyOfRange(5, 5 + lc)

        val id = NfcKeyStore.getDisplayId(this) ?: return SW_UNKNOWN
        val mode = NfcKeyStore.getMode(this) ?: "hmac"

        val proof = if (mode == "ecdsa") {
            runCatching { NfcKeyStore.signChallenge(challenge) }.getOrNull() ?: return SW_UNKNOWN
        } else {
            val keyHex = NfcKeyStore.getHmacKey(this) ?: return SW_UNKNOWN
            hmacSha256(hexToBytes(keyHex), challenge)
        }
        return id.toByteArray(Charsets.UTF_8) + byteArrayOf(0x00) + proof + SW_OK
    }

    override fun onDeactivated(reason: Int) {
        // 本次碰触结束，无需处理
    }

    private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length / 2
        val out = ByteArray(len)
        for (i in 0 until len) {
            out[i] = ((Character.digit(hex[i * 2], 16) shl 4) +
                Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
        return out
    }
}
