package com.vart.vdownloaderjava;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class DownloaderManager {

    private static final DownloaderManager instance = new DownloaderManager();
    private static final String PREFERENCE_FILE_KEY = "vart_downloader_manager";
    private static final String TAG = "VART_download";

    private DownloaderConfig config;

    private DownloaderManager() {
        config = new DownloaderConfig();
        config.threadNum = 5;
        config.connectTimeout = 5000;
        config.readTimeout = 5000;
        config.retryTimes = 1;
    }

    public static DownloaderManager getInstance() {
        return instance;
    }

    private ObjectMapper mapper = new ObjectMapper();

    private ConcurrentHashMap<String, DownloaderTask> tasks = new ConcurrentHashMap<>();

    public void addTask(final Context context, final DownloaderEntity.FileInfo fileInfo, final IDownloader downloadImpl, final long fileSize) {
        DownloaderTask previousTask = tasks.get(fileInfo.url);
        if (previousTask != null) {//如果task列表存在，说明正在下载中
            previousTask.downloaderImpl = downloadImpl;
            //为了解决并发问题，要到后面才能return
        }

        final DownloaderWrapper downloadWrapper = loadDownloadWrapper(context, fileInfo.url);
        if (downloadWrapper != null && downloadWrapper.isCompleted) {
            downloadImpl.onComplete(downloadWrapper);
            Log.d(TAG, "task already complete");
            return;
        }
        if (previousTask != null) {
            Log.d(TAG, "task exists");
            return;
        }

        Disposable disposable = Observable.create(new ObservableOnSubscribe<DownloaderWrapper>() {
            @Override
            public void subscribe(ObservableEmitter<DownloaderWrapper> e) throws Exception {
                if (downloadWrapper != null) {
                    e.onNext(downloadWrapper);
                } else {//既没有下载已完成，也不是正在下载中
                    DownloaderWrapper wrapper = new DownloaderWrapper();
                    if (TextUtils.isEmpty(fileInfo.fileName)) fileInfo.fileName = fileInfo.url.substring(fileInfo.url.lastIndexOf("/") + 1); //如果没有设置文件名，则自动设置一个
                    if (TextUtils.isEmpty(fileInfo.dictionary)) fileInfo.dictionary = "tmp"; //如果没有文件夹名，则自动设置一个

                    Log.d(TAG, "init wrapper ${fileInfo.fileName}");
                    wrapper.fileInfo = fileInfo;
                    if (fileSize > 0) {
                        wrapper.fileSize = fileSize;
                    } else {
                        wrapper.fileSize = getFileSize(fileInfo.url);
                        Log.d(TAG, "file size: ${wrapper?.fileSize}");
                        int threadNum = Math.max(config.threadNum, 1);
                        long block = wrapper.fileSize / threadNum;

                        for (int i = 0; i < threadNum; i++) {
                            long start = i * block;
                            long end =  (i == (threadNum - 1)) ? wrapper.fileSize - 1 : start + block - 1;
                            DownloaderEntity.ThreadInfo threadInfo = new DownloaderEntity.ThreadInfo(start, end, 0);
                            wrapper.threadInfoList.add(threadInfo);
                        }
                    }
                    if (wrapper.fileSize <= 0) {
                        //e.onError(null);
                    } else {
                        e.onNext(wrapper);
                    }
                }
            }

        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .flatMap(new Function<DownloaderWrapper, ObservableSource<DownloaderTask>>() {
            @Override
            public ObservableSource<DownloaderTask> apply(DownloaderWrapper wrapper) throws Exception {
                Log.d(TAG, "wrapper $downloadWrapper");
                DownloaderTask task = new DownloaderTask(context, wrapper, config, downloadImpl);
                tasks.put(fileInfo.url, task);
                downloadImpl.onStart(wrapper);
                return Observable.just(task);
            }
        })
        .observeOn(Schedulers.io())
        .subscribe(new Consumer<DownloaderTask>() {
            @Override
            public void accept(DownloaderTask downloaderTask) throws Exception {
                downloaderTask.start();
            }
        });
    }

    private long getFileSize(String url) {
        try {
            URL uRL = new URL(url);
            HttpURLConnection httpURLConnection = (HttpURLConnection)uRL.openConnection();
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setRequestProperty("Charset", "UTF-8");
            httpURLConnection.setConnectTimeout(config.connectTimeout);
            httpURLConnection.setReadTimeout(config.readTimeout);
            httpURLConnection.connect();
            int fileSize = httpURLConnection.getContentLength();
            int code = httpURLConnection.getResponseCode();
            if (code == 200 && fileSize > 0) {
                return fileSize;
            } else {
                return 0L;
            }
        } catch (Exception e) {return 0;}
    }

    public void saveWrapper(Context context, DownloaderWrapper downloaderWrapper) {
        SharedPreferences sp = context.getSharedPreferences(PREFERENCE_FILE_KEY, Context.MODE_PRIVATE);
        try {
            sp.edit().putString(downloaderWrapper.fileInfo.url, mapper.writeValueAsString(downloaderWrapper)).apply();
        } catch (JsonProcessingException e) { e.printStackTrace(); }
    }

//    fun removeTask(downloaderTask: DownloaderTask) {
//        tasks.remove(downloaderTask.downloaderWrapper.fileInfo?.url!!, downloaderTask)
//    }
//
    public void pause(Context context, DownloaderEntity.FileInfo fileInfo) {
        Log.d(TAG, "pause");
        DownloaderTask task = tasks.get(fileInfo.url);
        if (task != null) {
            task.stop();
            tasks.remove(fileInfo.url);
        }
    }

    public void deleteDownloaderInfoTest(Context context, DownloaderEntity.FileInfo fileInfo) {
        Log.d(TAG, "deleteDownloaderInfoTest");
        pause(context, fileInfo);
        SharedPreferences sp = context.getSharedPreferences(PREFERENCE_FILE_KEY, Context.MODE_PRIVATE);
        sp.edit().remove(fileInfo.url).commit();
        if (TextUtils.isEmpty(fileInfo.fileName)) fileInfo.fileName = fileInfo.url.substring(fileInfo.url.lastIndexOf("/") + 1) ;//如果没有设置文件名，则自动设置一个

        StorageUtils.deleteFile(context, fileInfo.dictionary, fileInfo.fileName);

    }

    public DownloaderWrapper loadDownloadWrapper(Context context, String url) {
        SharedPreferences sp = context.getSharedPreferences(PREFERENCE_FILE_KEY, Context.MODE_PRIVATE);
        String wrapper = sp.getString(url, null);
        if (wrapper != null) {
            try {
                return mapper.readValue(wrapper, DownloaderWrapper.class);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }
}
