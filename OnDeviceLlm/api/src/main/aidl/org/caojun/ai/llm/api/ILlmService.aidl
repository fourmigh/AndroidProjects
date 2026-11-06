package org.caojun.ai.llm.api;

import org.caojun.ai.llm.api.ILlmCallback;

interface ILlmService {
    int getApiVersion();

    boolean isReady();

    void preload();

    void generate(String systemInstruction, String userMessage, ILlmCallback callback);
}
