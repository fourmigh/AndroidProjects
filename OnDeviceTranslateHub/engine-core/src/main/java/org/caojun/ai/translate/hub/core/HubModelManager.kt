package org.caojun.ai.translate.hub.core

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.caojun.ai.translate.provider.LanguageCatalog

/** 单个模型的安装状态。 */
sealed interface InstallState {
    data object NotInstalled : InstallState
    data class Downloading(val bytes: Long, val total: Long) : InstallState
    data object Verifying : InstallState
    data object Ready : InstallState
    data class Failed(val message: String) : InstallState
}

/**
 * 模型管理器：目录、下载、安装、删除、推理调度。
 *
 * 进程内单例，UI（Activity）与 provider（Service）共用同一份状态和同一个已加载模型。
 */
object HubModelManager {

    private const val TAG = "HubModelManager"

    private lateinit var appContext: Context
    private lateinit var repo: ModelRepository
    private lateinit var downloader: ModelDownloader
    private lateinit var settings: HubSettings

    /** 由 app 启动时注入（engine-llama 的实现）。 */
    @Volatile
    var engine: HubTranslateEngine? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val installMutex = Mutex()
    private val translateMutex = Mutex()

    /** modelId -> DownloadManager 任务 id，用于真正取消。 */
    private val activeDownloads = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val _states = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val states: StateFlow<Map<String, InstallState>> = _states.asStateFlow()

    private val _loadedModelId = MutableStateFlow<String?>(null)
    val loadedModelId: StateFlow<String?> = _loadedModelId.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        repo = ModelRepository(appContext)
        downloader = ModelDownloader(appContext)
        settings = HubSettings(appContext)
        resumeDownloads()
    }

    fun repository(): ModelRepository = repo

    fun settings(): HubSettings = settings

    fun catalog(): List<CatalogModel> = ModelCatalog.load(appContext)

    fun model(id: String): CatalogModel? = catalog().firstOrNull { it.id == id }

    // ------------------------------------------------------------------ 安装

    fun isInstalled(model: CatalogModel): Boolean = repo.isInstalled(model)

    fun installedModels(): List<CatalogModel> = catalog().filter { repo.isInstalled(it) }

    fun stateOf(model: CatalogModel): InstallState = when {
        repo.isInstalled(model) -> InstallState.Ready
        else -> _states.value[model.id] ?: InstallState.NotInstalled
    }

    fun totalDeviceRamMb(): Int = runCatching {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        (info.totalMem / (1024 * 1024)).toInt()
    }.getOrDefault(4096)

    /** 检查空间与内存门槛，返回错误提示（null 表示可以下载）。 */
    fun preflight(model: CatalogModel): String? {
        val free = repo.freeBytes()
        val need = (model.sizeBytes * 1.15).toLong()
        if (free < need) {
            return "可用空间不足：还需约 ${(need - free) / (1024 * 1024)} MB"
        }
        if (totalDeviceRamMb() < model.minRamMb) {
            return "设备内存不足：该模型建议 ${model.minRamMb / 1024} GB 以上"
        }
        return null
    }

    fun install(model: CatalogModel) {
        val error = preflight(model)
        if (error != null) {
            setState(model.id, InstallState.Failed(error))
            return
        }
        if (_states.value[model.id] is InstallState.Downloading) return

        scope.launch {
            installMutex.withLock {
                downloadAndInstall(model)
            }
        }
    }

    private suspend fun downloadAndInstall(model: CatalogModel) {
        val file = model.files.firstOrNull() ?: run {
            setState(model.id, InstallState.Failed("catalog 中没有可下载文件"))
            return
        }
        repo.partDir(model.id).deleteRecursively()

        var urlIndex = 0
        var round = 0
        while (round < 3) {
            while (urlIndex < file.urls.size) {
                val downloadId = runCatching {
                    downloader.enqueue(model, file, urlIndex, settings.allowMetered)
                }.getOrNull()

                if (downloadId == null) {
                    setState(model.id, InstallState.Failed("无法创建下载任务"))
                    return
                }

                // 持久化：进程被杀后还能接着轮询
                settings.setActiveDownload(model.id, downloadId)

                if (monitor(model, downloadId, file.sizeBytes) && verifyInstalled(model)) {
                    return
                }

                settings.clearActiveDownload(model.id)
                downloader.cancel(downloadId)
                repo.partDir(model.id).deleteRecursively()
                urlIndex++
            }
            round++
            urlIndex = 0
            if (round < 3) delay(1500)
        }

        setState(model.id, InstallState.Failed("下载失败：所有镜像均不可用"))
    }

    /** 轮询一个 DownloadManager 任务直到结束；返回是否成功。 */
    private suspend fun monitor(
        model: CatalogModel,
        downloadId: Long,
        expectedSize: Long,
    ): Boolean {
        activeDownloads[model.id] = downloadId
        try {
            while (true) {
                delay(700)
                val snapshot = downloader.query(downloadId) ?: return false
                if (snapshot.isRunning) {
                    val total = if (snapshot.totalBytes > 0) snapshot.totalBytes else expectedSize
                    setState(model.id, InstallState.Downloading(snapshot.downloadedBytes, total))
                    continue
                }
                return snapshot.isSuccess
            }
        } finally {
            activeDownloads.remove(model.id)
        }
    }

    /** 校验并安装；成功则把状态置为 Ready。 */
    private suspend fun verifyInstalled(model: CatalogModel): Boolean {
        setState(model.id, InstallState.Verifying)
        val sha = withContext(Dispatchers.IO) { repo.verifyAndInstall(model) }
        if (sha == null) {
            return false
        }
        settings.clearActiveDownload(model.id)
        setState(model.id, InstallState.Ready)
        return true
    }

    /**
     * 进程重启后恢复未完成的下载：DownloadManager 里的任务还在跑，
     * 但只要 App 被杀过，内存里的状态就没了，这里重新接上轮询。
     */
    private fun resumeDownloads() {
        scope.launch {
            settings.activeDownloads().forEach { (modelId, downloadId) ->
                val model = model(modelId) ?: return@forEach
                if (repo.isInstalled(model)) {
                    settings.clearActiveDownload(modelId)
                    return@forEach
                }
                launch {
                    installMutex.withLock {
                        val file = model.files.firstOrNull() ?: return@withLock
                        if (monitor(model, downloadId, file.sizeBytes) && verifyInstalled(model)) {
                            return@withLock
                        }
                        // 任务已失效（被系统清理/失败）：清掉临时文件，回到未安装
                        settings.clearActiveDownload(modelId)
                        repo.partDir(modelId).deleteRecursively()
                        setState(modelId, InstallState.NotInstalled)
                    }
                }
            }
        }
    }

    fun cancelInstall(model: CatalogModel) {
        activeDownloads.remove(model.id)?.let { downloader.cancel(it) }
        settings.clearActiveDownload(model.id)
        setState(model.id, InstallState.NotInstalled)
        repo.partDir(model.id).deleteRecursively()
    }

    fun delete(model: CatalogModel) {
        if (engine?.loadedModelId == model.id) {
            engine?.unload()
            _loadedModelId.value = null
        }
        activeDownloads.remove(model.id)?.let { downloader.cancel(it) }
        settings.clearActiveDownload(model.id)
        repo.delete(model.id)
        setState(model.id, InstallState.NotInstalled)
        if (settings.defaultModelId == model.id) settings.defaultModelId = null
    }

    private fun setState(modelId: String, state: InstallState) {
        _states.value = _states.value.toMutableMap().apply { put(modelId, state) }
    }

    // ------------------------------------------------------------------ 推理

    /** 为方向挑一个已安装模型。 */
    fun pickModel(from: String, to: String): CatalogModel? {
        val installed = installedModels().filter { it.supports(from) && it.supports(to) }
        if (installed.isEmpty()) return null
        settings.defaultModelId?.let { id ->
            installed.firstOrNull { it.id == id }?.let { return it }
        }
        return installed.maxByOrNull { it.quality }
    }

    suspend fun translate(text: String, from: String, to: String): String {
        if (text.isBlank()) return ""
        val model = pickModel(from, to)
            ?: throw IllegalStateException("没有已安装的模型支持 $from -> $to")

        val impl = engine ?: throw IllegalStateException("推理引擎未初始化")
        val file = repo.fileOf(model.id, model.files.first().name)

        return translateMutex.withLock {
            withContext(Dispatchers.Default) {
                if (impl.loadedModelId != model.id) {
                    if (!impl.ensureLoaded(model, file)) {
                        val reason = impl.lastError?.takeIf { it.isNotBlank() }?.let { "：$it" } ?: ""
                        throw IllegalStateException("模型加载失败（${model.name}）$reason")
                    }
                    _loadedModelId.value = impl.loadedModelId
                }
                impl.translate(model, text, from, to)
            }
        }
    }

    fun preload(from: String, to: String) {
        val model = pickModel(from, to) ?: return
        val impl = engine ?: return
        scope.launch {
            translateMutex.withLock {
                if (impl.loadedModelId != model.id) {
                    val file = repo.fileOf(model.id, model.files.first().name)
                    withContext(Dispatchers.Default) { impl.ensureLoaded(model, file) }
                    _loadedModelId.value = impl.loadedModelId
                }
            }
        }
    }

    fun release() {
        engine?.unload()
        _loadedModelId.value = null
    }

    // ------------------------------------------------------------------ 能力

    /** 供 AIDL `getCapability()` 返回的 JSON。 */
    fun capabilityJson(): String {
        val installed = installedModels()
        val langs = installed.flatMap { it.langs }.distinct().sorted()
        val dialects = installed.flatMap { it.dialects }.distinct().sorted()
        val textOnly = installed.flatMap { it.textOnlyLangs }.distinct().filter { it in langs }
        val loaded = _loadedModelId.value ?: installed.firstOrNull()?.id

        val models = JSONArray()
        installed.forEach { m ->
            models.put(
                JSONObject().apply {
                    put("modelId", m.id)
                    put("displayName", m.name)
                    put("languages", JSONArray(m.langs))
                    put("dialects", JSONArray(m.dialects))
                    put("quality", m.quality)
                    put("speedClass", m.speedClass)
                    put("longText", m.longText)
                    put("maxInputChars", m.maxInputChars)
                    put("loaded", m.id == loaded)
                }
            )
        }

        return JSONObject().apply {
            put("apiVersion", 1)
            put("providerId", "hub")
            put("displayName", "离线翻译模型")
            put("modelReady", installed.isNotEmpty())
            put("directionMode", "any")
            put("languages", JSONArray(langs))
            put("dialects", JSONArray(dialects))
            put("textOnlyLangs", JSONArray(textOnly))
            put("installedModels", models)
        }.toString()
    }

    /** UI 用：语种中文名列表。 */
    fun languageLabel(code: String): String =
        LanguageCatalog.of(code)?.native ?: code

    /** 下载中的临时文件占用（用于展示，不参与计算）。 */
    fun partBytes(): Long = repo.partBytes()
}
