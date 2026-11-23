package org.caojun.ai.translate.provider

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一次翻译的结果，带上实际使用的 provider / 模型，便于排查。 */
data class TranslateResult(
    val text: String,
    val packageName: String,
    val providerName: String,
    val modelId: String?,
)

/**
 * 翻译路由：在已安装 provider 中挑选能覆盖该方向的一个。
 *
 * 选择顺序：
 * 1. 显式指定的 provider（[preferPackage]）
 * 2. 策略：QUALITY 取 quality 最高；SPEED 取最快的 provider；AUTO 取 quality 最高
 * 3. 同分时优先模型已常驻（`modelReady`）且语种覆盖更窄的模型（更可能已加载）
 */
class TranslateRouter(context: Context) {

    private val appContext = context.applicationContext

    suspend fun translate(
        text: String,
        from: String,
        to: String,
        policy: TranslateProviderApi.Policy = TranslateProviderApi.Policy.AUTO,
        preferPackage: String? = null,
        timeoutMs: Long = 90_000L,
    ): TranslateResult = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            return@withContext TranslateResult("", "", "", null)
        }
        val target = LanguageCatalog.normalize(from)
            ?: throw TranslateProviderException(TranslateProviderApi.ERROR_INVALID_INPUT, "unknown source: $from")
        val dest = LanguageCatalog.normalize(to)
            ?: throw TranslateProviderException(TranslateProviderApi.ERROR_INVALID_INPUT, "unknown target: $to")
        if (target == dest) {
            return@withContext TranslateResult(text, "", "", null)
        }

        val providers = TranslateProviders.discover(appContext)
        if (providers.isEmpty()) {
            throw TranslateProviderException(
                TranslateProviderApi.ERROR_NOT_READY,
                "no translate provider installed"
            )
        }

        val candidates = providers
            .filter { it.capability.modelReady && it.capability.supports(target, dest) }
            .sortedWith(providerComparator(policy, preferPackage))

        val chosen = candidates.firstOrNull() ?: run {
            val installed = providers.filter { it.capability.modelReady }
            val hint = installed.joinToString(", ") { it.capability.displayName }
            throw TranslateProviderException(
                TranslateProviderApi.ERROR_UNSUPPORTED,
                "no installed model supports $target -> $dest (installed: ${hint.ifEmpty { "none" }})"
            )
        }

        val modelId = chosen.capability.bestModelFor(target, dest, policy)?.modelId
        val translated = TranslateProviderClient(appContext, chosen.packageName)
            .translate(text, target, dest, "", timeoutMs)

        TranslateResult(
            text = translated,
            packageName = chosen.packageName,
            providerName = chosen.capability.displayName,
            modelId = modelId,
        )
    }

    /** 纯查询：该方向是否可用（不触发翻译）。 */
    suspend fun canTranslate(from: String, to: String): Boolean = withContext(Dispatchers.IO) {
        val target = LanguageCatalog.normalize(from) ?: return@withContext false
        val dest = LanguageCatalog.normalize(to) ?: return@withContext false
        TranslateProviders.discover(appContext).any {
            it.capability.modelReady && it.capability.supports(target, dest)
        }
    }

    suspend fun supportedLanguages(): List<String> = withContext(Dispatchers.IO) {
        TranslateProviders.unionLanguages(TranslateProviders.discover(appContext))
    }

    private fun providerComparator(
        policy: TranslateProviderApi.Policy,
        preferPackage: String?,
    ): Comparator<DiscoveredProvider> = Comparator { a, b ->
        if (preferPackage != null) {
            val pa = if (a.packageName == preferPackage) 1 else 0
            val pb = if (b.packageName == preferPackage) 1 else 0
            if (pa != pb) return@Comparator pb - pa
        }
        val bestA = a.capability.installedModels.maxOfOrNull { it.quality } ?: 0
        val bestB = b.capability.installedModels.maxOfOrNull { it.quality } ?: 0
        when (policy) {
            TranslateProviderApi.Policy.SPEED -> {
                val sa = a.capability.installedModels.minOfOrNull {
                    TranslateCapability.speedRank(it.speedClass)
                } ?: 2
                val sb = b.capability.installedModels.minOfOrNull {
                    TranslateCapability.speedRank(it.speedClass)
                } ?: 2
                if (sa != sb) sa - sb else bestB - bestA
            }
            else -> if (bestA != bestB) bestB - bestA else 0
        }
    }
}
