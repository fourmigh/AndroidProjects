package org.caojun.ai.translate.hub

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.caojun.ai.translate.hub.core.CatalogModel
import org.caojun.ai.translate.hub.core.HubModelManager
import org.caojun.ai.translate.hub.core.InstallState
import org.caojun.ai.translate.provider.LanguageCatalog

class HubViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = HubModelManager.settings()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val catalog: List<CatalogModel> = HubModelManager.catalog()
    val states: StateFlow<Map<String, InstallState>> = HubModelManager.states
    val loadedModelId: StateFlow<String?> = HubModelManager.loadedModelId

    var installedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var installedBytes by mutableLongStateOf(0L)
        private set
    var freeBytes by mutableLongStateOf(0L)
        private set

    var defaultModelId by mutableStateOf(settings.defaultModelId)
        private set
    var allowMetered by mutableStateOf(settings.allowMetered)
        private set

    var fromLang by mutableStateOf(settings.lastFrom)
    var toLang by mutableStateOf(settings.lastTo)
    var input by mutableStateOf("Hello, world!")
    var output by mutableStateOf("")
    var busy by mutableStateOf(false)
    var status by mutableStateOf("")
    var elapsedMs by mutableLongStateOf(0L)

    init {
        // 同步初始化一次：避免进程重启后首帧把「已安装」误显示成「下载」
        installedIds = HubModelManager.installedModels().map { it.id }.toSet()
        refresh()
        scope.launch {
            HubModelManager.states.collect { refresh() }
        }
    }

    /**
     * 合并「内存状态」与「磁盘事实」：
     * - 下载中 / 校验中 / 失败 → 以内存状态为准
     * - 内存里没有 → 看磁盘是否已安装（进程重启后的情况）
     */
    fun resolveState(model: CatalogModel, states: Map<String, InstallState>): InstallState {
        val memory = states[model.id]
        if (memory != null && memory != InstallState.NotInstalled) return memory
        return if (model.id in installedIds) InstallState.Ready else InstallState.NotInstalled
    }

    val installedModels: List<CatalogModel>
        get() = catalog.filter { it.id in installedIds }

    val availableLanguages: List<String>
        get() = installedModels
            .flatMap { it.langs }
            .distinct()
            .sortedWith(
                compareByDescending<String> { it == "zh-Hans" }
                    .thenByDescending { it == "en" }
                    .thenBy { LanguageCatalog.of(it)?.native ?: it }
            )

    fun refresh() {
        scope.launch {
            val ids = withContext(Dispatchers.IO) {
                HubModelManager.installedModels().map { it.id }.toSet()
            }
            installedIds = ids
            installedBytes = withContext(Dispatchers.IO) { HubModelManager.repository().installedBytes() }
            freeBytes = withContext(Dispatchers.IO) { HubModelManager.repository().freeBytes() }
        }
    }

    fun install(model: CatalogModel) {
        HubModelManager.install(model)
    }

    fun cancelInstall(model: CatalogModel) {
        HubModelManager.cancelInstall(model)
    }

    fun delete(model: CatalogModel) {
        HubModelManager.delete(model)
        installedIds = installedIds - model.id
        refresh()
    }

    fun setDefault(modelId: String?) {
        settings.defaultModelId = modelId
        defaultModelId = modelId
    }

    fun updateAllowMetered(value: Boolean) {
        settings.allowMetered = value
        allowMetered = value
    }

    /** 交换源/目标语言；同时把译文带回源文本（单向，便于直接回译）。 */
    fun swapLanguages() {
        val from = fromLang
        val to = toLang
        val available = availableLanguages
        if (from !in available || to !in available) return

        val previousResult = output
        fromLang = to
        toLang = from
        status = ""

        if (previousResult.isNotBlank()) {
            input = previousResult
            output = ""
        }
    }

    fun preflight(model: CatalogModel): String? = HubModelManager.preflight(model)

    fun translate() {        if (busy) return
        val text = input
        if (text.isBlank()) return
        val from = fromLang
        val to = toLang
        if (from == to) {
            status = "源语言和目标语言相同"
            return
        }

        busy = true
        output = ""
        status = "翻译中…"
        elapsedMs = 0L

        scope.launch {
            val started = System.currentTimeMillis()
            runCatching {
                withContext(Dispatchers.IO) { HubModelManager.translate(text, from, to) }
            }.onSuccess {
                output = it
                status = if (it.isBlank()) "模型没有给出有效结果" else "完成"
            }.onFailure {
                status = "失败：${it.message}"
            }
            elapsedMs = System.currentTimeMillis() - started
            busy = false
            settings.lastFrom = from
            settings.lastTo = to
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
