package org.caojun.screen.capture;

import android.os.ParcelFileDescriptor;

interface IShizukuScreen {
    int captureToFile(in ParcelFileDescriptor png);
}
