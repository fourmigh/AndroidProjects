package org.caojun.ai.translate.api;

import org.caojun.ai.translate.api.ITranslateCallback;

interface ITranslateService {
    int getApiVersion();

    String[] getSupportedDirections();

    boolean isDirectionReady(String from, String to);

    void preload(String from, String to);

    void translate(String text, String from, String to, ITranslateCallback callback);
}
