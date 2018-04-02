package com.devbrackets.android.exomedia.ui.widget.subtitle;

import java.io.File;

public interface DownloadCallback {
    public void onDownload(File file);
    public void onFail(Exception e);
}