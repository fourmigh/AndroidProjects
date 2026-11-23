package org.caojun.ai.translate.hub.core

import android.content.Context
import android.os.StatFs
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 模型文件仓库。
 *
 * 目录布局（外部私有目录，卸载自动清理）：
 * ```
 * files/models/
 * ├── <modelId>/
 * │   ├── model.gguf
 * │   └── manifest.json      # 安装完成标记 + 实际 sha256 + 时间
 * └── <modelId>.part/        # 下载中
 * ```
 */
class ModelRepository(context: Context) {

    private val appContext = context.applicationContext

    private val root: File
        get() = (appContext.getExternalFilesDir("models") ?: File(appContext.filesDir, "models"))
            .also { if (!it.exists()) it.mkdirs() }

    fun modelDir(modelId: String): File = File(root, modelId)

    fun partDir(modelId: String): File = File(root, "$modelId.part")

    fun fileOf(modelId: String, fileName: String): File = File(modelDir(modelId), fileName)

    fun manifestOf(modelId: String): File = File(modelDir(modelId), "manifest.json")

    /** 已安装且文件完整（大小匹配）。 */
    fun isInstalled(model: CatalogModel): Boolean {
        if (!manifestOf(model.id).exists()) return false
        return model.files.all { f ->
            val file = fileOf(model.id, f.name)
            file.exists() && (f.sizeBytes <= 0L || file.length() == f.sizeBytes)
        }
    }

    fun installedModelIds(): Set<String> =
        root.listFiles()
            ?.filter { it.isDirectory && File(it, "manifest.json").exists() }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()

    fun installedBytes(): Long =
        root.listFiles()?.filter { it.isDirectory }?.sumOf { dir ->
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } ?: 0L

    /** 下载中（.part 目录）占用的字节数。 */
    fun partBytes(): Long =
        root.listFiles()?.filter { it.isDirectory && it.name.endsWith(".part") }?.sumOf { dir ->
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } ?: 0L

    fun freeBytes(): Long = runCatching {
        StatFs(root.absolutePath).availableBytes
    }.getOrDefault(0L)

    /**
     * 校验下载结果并安装：大小 +（可选）sha256 → 移动到正式目录 → 写 manifest。
     *
     * @return 安装成功返回实际 sha256，失败返回 null（临时目录保留，便于排查/续传）
     */
    fun verifyAndInstall(model: CatalogModel, onProgress: ((String) -> Unit)? = null): String? {
        val part = partDir(model.id)
        for (f in model.files) {
            // 下载文件名带时间戳前缀，这里按后缀匹配，取最新的一份
            val file = part.listFiles()
                ?.filter { it.isFile && it.name.endsWith(f.name) }
                ?.maxByOrNull { it.lastModified() }

            if (file == null) {
                Log.e(TAG, "missing downloaded file for ${f.name} in ${part.absolutePath}")
                return null
            }
            if (f.sizeBytes > 0 && file.length() != f.sizeBytes) {
                Log.e(TAG, "size mismatch ${file.name}: ${file.length()} != ${f.sizeBytes}")
                return null
            }
            onProgress?.invoke("校验 ${f.name}…")
            if (f.sha256.isNotBlank()) {
                val actual = sha256(file)
                if (!actual.equals(f.sha256, ignoreCase = true)) {
                    Log.e(TAG, "sha256 mismatch ${file.name}: $actual != ${f.sha256}")
                    file.delete()
                    return null
                }
            }
        }

        val target = modelDir(model.id)
        if (target.exists()) target.deleteRecursively()
        if (!target.mkdirs()) return null

        var firstSha = ""
        for (f in model.files) {
            val src = part.listFiles()
                ?.filter { it.isFile && it.name.endsWith(f.name) }
                ?.maxByOrNull { it.lastModified() }
                ?: return null
            val dst = File(target, f.name)
            if (!src.renameTo(dst)) {
                src.copyTo(dst, overwrite = true)
                src.delete()
            }
            if (firstSha.isEmpty()) {
                firstSha = f.sha256.ifBlank { sha256(dst) }
            }
        }

        JSONObject().apply {
            put("modelId", model.id)
            put("installedAt", System.currentTimeMillis())
            put("sha256", firstSha)
            put("sizeBytes", model.sizeBytes)
        }.also { manifestOf(model.id).writeText(it.toString()) }

        part.deleteRecursively()
        return firstSha
    }

    fun delete(modelId: String): Boolean {
        partDir(modelId).deleteRecursively()
        return modelDir(modelId).deleteRecursively()
    }

    /** 流式计算 SHA-256，避免把 GB 级文件读进内存。 */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "HubModelRepo"
    }
}
