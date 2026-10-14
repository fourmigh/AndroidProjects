package org.caojun.shotocr.llm

import android.content.Context
import org.caojun.shotocr.prefs.SharedPrefsStore

/** llm-parser 领域内的持久化存储入口（基于通用 :prefs）。 */
object LlmStores {

    private const val PREFS_PROMPT = "llm_prompt"
    private const val PREFS_CORRECTIONS = "llm_corrections"
    private const val PREFS_CUSTOM_PLATFORMS = "llm_custom_platforms"

    fun promptStore(context: Context): PromptStore =
        PromptStore(SharedPrefsStore(context.applicationContext, PREFS_PROMPT))

    fun correctionStore(context: Context): CorrectionStore =
        CorrectionStore(SharedPrefsStore(context.applicationContext, PREFS_CORRECTIONS))

    fun customPlatformStore(context: Context): CustomPlatformStore =
        CustomPlatformStore(SharedPrefsStore(context.applicationContext, PREFS_CUSTOM_PLATFORMS))
}
