package org.caojun.ai.translate.hub

import android.app.Application
import org.caojun.ai.translate.hub.core.HubModelManager
import org.caojun.ai.translate.hub.llama.LlamaBridge
import org.caojun.ai.translate.hub.llama.LlamaTranslateEngine

class HubApp : Application() {

    override fun onCreate() {
        super.onCreate()
        HubModelManager.init(this)
        if (LlamaBridge.isAvailable) {
            HubModelManager.engine = LlamaTranslateEngine()
        }
    }
}
