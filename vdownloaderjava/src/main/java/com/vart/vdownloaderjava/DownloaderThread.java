package com.vart.vdownloaderjava;

import android.util.Log;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

public class DownloaderThread implements Runnable {

    private static final String TAG = "VART_download";
    public int id;//用来记录当前是第几个线程
    public String path; //需要下载到哪个文件
    public boolean isStop; //暂停下载的本质是结束下载的所有线程，如果继续，会重启新的线程继续下载，这个变量控制是否暂停
    public CountDownLatch latch;
    public DownloaderTask.IProgress progressImpl; //每个线程会首先回调给创建他的task，然后由这个task统一回调给IDownloader
    public DownloaderEntity downloaderEntity; //描述当前下载状态
    public DownloaderConfig downloaderConfig;


    @Override
    public void run() {
        long rangeStart = downloaderEntity.threadInfo.start;
        long rangeOffset = downloaderEntity.threadInfo.offset;
        if (rangeStart + downloaderEntity.threadInfo.offset >= downloaderEntity.threadInfo.end) {//该线程已经下载完成了
            downloaderEntity.status = DownloaderEntity.Status.complete;
            Log.d(TAG, "thread $id has already completed");
            latch.countDown();
            return;
        }
        URL url;
        InputStream inputStream;
        RandomAccessFile file;
        try {
            url = new URL(downloaderEntity.fileInfo.url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Range", "bytes=" + (rangeStart + rangeOffset) + "-" + downloaderEntity.threadInfo.end);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Charset", "UTF-8");
            connection.setConnectTimeout(downloaderConfig.connectTimeout);
            connection.setRequestProperty("User-Agent", "joowing");
            connection.setReadTimeout(downloaderConfig.readTimeout);

            inputStream = connection.getInputStream();
            file = new RandomAccessFile(path, "rwd");
            file.seek(rangeStart + rangeOffset);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            latch.countDown();
            return;
        }

        byte[] buffer = new byte[4096];
        downloaderEntity.status = DownloaderEntity.Status.downloading;
        Log.d(TAG, "thread $id id ${Thread.currentThread().id} is running start: $rangeStart, offset: $rangeOffset, end: ${downloaderEntity.threadInfo.end}");
        int bufferedLen = 0;
        while (true) {
            if (isStop) {//如果暂停
                Log.d(TAG, "$id stopped");
                downloaderEntity.status = DownloaderEntity.Status.pending;
                break;
            }
            int len;
            try {
                len = inputStream.read(buffer);
                if (len == -1) {
                    break;
                }
                file.write(buffer, 0, len);
            } catch (Exception e) {
                Log.d(TAG, e.toString());
                break;
            }

            downloaderEntity.threadInfo.offset = downloaderEntity.threadInfo.offset + len;
            bufferedLen += len;
            if (bufferedLen >= 409600) {
                Log.d(TAG, "thread $id download ${downloaderEntity.threadInfo.offset}");
                progressImpl.onProgressUpdate(bufferedLen);
                bufferedLen = 0;
            }
        }
        if (rangeStart + downloaderEntity.threadInfo.offset >= downloaderEntity.threadInfo.end) {
            downloaderEntity.status = DownloaderEntity.Status.complete;
        }
        progressImpl.onProgressUpdate(bufferedLen);
        Log.d(TAG, "thread $id end: ${downloaderEntity.status}");
        latch.countDown();
        try {
            file.close();
            inputStream.close();
        } catch (Exception e) {}

    }
}
