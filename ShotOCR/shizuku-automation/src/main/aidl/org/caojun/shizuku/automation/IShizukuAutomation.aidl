package org.caojun.shizuku.automation;

import android.os.ParcelFileDescriptor;

interface IShizukuAutomation {
    int dumpUi(in ParcelFileDescriptor pfd);
    int screenshot(in ParcelFileDescriptor pfd);
    int tap(int x, int y);
    int swipe(int x1, int y1, int x2, int y2, int durationMs);
    int pressBack();
}
