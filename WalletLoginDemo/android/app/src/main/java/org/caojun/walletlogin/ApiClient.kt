package org.caojun.walletlogin

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {

    // 打包时注入（来源：local.properties）
    private val BASE_URL = BuildConfig.BASE_URL
    private val READER_TOKEN = BuildConfig.NFC_READER_TOKEN

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun Request.Builder.withReaderToken(): Request.Builder {
        if (READER_TOKEN.isNotBlank()) header("X-Reader-Token", READER_TOKEN)
        return this
    }

    // ---------- Google Wallet 登录卡 ----------

    data class BindResult(val mock: Boolean, val jwt: String?)
    data class MockCode(val code: String, val expiresInSeconds: Int)

    fun bindPass(userId: String): BindResult {
        val body = JSONObject().put("userId", userId).toString().toRequestBody(jsonType)
        val request = Request.Builder().url("$BASE_URL/api/bind-pass").post(body).build()
        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || !json.optBoolean("ok")) {
                throw IllegalStateException(json.optString("error", "HTTP ${response.code}"))
            }
            return BindResult(json.optBoolean("mock"), json.optString("jwt").ifBlank { null })
        }
    }

    fun fetchMockCode(userId: String): MockCode {
        val encoded = java.net.URLEncoder.encode(userId, "UTF-8")
        val request = Request.Builder().url("$BASE_URL/api/mock/code?userId=$encoded").get().build()
        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || !json.optBoolean("ok")) {
                throw IllegalStateException(json.optString("error", "HTTP ${response.code}"))
            }
            return MockCode(json.getString("code"), json.optInt("expiresInSeconds", 30))
        }
    }

    fun login(code: String): String {
        val body = JSONObject().put("code", code).toString().toRequestBody(jsonType)
        val request = Request.Builder().url("$BASE_URL/api/login/scan").post(body).build()
        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || !json.optBoolean("ok")) {
                throw IllegalStateException(json.optString("error", "HTTP ${response.code}"))
            }
            val user = json.getJSONObject("user")
            return "${user.optString("name")} (${user.optString("userId")})"
        }
    }

    // ---------- NFC 配置 ----------

    fun getConfig(): JSONObject {
        val request = Request.Builder().url("$BASE_URL/api/nfc/config").get().build()
        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || !json.optBoolean("ok")) {
                throw IllegalStateException(json.optString("error", "HTTP ${response.code}"))
            }
            return json.getJSONObject("config")
        }
    }

    fun setConfig(config: JSONObject): JSONObject {
        val body = config.toString().toRequestBody(jsonType)
        val request = Request.Builder().url("$BASE_URL/api/nfc/config").put(body).build()
        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || !json.optBoolean("ok")) {
                throw IllegalStateException(json.optString("error", "HTTP ${response.code}"))
            }
            return json.getJSONObject("config")
        }
    }

    // ---------- NFC 绑定 / 验证 ----------

    data class BindNfcResult(
        val mode: String,
        val idType: String,
        val opaqueId: String?,
        val nfcKey: String?
    )

    fun bindNfc(userId: String, publicKey: String?): BindNfcResult {
        val obj = JSONObject().put("userId", userId)
        if (!publicKey.isNullOrBlank()) obj.put("publicKey", publicKey)
        val body = obj.toString().toRequestBody(jsonType)
        val request = Request.Builder().url("$BASE_URL/api/nfc/bind").post(body).build()
        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || !json.optBoolean("ok")) {
                throw IllegalStateException(json.optString("error", "HTTP ${response.code}"))
            }
            return BindNfcResult(
                json.optString("mode"),
                json.optString("idType"),
                json.optString("opaqueId").ifBlank { null },
                json.optString("nfcKey").ifBlank { null }
            )
        }
    }

    /** 向后端申请 challenge（readerAuth 开启时会带读卡器口令）。 */
    fun getChallenge(): String {
        val request = Request.Builder()
            .url("$BASE_URL/api/nfc/challenge")
            .post("{}".toRequestBody(jsonType))
            .withReaderToken()
            .build()
        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || !json.optBoolean("ok")) {
                throw IllegalStateException(json.optString("error", "HTTP ${response.code}"))
            }
            return json.getString("challenge")
        }
    }

    data class NfcVerifyResult(
        val httpCode: Int,
        val ok: Boolean,
        val rawJson: String,
        val error: String?,
        val userName: String?,
        val userId: String?,
        val sessionToken: String?
    )

    fun verifyNfc(id: String, challenge: String, proof: String): NfcVerifyResult {
        val body = JSONObject()
            .put("id", id)
            .put("challenge", challenge)
            .put("proof", proof)
            .toString()
            .toRequestBody(jsonType)
        val request = Request.Builder()
            .url("$BASE_URL/api/nfc/verify")
            .post(body)
            .withReaderToken()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            val ok = response.isSuccessful && (json?.optBoolean("ok") == true)
            val user = json?.optJSONObject("user")
            return NfcVerifyResult(
                httpCode = response.code,
                ok = ok,
                rawJson = text,
                error = if (ok) null else (json?.optString("error")?.ifBlank { null } ?: "HTTP ${response.code}"),
                userName = user?.optString("name"),
                userId = user?.optString("userId"),
                sessionToken = json?.optString("sessionToken")?.ifBlank { null }
            )
        }
    }

    data class NfcBinding(val userId: String, val name: String, val credentials: List<String>)

    fun listNfcBindings(): List<NfcBinding> {
        val request = Request.Builder().url("$BASE_URL/api/nfc/bindings").get().build()
        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || !json.optBoolean("ok")) {
                throw IllegalStateException(json.optString("error", "HTTP ${response.code}"))
            }
            val arr = json.getJSONArray("bindings")
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val credsArr = o.optJSONArray("credentials")
                val creds = (0 until (credsArr?.length() ?: 0)).map { j ->
                    val c = credsArr!!.getJSONObject(j)
                    val base = "${c.optString("mode")}/${c.optString("idType")}"
                    val op = c.optString("opaqueId")
                    if (op.isNotBlank()) "$base ($op)" else base
                }
                NfcBinding(o.optString("userId"), o.optString("name"), creds)
            }
        }
    }

    fun unbindNfc(userId: String) {
        val body = JSONObject().put("userId", userId).toString().toRequestBody(jsonType)
        val request = Request.Builder().url("$BASE_URL/api/nfc/unbind").post(body).build()
        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || !json.optBoolean("ok")) {
                throw IllegalStateException(json.optString("error", "HTTP ${response.code}"))
            }
        }
    }
}
