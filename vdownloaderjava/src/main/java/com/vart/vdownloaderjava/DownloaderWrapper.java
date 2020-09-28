package com.vart.vdownloaderjava;

import java.util.ArrayList;
import java.util.List;

public class DownloaderWrapper {

    List<DownloaderEntity.ThreadInfo> threadInfoList = new ArrayList <>();

    long fileSize = 0;

    public DownloaderEntity.FileInfo fileInfo = null;

    String jsonProperties = "";

    public boolean isCompleted = false;
}
