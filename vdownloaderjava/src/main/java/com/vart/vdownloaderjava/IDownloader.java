package com.vart.vdownloaderjava;

public interface IDownloader {

    void onStart(DownloaderWrapper wrapper);

    void onComplete(DownloaderWrapper wrapper);

    void onFail(DownloaderWrapper wrapper);

    void onProgress(float progress);
}
