package com.vart.vdownloaderjava;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class DownloaderTask {

    Context context;
    DownloaderWrapper downloaderWrapper;
    DownloaderConfig downloaderConfig;
    IDownloader downloaderImpl;

    public DownloaderTask(Context context, DownloaderWrapper downloaderWrapper, DownloaderConfig downloaderConfig, IDownloader downloaderImpl) {
        this.context = context;
        this.downloaderWrapper = downloaderWrapper;
        this.downloaderConfig = downloaderConfig;
        this.downloaderImpl = downloaderImpl;
    }

    public static final String TAG = "VART_download";
    public AtomicLong atomCurrent = new AtomicLong(0L);//当前下载总量
    public int retryTimes = 0; //当前重试次数
    public boolean isStop = false; //是否已停止
    private List<DownloaderThread> threads = new ArrayList<>();
    private IProgress progressImpl = new IProgress() {
        @Override
        public void onProgressUpdate(final int plus) {
            if (isStop) {
                Log.d(TAG, "onProgressUpdate: task stop");
                return;
            }

            Disposable disposable = Observable.fromCallable(new Callable<Float>() {
                @Override
                public Float call() throws Exception {
                    long current = atomCurrent.addAndGet(plus);
                    return current * 1.0f / (downloaderWrapper.fileSize == 0 ? 1 : downloaderWrapper.fileSize);
                }})
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<Float>() {
                        @Override
                        public void accept(Float progress) throws Exception {
                            downloaderImpl.onProgress(progress);
                            Log.d(TAG, "plus: $plus current: $current total: ${downloaderWrapper.fileSize} progress $progress in thread"
                                    + Thread.currentThread().getName());
                            if (progress >= 1.0f) downloaderWrapper.isCompleted = true;
                        }
                    });
            DownloaderManager.getInstance().saveWrapper(context, downloaderWrapper);
            //这里因为没有做同步，所以不好删除掉该task
        }
    };


    interface IProgress {
        void onProgressUpdate(int plus);
    }

    public void start() {
        //todo switch to io thread
        File file = StorageUtils.createFile(context, downloaderWrapper.fileInfo.dictionary,
                downloaderWrapper.fileInfo.fileName, false);
        try {
            RandomAccessFile randomFile = new RandomAccessFile(file.getPath(), "rwd");
            randomFile.setLength(downloaderWrapper.fileSize);
            randomFile.close();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        atomCurrent.set(0L);
        CountDownLatch latch = new CountDownLatch(downloaderWrapper.threadInfoList.size());
        for (DownloaderEntity.ThreadInfo info: downloaderWrapper.threadInfoList) {
            atomCurrent.addAndGet(info.offset);
        }
//        launch(Dispatchers.Main) { todo maybe some problem
//            progressImpl.onProgressUpdate(0)
//        }

        Log.d(TAG, "task ${Thread.currentThread().name} start current: ${atomCurrent.get()} retryTimes: $retryTimes path: ${file.path} ");
        for (int i = 0; i < downloaderWrapper.threadInfoList.size(); i++) {
            DownloaderEntity.ThreadInfo it = downloaderWrapper.threadInfoList.get(i);
            DownloaderEntity downloaderEntity = new DownloaderEntity();
            downloaderEntity.threadInfo = it;
            downloaderEntity.fileInfo = (downloaderWrapper.fileInfo);
            downloaderEntity.status = (DownloaderEntity.Status.pending);

            DownloaderThread downloaderThread = new DownloaderThread();
            downloaderThread.id = i;
            downloaderThread.path = file.getPath();
            downloaderThread.isStop = isStop;
            downloaderThread.progressImpl = progressImpl;
            downloaderThread.downloaderConfig = downloaderConfig;
            downloaderThread.downloaderEntity = downloaderEntity;
            downloaderThread.latch = latch;

            Thread thread = new Thread(downloaderThread);
            threads.add(downloaderThread);
            thread.start();
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }
        if (isStop) {
            Log.d(TAG, "task stopped");
            return;
        }
        if (downloaderWrapper.isCompleted) {
            Disposable disposable = Observable.just(downloaderWrapper).observeOn(AndroidSchedulers.mainThread()).subscribe(new Consumer<DownloaderWrapper>() {
                @Override
                public void accept(DownloaderWrapper downloaderWrapper) throws Exception {
                    downloaderImpl.onComplete(downloaderWrapper);
                }
            });
            return;
        }
        if (retryTimes >= downloaderConfig.retryTimes) {
            Disposable disposable = Observable.just(downloaderWrapper).observeOn(AndroidSchedulers.mainThread()).subscribe(new Consumer<DownloaderWrapper>() {
                @Override
                public void accept(DownloaderWrapper downloaderWrapper) throws Exception {
                    Log.d(TAG, "failed: $retryTimes ${downloaderConfig.retryTimes}");
                    downloaderImpl.onFail(downloaderWrapper);
                }
            });
        } else {
            Log.d(TAG, "retry: $retryTimes ${downloaderConfig.retryTimes}");
            retryTimes++;
            start();
        }
    }

    public void stop() {
        Log.d(TAG, "task stop");
        this.isStop = true;
        for (DownloaderThread thread : threads) {
            thread.isStop = true;
        }
    }
}
