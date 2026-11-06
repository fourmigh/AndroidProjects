package org.caojun.ai.ocr.api;

import android.os.ParcelFileDescriptor;
import org.caojun.ai.ocr.api.IOcrCallback;

interface IOcrService {
    int getApiVersion();

    boolean isReady();

    void preload();

    void recognize(in ParcelFileDescriptor image, String format, IOcrCallback callback);
}
