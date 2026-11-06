package org.caojun.ai.llm.api;

oneway interface ILlmCallback {
    void onSuccess(String resultText);

    void onError(int code, String message);
}
