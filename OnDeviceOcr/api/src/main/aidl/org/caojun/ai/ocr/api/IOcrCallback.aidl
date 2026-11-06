package org.caojun.ai.ocr.api;

oneway interface IOcrCallback {
    void onSuccess(String resultJson);

    void onError(int code, String message);
}
