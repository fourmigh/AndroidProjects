package org.caojun.ai.translate.provider

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 枚举本机已安装的翻译 provider。
 *
 * 通过统一的 intent action 发现，不需要知道任何具体包名；
 * Android 11+ 的包可见性由 api 模块 manifest 里的 `<queries>` 保证。
 */
object TranslateProviders {

    /** 发现所有 provider 并读取能力（会逐个 bind，约几十毫秒）。 */
    suspend fun discover(context: Context): List<DiscoveredProvider> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val services = runCatching {
            pm.queryIntentServices(Intent(TranslateProviderApi.ACTION_PROVIDER), 0)
        }.getOrDefault(emptyList())

        val packages = services.mapNotNull { it.serviceInfo?.packageName }.distinct()
        if (packages.isEmpty()) return@withContext emptyList()

        coroutineScope {
            packages
                .map { pkg ->
                    async {
                        TranslateProviderClient(context, pkg).capability()?.let {
                            DiscoveredProvider(pkg, it)
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .sortedBy { it.capability.displayName }
        }
    }

    /** 所有 provider 支持语种的并集（UI 展示用）。 */
    fun unionLanguages(providers: List<DiscoveredProvider>): List<String> =
        providers.filter { it.capability.modelReady }
            .flatMap { it.capability.languages }
            .distinct()
            .sortedWith(compareByDescending<String> { it == "zh-Hans" }
                .thenByDescending { it == "en" }
                .thenBy { it })

    fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
}
