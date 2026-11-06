package org.caojun.ai.translate.api;

oneway interface ITranslateCallback {
    void onSuccess(String translatedText);

    void onError(int code, String message);
}
