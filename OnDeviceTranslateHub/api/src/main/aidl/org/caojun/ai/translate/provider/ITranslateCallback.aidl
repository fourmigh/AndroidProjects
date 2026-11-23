package org.caojun.ai.translate.provider;

oneway interface ITranslateCallback {
    void onSuccess(String translatedText);
    void onError(int code, String message);
}
